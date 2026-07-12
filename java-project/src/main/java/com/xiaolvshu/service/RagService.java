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

import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagService {

    private static final int SHORT_CONTENT_LIMIT = 800;
    private static final int CHUNK_TARGET_LENGTH = 650;
    private static final int CHUNK_MAX_LENGTH = 800;
    private static final int CHUNK_OVERLAP_LENGTH = 80;

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final VectorStore ragVectorStore;
    private final JdbcTemplate ragJdbcTemplate;

    @Value("${app.rag.top-k:5}")
    private int defaultTopK;

    @Value("${app.rag.similarity-threshold:0.45}")
    private double similarityThreshold;

    @Value("${app.rag.auto-sync-on-startup:true}")
    private boolean autoSyncOnStartup;

    public RagService(
            PostMapper postMapper,
            PostTagMapper postTagMapper,
            TagMapper tagMapper,
            UserMapper userMapper,
            VectorStore ragVectorStore,
            @Qualifier("ragJdbcTemplate") JdbcTemplate ragJdbcTemplate) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.ragVectorStore = ragVectorStore;
        this.ragJdbcTemplate = ragJdbcTemplate;
    }

    /**
     * 应用启动后自动同步社区笔记到向量库。
     * 这里失败只记录日志，不阻断主应用启动；运行期仍可通过 `/ai/travel/sync` 手动补偿同步。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initRagIndexOnStartup() {
        if (!autoSyncOnStartup) {
            return;
        }
        try {
            int count = syncPostNotesToVectorStore();
            log.info("同步笔记内容到向量库完成，向量化笔记数量: {}", count);
        } catch (Exception e) {
            log.warn("同步向量库失败: {}", e.getMessage());
        }
    }

    /**
     * 检索社区笔记并转换为 Agent 工具结果。
     *
     * @param query 用户原始问题
     * @param destination 可选目的地，用于增强检索 query
     * @param interests 可选兴趣标签，用于增强检索 query
     * @param topK 召回数量，限制在 1 到 10
     * @return 包含上下文文本和引用笔记的检索结果
     */
    public CommunitySearchResult searchCommunityNotes(String query, String destination, List<String> interests, Integer topK) {
        String mergedQuery = buildSearchQuery(query, destination, interests);
        int safeTopK = topK == null ? defaultTopK : Math.max(1, Math.min(10, topK));
        SearchRequest searchRequest = SearchRequest.builder()
                .query(mergedQuery)
                .topK(safeTopK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> docs = ragVectorStore.similaritySearch(searchRequest);
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
     * 将业务库中的已发布笔记同步到 pgvector。
     * <p>
     * 只同步新增、未向量化或内容更新后的笔记；同一篇笔记同步前会先删除旧向量，避免重复召回。
     *
     * @return 本次写入向量库的文档 chunk 数
     */
    public int syncPostNotesToVectorStore() {
        // 仅索引正式发布且有正文的笔记，草稿不进入旅行助手知识库。
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getIsDraft, 0)
                .isNotNull(Post::getContent)
                .and(wrapper -> wrapper
                        .eq(Post::getIsVectorized, 0)
                        .or()
                        .isNull(Post::getVectorizedAt)
                        .or()
                        .apply("updated_at IS NOT NULL AND vectorized_at IS NOT NULL AND updated_at > vectorized_at"))
                .orderByDesc(Post::getCreatedAt));

        if (posts.isEmpty()) {
            return 0;
        }

        Map<Long, User> userMap = buildUserMap(posts);
        Map<Long, List<String>> tagMap = buildTagMap(posts.stream().map(Post::getId).toList());

        List<Document> documents = new ArrayList<>();
        for (Post post : posts) {
            User author = userMap.get(post.getUserId());
            List<String> tags = tagMap.getOrDefault(post.getId(), Collections.emptyList());
            documents.addAll(buildPostDocuments(post, author, tags));
            // 同一 postId 先删旧向量再写新向量，保证检索结果不会包含历史版本。
            ragJdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'source' = 'post-note' AND metadata->>'postId' = ?",
                    String.valueOf(post.getId()));
        }

        final int embeddingBatchSize = 10;
        for (int i = 0; i < documents.size(); i += embeddingBatchSize) {
            // 兼容部分 OpenAI 兼容网关的 embeddings 批量限制。
            int end = Math.min(i + embeddingBatchSize, documents.size());
            ragVectorStore.add(documents.subList(i, end));
        }

        postMapper.update(
                null,
                new LambdaUpdateWrapper<Post>()
                        .in(Post::getId, posts.stream().map(Post::getId).toList())
                        .set(Post::getIsVectorized, 1)
                        .set(Post::getVectorizedAt, LocalDateTime.now()));

        return documents.size();
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
            return "未检索到相关笔记";
        }

        StringBuilder builder = new StringBuilder();
        for (Document doc : docs) {
            if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                continue;
            }
            builder.append(doc.getText().trim()).append("\n\n");
        }
        return builder.isEmpty() ? "未检索到相关笔记" : builder.toString();
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
        String tagText = tags == null || tags.isEmpty() ? "无" : String.join("、", tags);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "post-note");
            metadata.put("postId", String.valueOf(post.getId()));
            metadata.put("title", post.getTitle());
            metadata.put("author", authorName);
            metadata.put("summary", truncate(chunk, 180));
            metadata.put("link", "/post?id=" + post.getId());
            metadata.put("tags", tags == null ? "" : String.join(",", tags));
            metadata.put("chunkIndex", i);
            metadata.put("chunkCount", chunks.size());
            metadata.put("chunkType", "content");

            String content = "标题: " + safe(post.getTitle()) + "\n"
                    + "作者: " + authorName + "\n"
                    + "标签: " + tagText + "\n"
                    + "片段: " + safe(chunk);

            documents.add(new Document(content, metadata));
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
        if (value == null || value.toString().isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = value.toString().split(",");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tags.add(part.trim());
            }
        }
        return tags;
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
