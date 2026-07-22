package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xiaolvshu.dto.CommunitySearchResult;
import com.xiaolvshu.dto.TravelChatRequest;
import com.xiaolvshu.dto.TravelChatResponse;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.entity.PostTag;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.entity.User;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.PostTagMapper;
import com.xiaolvshu.mapper.TagMapper;
import com.xiaolvshu.mapper.UserMapper;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final int SHORT_CONTENT_LIMIT = 800;
    private static final int CHUNK_TARGET_LENGTH = 650;
    private static final int CHUNK_MAX_LENGTH = 800;
    private static final int CHUNK_OVERLAP_LENGTH = 80;

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final RagIndexService ragIndex;

    @Value("${app.rag.top-k:5}")
    private int defaultTopK;

    public RagService(
            PostMapper postMapper,
            PostTagMapper postTagMapper,
            TagMapper tagMapper,
            UserMapper userMapper,
            RagIndexService ragIndex) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.ragIndex = ragIndex;
    }

    /**
     * 检索社区笔记并转换为 Agent 工具结果。
     *
     * @param query       用户原始问题
     * @param destination 可选目的地，用于增强检索 query
     * @param interests   可选兴趣标签，用于增强检索 query
     * @param topK        召回数量，限制在 1 到 10
     * @return 包含上下文文本和引用笔记的检索结果
     */
    public CommunitySearchResult searchCommunityNotes(String query, String destination, List<String> interests,
            Integer topK) {
        String mergedQuery = buildSearchQuery(query, destination, interests);
        int safeTopK = topK == null ? defaultTopK : Math.max(1, Math.min(10, topK));
        List<Document> docs = ragIndex.hybridSearch(mergedQuery, safeTopK);
        CommunitySearchResult result = new CommunitySearchResult();
        result.setQuery(mergedQuery);
        result.setReferences(mapReferences(docs));
        result.setContextText(renderContextText(docs));
        return result;
    }

    /**
     * 兼容旧代码的 RAG 上下文构建入口。
     */
    public CommunitySearchResult buildRagContext(TravelChatRequest request) {
        String userPrompt = request.getMessage() == null ? "" : request.getMessage().trim();
        return searchCommunityNotes(userPrompt, null, Collections.emptyList(), request.getTopK());
    }

    /**
     * 将业务库中的已发布笔记同步到 Elasticsearch RAG chunk 索引。
     * <p>
     * 只同步新增、未向量化或内容更新后的笔记；同一篇笔记同步前会先删除旧向量，避免重复召回。
     *
     * @return 本次写入向量库的文档 chunk 数
     */
    public int syncPostNotesToVectorStore() {
        return syncPostChunksToElasticsearch(false);
    }

    /**
     * 同步笔记搜索chunk向量化索引，支持全量重建。
     *
     * @param full true 时忽略历史向量化标记，从 MySQL 全量重建当前已发布笔记
     */
    public int syncPostChunksToElasticsearch(boolean full) {
        ragIndex.ensureIndex();
        if (full) {
            // 先将已发布笔记置为待向量化；若全量重建中途失败，下次增量同步仍能继续补偿。
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getIsDraft, 0)
                    .set(Post::getIsVectorized, 0)
                    .set(Post::getVectorizedAt, null));
            ragIndex.clearIndex();
        }
        // 仅索引正式发布且有正文的笔记，草稿不进入旅行助手知识库。
        LambdaQueryWrapper<Post> query = new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .isNotNull(Post::getContent)
                .orderByDesc(Post::getCreatedAt);
        if (!full) {
            query.and(wrapper -> wrapper
                    .eq(Post::getIsVectorized, 0)
                    .or()
                    .isNull(Post::getVectorizedAt)
                    .or()
                    .apply("updated_at IS NOT NULL AND vectorized_at IS NOT NULL AND updated_at > vectorized_at"));
        }
        List<Post> posts = postMapper.selectList(query);

        if (posts.isEmpty()) {
            return 0;
        }

        Map<Long, User> userMap = buildUserMap(posts);
        Map<Long, List<String>> tagMap = buildTagMap(posts.stream().map(Post::getId).toList());

        int indexedChunks = 0;
        int embeddingBatchSize = ragIndex.embeddingBatchSize();
        Map<Long, List<Document>> pendingDocuments = new LinkedHashMap<>();
        int pendingChunkCount = 0;
        for (Post post : posts) {
            User author = userMap.get(post.getUserId());
            List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
            List<Document> documents = buildPostDocuments(post, author, tags);

            /*
             * 批次上限按 chunk 数而不是帖子数计算，因为一次 Embedding 请求的实际输入单位是 chunk。
             * 当加入当前帖子会超过上限时，先提交已经积累的完整帖子；不拆开 pendingDocuments 中的帖子，
             * 这样只有整篇帖子的所有新向量均生成并写入成功后，才会更新该帖子的 MySQL 向量化标记。
             */
            if (!pendingDocuments.isEmpty() && pendingChunkCount + documents.size() > embeddingBatchSize) {
                indexedChunks += flushProjectionBatch(pendingDocuments);
                pendingChunkCount = 0;
            }
            pendingDocuments.put(post.getId(), documents);
            pendingChunkCount += documents.size();

            // 单篇长笔记可能自身就超过批次上限，立即提交；底层仍会按上限拆分远程 Embedding 请求。
            if (pendingChunkCount >= embeddingBatchSize) {
                indexedChunks += flushProjectionBatch(pendingDocuments);
                pendingChunkCount = 0;
            }
        }

        // 提交不足一个完整批次的尾部帖子，避免最后几篇笔记一直停留在待向量化状态。
        indexedChunks += flushProjectionBatch(pendingDocuments);

        return indexedChunks;
    }

    /**
     * 提交一批完整帖子，并在 Elasticsearch 写入全部成功后更新 MySQL 状态。
     * <p>
     * 若 Embedding 或 Elasticsearch 任一阶段抛出异常，本方法不会为该批次中的任何帖子写入
     * {@code is_vectorized=1}。下次增量同步仍会重新选择这些帖子，从而保留可重试性。
     *
     * @param pendingDocuments 按同步顺序保存的帖子及其 chunk；调用成功或失败后均由调用方决定是否重试
     * @return 本批次成功写入的 chunk 数
     */
    private int flushProjectionBatch(Map<Long, List<Document>> pendingDocuments) {
        if (pendingDocuments.isEmpty()) {
            return 0;
        }

        /*
         * 创建批次快照后再交给索引层，避免清空累积容器时同时修改被调用方持有的 Map 引用。
         * 这也让日志、监控或后续异步实现看到的批次内容始终稳定。
         */
        Map<Long, List<Document>> submittedDocuments = new LinkedHashMap<>(pendingDocuments);
        int indexedChunks = ragIndex.replaceChunksBatch(submittedDocuments);
        // replaceChunksBatch 返回前已完成整批写入，因此此处可以安全地逐篇更新业务库状态。
        submittedDocuments.keySet().forEach(this::markVectorized);
        pendingDocuments.clear();
        return indexedChunks;
    }

    /** 同步单篇笔记的 RAG chunks；草稿或不存在的笔记会删除旧 chunks。 */
    public int syncPostChunks(Long postId) {
        ragIndex.ensureIndex();
        Post post = postMapper.selectById(postId);
        if (post == null || Integer.valueOf(1).equals(post.getIsDraft())
                || post.getContent() == null || post.getContent().isBlank()) {
            ragIndex.deleteChunks(postId);
            if (post != null) {
                resetVectorizedState(postId);
            }
            return 0;
        }
        User author = userMapper.selectById(post.getUserId());
        List<String> tags = buildTagMap(List.of(postId)).getOrDefault(postId, Collections.emptyList());
        List<Document> documents = buildPostDocuments(post, author, tags);
        ragIndex.replaceChunks(postId, documents);
        markVectorized(postId);
        return documents.size();
    }

    public void deletePostChunks(Long postId) {
        ragIndex.deleteChunks(postId);
    }

    private void markVectorized(Long postId) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getIsVectorized, 1)
                .set(Post::getVectorizedAt, LocalDateTime.now()));
    }

    private void resetVectorizedState(Long postId) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .set(Post::getIsVectorized, 0)
                .set(Post::getVectorizedAt, null));
    }

    /**
     * 合并用户问题、目的地和兴趣偏好，生成更稳定的向量检索 query。
     */
    private String buildSearchQuery(String query, String destination, List<String> interests) {
        List<String> parts = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            parts.add(query.trim());
        }
        if (destination != null && !destination.isBlank()) {
            parts.add("目的地: " + destination.trim());
        }
        if (interests != null && !interests.isEmpty()) {
            parts.add("兴趣: " + String.join("、", interests));
        }
        return parts.isEmpty() ? "旅行攻略 景点 美食 路线 避坑" : String.join("；", parts);
    }

    /**
     * 将向量检索文档渲染成模型可读取的上下文文本。
     */
    private String renderContextText(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "未检索到可靠社区笔记";
        }

        StringBuilder builder = new StringBuilder();
        /*
         * 同一帖子可能召回多个 chunk，它们必须共享一个来源编号；编号按首次出现顺序分配，
         * 这样最终 refs 按 postId 去重后仍能与模型回答中的 [S1]、[S2] 稳定对应。
         */
        Map<Long, String> sourceIds = new LinkedHashMap<>();
        int nextSourceNumber = 1;
        for (Document doc : docs) {
            if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                continue;
            }
            String title = asString(doc.getMetadata().get("title")).trim();
            List<String> tags = parseTags(doc.getMetadata().get("tags"));
            Long postId = parseLong(doc.getMetadata().get("postId"));
            String sourceId = postId == null ? null : sourceIds.get(postId);
            if (sourceId == null) {
                // 缺失 postId 的异常文档仍分配独立编号，但不会写入映射，避免多个未知来源被错误合并。
                sourceId = "S" + nextSourceNumber++;
                if (postId != null) {
                    sourceIds.put(postId, sourceId);
                }
            }
            builder.append('[').append(sourceId).append("]\n");
            if (!title.isBlank()) {
                builder.append("标题: ").append(title).append('\n');
            }
            if (!tags.isEmpty()) {
                builder.append("标签: ").append(String.join("、", tags)).append('\n');
            }
            builder.append("片段: ").append(doc.getText().trim()).append("\n\n");
        }
        return builder.isEmpty() ? "未检索到可靠社区笔记" : builder.toString();
    }

    /**
     * 将向量库 metadata 映射为前端可展示的引用笔记，按 postId 去重。
     */
    private List<TravelChatResponse.TravelNoteReference> mapReferences(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, TravelChatResponse.TravelNoteReference> refsByPostId = new LinkedHashMap<>();
        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();
            Long postId = parseLong(metadata.get("postId"));
            if (postId != null && refsByPostId.containsKey(postId)) {
                continue;
            }

            TravelChatResponse.TravelNoteReference ref = new TravelChatResponse.TravelNoteReference();
            ref.setPostId(postId);
            ref.setTitle(asString(metadata.get("title")));
            ref.setAuthor(asString(metadata.get("author")));
            ref.setSummary(asString(metadata.get("summary")));
            ref.setLink(asString(metadata.get("link")));
            ref.setTags(parseTags(metadata.get("tags")));
            refsByPostId.put(postId, ref);
        }
        return new ArrayList<>(refsByPostId.values());
    }

    /**
     * 将一篇笔记切分为可向量化的 Document 列表，并保留标题、作者、标签等引用 metadata。
     */
    private List<Document> buildPostDocuments(Post post, User author, List<String> tags) {
        List<String> chunks = splitPostContent(post.getContent());
        List<Document> documents = new ArrayList<>();
        String authorName = author == null ? "匿名用户" : safe(author.getNickname());

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "post-note");
            metadata.put("postId", String.valueOf(post.getId()));
            metadata.put("title", post.getTitle());
            metadata.put("author", authorName);
            metadata.put("summary", truncate(chunk, 180));
            metadata.put("link", "/post?id=" + post.getId());
            // Elasticsearch keyword 字段原生支持数组，保留标签边界才能正确执行精确匹配。
            metadata.put("tags", tags == null ? List.of() : List.copyOf(tags));
            metadata.put("chunkIndex", i);
            metadata.put("chunkCount", chunks.size());
            metadata.put("chunkType", "content");

            // Document 只保存原始正文 chunk；标题和标签仅在生成 embedding 时临时组合。
            documents.add(new Document(safe(chunk), metadata));
        }
        return documents;
    }

    /**
     * 按长度、自然段和句末标点拆分笔记正文，控制单个向量 chunk 的上下文大小。
     */
    private List<String> splitPostContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of("");
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() <= SHORT_CONTENT_LIMIT) {
            // 短笔记保持整体语义，不做过度切分。
            return List.of(normalized);
        }

        List<String> parts = new ArrayList<>();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > CHUNK_MAX_LENGTH) {
                parts.addAll(splitLongParagraph(trimmed));
            } else {
                parts.add(trimmed);
            }
        }

        if (parts.isEmpty()) {
            return List.of(normalized);
        }
        return buildChunksWithOverlap(parts);
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> parts = new ArrayList<>();
        paragraph = paragraph.replaceAll("\\s+", " ").trim();
        String[] splitParts = paragraph.split("(?<=[。！？；!?;])\\s*");
        for (String part : splitParts) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private List<String> buildChunksWithOverlap(List<String> parts) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!appendWithLimit(current, part.trim(), CHUNK_MAX_LENGTH)) {
                chunks.add(current.toString().trim());
                String overlap = tailText(current.toString(), CHUNK_OVERLAP_LENGTH);
                current.setLength(0);
                if (!overlap.isBlank()) {
                    current.append(overlap).append("\n");
                }
                if (!appendWithLimit(current, part.trim(), CHUNK_MAX_LENGTH)) {
                    current.setLength(0);
                    current.append(part.trim());
                }
            }

            if (current.length() >= CHUNK_TARGET_LENGTH && hasRemainingText(parts, i + 1)) {
                chunks.add(current.toString().trim());
                String overlap = tailText(current.toString(), CHUNK_OVERLAP_LENGTH);
                current.setLength(0);
                if (!overlap.isBlank()) {
                    current.append(overlap).append("\n");
                }
            }
        }

        if (!current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private boolean appendWithLimit(StringBuilder current, String part, int limit) {
        if (current.isEmpty()) {
            current.append(part);
            return true;
        }

        int nextLength = current.length() + 1 + part.length();
        if (nextLength > limit) {
            return false;
        }
        current.append("\n").append(part);
        return true;
    }

    private boolean hasRemainingText(List<String> parts, int start) {
        for (int i = start; i < parts.size(); i++) {
            if (parts.get(i) != null && !parts.get(i).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, User> buildUserMap(List<Post> posts) {
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, x -> x));
    }

    private Map<Long, List<String>> buildTagMap(List<Long> postIds) {
        List<PostTag> postTags = postTagMapper
                .selectList(new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        if (postTags.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        Map<Integer, Tag> tagEntityMap = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, x -> x));

        Map<Long, List<String>> map = new HashMap<>();
        for (PostTag postTag : postTags) {
            Tag tag = tagEntityMap.get(postTag.getTagId());
            if (tag == null) {
                continue;
            }
            map.computeIfAbsent(postTag.getPostId(), k -> new ArrayList<>()).add(tag.getName());
        }
        return map;
    }

    private String tailText(String text, int limit) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(normalized.length() - limit);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private List<String> parseTags(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }

        // tags 使用 keyword 数组，ES 客户端会将其反序列化为 Collection。
        if (value instanceof Collection<?> values) {
            List<String> tags = new ArrayList<>();
            for (Object item : values) {
                if (item != null && !item.toString().isBlank()) {
                    tags.add(item.toString().trim());
                }
            }
            return tags;
        }
        return Collections.emptyList();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
