package com.xiaolvshu.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.PostTagMapper;
import com.xiaolvshu.mapper.TagMapper;
import com.xiaolvshu.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Elasticsearch 全文索引服务。
 *
 * <p>该服务独立管理笔记全文索引的创建、增量同步、单篇刷新、删除和检索。
 * MySQL 中的 {@code is_indexed/indexed_at} 是全文索引同步的唯一状态标记，
 * 不与 RAG 的向量化状态共用。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private final ElasticsearchClient client;
    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;

    @Value("${app.search.index-prefix:xiaolvshu}")
    private String indexPrefix;

    public String postIndex() {
        return indexPrefix + "_posts_v1";
    }

    /** 初始化全文索引；已存在时不修改 mapping。 */
    public void ensureIndex() {
        try {
            // 1）检查索引是否存在
            if (client.indices().exists(ExistsRequest.of(e -> e.index(postIndex()))).value()) {
                return;
            }
            // 2）索引不存在时创建索引，包含中文分词器和字段 mapping
            client.indices()
                .create(c -> c.index(postIndex())
                    .settings(s -> s.analysis(a -> a.analyzer("zh", an -> an.custom(ca -> ca.tokenizer("smartcn_tokenizer")))))
                    .mappings(m -> m.properties("postId", p -> p.long_(v -> v))
                        .properties("title", p -> p.text(v -> v.analyzer("zh")
                            .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("content", p -> p.text(v -> v.analyzer("zh")))
                        .properties("author", p -> p.text(v -> v.analyzer("zh")
                            .fields("keyword", f -> f.keyword(k -> k))))
                        .properties("authorUserId", p -> p.keyword(v -> v))
                        .properties("tags", p -> p.keyword(v -> v))
                        .properties("type", p -> p.integer(v -> v))
                        .properties("createdAt", p -> p.date(v -> v))));
        } catch (IOException e) {
            throw new IllegalStateException("初始化 Elasticsearch 全文索引失败", e);
        }
    }

    /**
     * 同步全部未索引或内容已更新的已发布笔记。
     * 每篇笔记只在 ES 写入成功后更新数据库状态，中途失败的笔记会留待下次重试。
     */
    public int syncPendingPosts() {
        ensureIndex();
        // 1）查询所有非草稿、内容非空、未索引或已更新(updated_at > indexed_at) 的笔记
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .isNotNull(Post::getContent)
                .and(wrapper -> wrapper
                        .eq(Post::getIsIndexed, 0)
                        .or()
                        .isNull(Post::getIndexedAt)
                        .or()
                        .apply("updated_at IS NOT NULL AND indexed_at IS NOT NULL AND updated_at > indexed_at"))
                .orderByAsc(Post::getId));
        if (posts.isEmpty()) {
            return 0;
        }

        // 2）批量查询作者和标签信息，避免 N+1 查询
        Map<Long, User> userMap = buildUserMap(posts);
        Map<Long, List<String>> tagMap = buildTagMap(posts.stream().map(Post::getId).toList());
        // 3）逐篇写入 ES 并更新数据库索引状态(不需要事务，失败的笔记留待下次重试)
        int syncedCount = 0;
        for (Post post : posts) {
            User author = userMap.get(post.getUserId());
            writePost(post, author, tagMap.getOrDefault(post.getId(), Collections.emptyList()));
            markIndexed(post);
            syncedCount++;
        }
        return syncedCount;
    }

    /** 
     * 索引单篇笔记，笔记不存在或为草稿时删除全文文档并重置索引状态。
     * @return 1=已索引，0=已删除或未索引 
     */
    public int syncPost(Long postId) {
        ensureIndex();
        Post post = postMapper.selectById(postId);
        if (post == null) {
            deletePost(postId);
            return 0;
        }
        if (Integer.valueOf(1).equals(post.getIsDraft())
                || post.getContent() == null || post.getContent().isBlank()) {
            deletePost(postId);
            resetIndexedState(postId);
            return 0;
        }

        User author = userMapper.selectById(post.getUserId());
        List<String> tags = buildTagMap(List.of(postId)).getOrDefault(postId, Collections.emptyList());
        writePost(post, author, tags);
        markIndexed(post);
        return 1;
    }

    /** 删除全文文档，文档不存在时视为成功。 */
    public void deletePost(Long postId) {
        ensureIndex();
        try {
            client.delete(d -> d.index(postIndex()).id(String.valueOf(postId)));
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;
            }
        } catch (IOException e) {
            throw new IllegalStateException("删除 Elasticsearch 全文文档失败: " + postId, e);
        }
    }

    /** 
     * 用户笔记搜索：ES 负责相关性、过滤、分页和标签聚合。 
     */
    public PostSearchResult searchPosts(String keyword, String tag, Integer type, int page, int limit) {
        ensureIndex();
        Query query = buildPostQuery(keyword, tag, type);
        try {
            SearchResponse<Map> response = client.search(s -> s.index(postIndex())
                            .from(Math.max(0, page - 1) * limit).size(limit)
                            .query(query)
                            .sort(so -> so.score(sc -> sc.order(SortOrder.Desc)))
                            .sort(so -> so.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                            .aggregations("tags", a -> a.terms(t -> t.field("tags").size(10))), Map.class);
            List<Long> ids = response.hits().hits().stream()
                    .map(Hit::source).filter(java.util.Objects::nonNull)
                    .map(source -> Long.valueOf(String.valueOf(source.get("postId")))).toList();
            Map<String, Long> tagCounts = new LinkedHashMap<>();
            var aggregate = response.aggregations().get("tags");
            if (tag != null && !tag.isBlank()) {
                SearchResponse<Map> aggregationResponse = client.search(s -> s.index(postIndex()).size(0)
                        .query(buildPostQuery(keyword, "", type))
                        .aggregations("tags", a -> a.terms(t -> t.field("tags").size(10))), Map.class);
                aggregate = aggregationResponse.aggregations().get("tags");
            }
            if (aggregate != null && aggregate.isSterms()) {
                aggregate.sterms().buckets().array()
                        .forEach(bucket -> tagCounts.put(bucket.key().stringValue(), bucket.docCount()));
            }
            long total = response.hits().total() == null ? ids.size() : response.hits().total().value();
            return new PostSearchResult(ids, total, tagCounts);
        } catch (IOException e) {
            throw new IllegalStateException("Elasticsearch 笔记搜索失败", e);
        }
    }

    // ==================== 私有工具方法 =====================

    // 索引单篇笔记到 ES
    void writePost(Post post, User author, List<String> tags) {
        Map<String, Object> source = new HashMap<>();
        source.put("postId", post.getId());
        source.put("title", safe(post.getTitle()));
        source.put("content", safe(post.getContent()));
        source.put("author", author == null ? "匿名用户" : safe(author.getNickname()));
        source.put("authorUserId", author == null ? "" : safe(author.getUserId()));
        source.put("tags", tags == null ? List.of() : tags);
        source.put("type", post.getType());
        if (post.getCreatedAt() != null) {
            source.put("createdAt", post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        try {
            client.index(i -> i.index(postIndex()).id(String.valueOf(post.getId())).document(source));
        } catch (IOException e) {
            throw new IllegalStateException("写入 Elasticsearch 全文文档失败: " + post.getId(), e);
        }
    }

    // 标记笔记为已索引，更新 indexed_at 为当前时间；若 updated_at 不为空则要求匹配，避免覆盖新修改的笔记。
    private void markIndexed(Post post) {
        LambdaUpdateWrapper<Post> update = new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, post.getId())
                .set(Post::getIsIndexed, 1)
                .set(Post::getIndexedAt, LocalDateTime.now());
        if (post.getUpdatedAt() != null) {
            update.eq(Post::getUpdatedAt, post.getUpdatedAt());
        }
        postMapper.update(null, update);
    }

    // 重置笔记索引状态为未索引，避免下次同步时被跳过
    private void resetIndexedState(Long postId) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getIsIndexed, 0)
                .set(Post::getIndexedAt, null));
    }

    // 批量查询笔记作者信息，避免 N+1 查询
    private Map<Long, User> buildUserMap(List<Post> posts) {
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    // 批量查询笔记标签，避免 N+1 查询
    private Map<Long, List<String>> buildTagMap(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PostTag> postTags = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        if (postTags.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> tagNames = tagMapper.selectBatchIds(postTags.stream().map(PostTag::getTagId).distinct().toList()).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return postTags.stream().filter(postTag -> tagNames.containsKey(postTag.getTagId()))
                .collect(Collectors.groupingBy(PostTag::getPostId, Collectors.mapping(postTag -> tagNames.get(postTag.getTagId()), Collectors.toList())));
    }

    // 构建笔记搜索查询，支持关键字、标签和类型过滤
    private Query buildPostQuery(String keyword, String tag, Integer type) {
        return Query.of(q -> q.bool(b -> {
            if (keyword != null && !keyword.isBlank()) {
                b.must(m -> m.multiMatch(mm -> mm.query(keyword)
                        .fields("title^4", "tags^3", "author^2", "authorUserId^2", "content")));
            } else {
                b.must(m -> m.matchAll(v -> v));
            }
            if (tag != null && !tag.isBlank()) {
                b.filter(f -> f.term(t -> t.field("tags").value(tag)));
            }
            if (type != null) {
                b.filter(f -> f.term(t -> t.field("type").value(type)));
            }
            return b;
        }));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record PostSearchResult(List<Long> postIds, long total, Map<String, Long> tagCounts) {
        public static PostSearchResult empty() {
            return new PostSearchResult(Collections.emptyList(), 0, Collections.emptyMap());
        }
    }
}
