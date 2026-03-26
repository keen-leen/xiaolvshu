package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
@Slf4j
public class TravelAiService {

    private static final int MAX_HISTORY = 8;

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final VectorStore ragVectorStore;
    private final JdbcTemplate ragJdbcTemplate;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    // 流式处理执行器，核心线程数4，最大线程数8，空闲线程60秒后回收，队列长度20，满载时由调用线程执行，适合长任务。
    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            4, // 线上两核CPU，核心线程数设置为4，足够处理并发请求且不至于过度占用资源。
            8, // 最大线程数设置为8，允许在高峰期临时增加线程处理更多流式请求，但仍有上限防止资源耗尽。
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(20), // 队列长度设置为20，允许一定数量的请求排队等待处理，超过时由调用线程执行，避免请求丢失。
            r -> {
                Thread t = new Thread(r);
                t.setName("travel-ai-stream-" + t.getId());
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy() // 满载时抛出异常，调用线程可以捕获并返回错误响应，提示用户稍后重试。
    );

    @Value("${app.rag.top-k:5}")
    private int defaultTopK;

    @Value("${app.rag.similarity-threshold:0.45}")
    private double similarityThreshold;

    @Value("${app.rag.auto-sync-on-startup:true}")
    private boolean autoSyncOnStartup;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String model;

    public TravelAiService(
            PostMapper postMapper,
            PostTagMapper postTagMapper,
            TagMapper tagMapper,
            UserMapper userMapper,
            VectorStore ragVectorStore,
            @Qualifier("ragJdbcTemplate") JdbcTemplate ragJdbcTemplate,
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.ragVectorStore = ragVectorStore;
        this.ragJdbcTemplate = ragJdbcTemplate;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initRagIndexOnStartup() {
        // 在应用完全启动后再执行首轮索引，避免数据源或向量库尚未就绪。
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
     * 查询业务库中的笔记数据，构建向量化文档，并同步到向量库中。返回同步的文档数量供日志记录。
     */
    public int syncPostNotesToVectorStore() {
        // 仅索引已发布且有正文、且尚未向量化或已被更新的笔记。
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
            documents.add(buildPostDocument(post, author, tags));

            // 对同一 postId 先删旧向量，再写新向量，避免重复索引。
            ragJdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'source' = 'post-note' AND metadata->>'postId' = ?",
                    String.valueOf(post.getId()));
        }

        // 兼容部分 OpenAI 兼容网关（如 DashScope）单次 embeddings 批量上限 10。
        final int embeddingBatchSize = 10;
        for (int i = 0; i < documents.size(); i += embeddingBatchSize) {
            int end = Math.min(i + embeddingBatchSize, documents.size());
            ragVectorStore.add(documents.subList(i, end));
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        postMapper.update(
                null,
                new LambdaUpdateWrapper<Post>()
                        .in(Post::getId, postIds)
                        .set(Post::getIsVectorized, 1)
                        .set(Post::getVectorizedAt, LocalDateTime.now()));

        return documents.size();
    }

    /**
     * 同步式对话入口：先做 RAG 检索，再调用模型生成完整答案。
     *
     * @param request 用户输入与可选历史对话
     * @return 包含答案、模型名和引用笔记的响应
     */
    public TravelChatResponse chat(TravelChatRequest request) {
        RagContext ragContext = buildRagContext(request);

        String answer = chatClient.prompt()
                .system(Objects.requireNonNull(systemPrompt()))
                .user(Objects.requireNonNull(composeUserPrompt(request, ragContext.contextText())))
                .call()
                .content();

        TravelChatResponse response = new TravelChatResponse();
        response.setAnswer(answer == null || answer.isBlank() ? fallbackAnswer(request.getMessage()) : answer);
        response.setRagEnabled(true);
        response.setModel(model);
        response.setReferences(ragContext.references());
        return response;
    }

    /**
     * 流式对话入口：通过 SSE 按 token 推送答案，并在结束时附带引用信息。
     *
     * @param request 用户输入与可选历史对话
     * @return SSE 发射器
     */
    public SseEmitter chatStream(TravelChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        // 流式输出放在独立线程执行，避免阻塞请求线程。
        try {
            streamExecutor.execute(() -> {
                try {
                    RagContext ragContext = buildRagContext(request);
                    String mergedPrompt = composeUserPrompt(request, ragContext.contextText());

                    Flux<String> tokenFlux = chatClient.prompt()
                            .system(Objects.requireNonNull(systemPrompt()))
                            .user(Objects.requireNonNull(mergedPrompt))
                            .stream()
                            .content();

                    tokenFlux
                            .doOnNext(token -> sendSseEvent(emitter, "chunk", token))
                            .doOnError(err -> {
                                log.warn("RAG流式输出失败: {}", err.getMessage());
                                sendSseEvent(emitter, "error", "流式生成失败，请稍后重试。");
                                emitter.complete();
                            })
                            .doOnComplete(() -> {
                                // 结束前统一返回引用，便于前端展示“答案依据”。
                                sendSseEvent(emitter, "refs", toJsonSafe(ragContext.references()));
                                sendSseEvent(emitter, "done", "[DONE]");
                                emitter.complete();
                            })
                            .blockLast();
                } catch (Exception e) {
                    log.warn("RAG流式对话异常: {}", e.getMessage());
                    sendSseEvent(emitter, "error", fallbackAnswer(request.getMessage()));
                    emitter.complete();
                }
            });
        } catch (Exception e) {
            log.warn("提交RAG流式任务失败: {}", e.getMessage());
            sendSseEvent(emitter, "error", "当前请求过多，请稍后重试。");
            emitter.complete();
        }
        

        return emitter;
    }

    /**
     * 构建 RAG 上下文：向量检索相关文档，并转换为“引用列表 + 可拼接文本”。
     *
     * @param request 用户请求
     * @return RAG 上下文对象
     */
    private RagContext buildRagContext(TravelChatRequest request) {
        String userPrompt = request.getMessage() == null ? "" : request.getMessage().trim();
        int topK = request.getTopK() == null ? defaultTopK : Math.max(1, Math.min(10, request.getTopK()));
        String safePrompt = Objects.requireNonNull(userPrompt);

        // 召回数量限制在 1~10，防止上下文过短或过长影响回答质量与延迟。
        SearchRequest searchRequest = SearchRequest.builder()
                .query(safePrompt)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> docs = ragVectorStore.similaritySearch(searchRequest);
        List<TravelChatResponse.TravelNoteReference> references = mapReferences(docs);
        String contextText = renderContextText(references);
        return new RagContext(references, contextText);
    }

    /**
     * 组装最终用户提示词，包含问题、检索上下文和最近历史对话。
     *
     * @param request     用户请求
     * @param contextText RAG 检索上下文文本
     * @return 可直接发送给大模型的提示词
     */
    private String composeUserPrompt(TravelChatRequest request, String contextText) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 用户问题\n")
                .append(request.getMessage())
                .append("\n\n")
                .append("# 社区相关内容\n")
                .append(contextText)
                .append("\n\n");

        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            builder.append("# 历史对话\n");
            // 仅保留最近 N 条历史，控制 prompt 长度并降低无关上下文干扰。
            int start = Math.max(0, request.getHistory().size() - MAX_HISTORY);
            for (int i = start; i < request.getHistory().size(); i++) {
                TravelChatRequest.ChatMessage msg = request.getHistory().get(i);
                if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                    continue;
                }
                String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "助手" : "用户";
                builder.append(role).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * 返回系统提示词，约束模型输出结构与引用规则。
     */
    private String systemPrompt() {
        return "你是一个专业的旅行攻略生成助手，为小旅书旅行图文交流社区的用户提供个性化的旅行建议和规划。" +
                "基于用户输入的问题和社区检索到的相关笔记，生成实用的旅行建议，但要注意长度适中。" +
                "输出结构必须为：1) 行程规划 2) 预算建议 3) 避坑提醒 4) 可选替代方案。" +
                "当提供的相关笔记不足以支持生成完整建议时，你可以基于自己的知识和经验进行补充，但必须明确指出哪些内容是基于检索到的笔记，哪些是你自己生成的。" +
                "如果检索到的笔记与用户问题相关但信息不足，你可以提示用户补充更多信息（如目的地、旅行天数、预算等），以便生成更精准的攻略。";
    }

    /**
     * 将引用对象列表渲染为文本块，供模型读取。
     *
     * @param refs 引用列表
     * @return 上下文文本；为空时返回引导补充信息
     */
    private String renderContextText(List<TravelChatResponse.TravelNoteReference> refs) {
        if (refs == null || refs.isEmpty()) {
            return "未检索到相关笔记";
        }

        StringBuilder builder = new StringBuilder();
        for (TravelChatResponse.TravelNoteReference ref : refs) {
            builder.append("标题: ")
                    .append(Objects.toString(ref.getTitle(), ""))
                    .append("；作者: ")
                    .append(Objects.toString(ref.getAuthor(), ""))
                    .append("；标签: ")
                    .append(ref.getTags() == null || ref.getTags().isEmpty() ? "无" : String.join("、", ref.getTags()))
                    .append("；摘要: ")
                    .append(Objects.toString(ref.getSummary(), ""))
                    .append("\n");
        }
        return builder.toString();
    }

    /**
     * 将向量检索返回的 Document 映射为前端可展示的引用结构。
     *
     * @param docs 检索返回文档
     * @return 引用列表
     */
    private List<TravelChatResponse.TravelNoteReference> mapReferences(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        List<TravelChatResponse.TravelNoteReference> refs = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();
            TravelChatResponse.TravelNoteReference ref = new TravelChatResponse.TravelNoteReference();
            ref.setPostId(parseLong(metadata.get("postId")));
            ref.setTitle(asString(metadata.get("title")));
            ref.setAuthor(asString(metadata.get("author")));
            ref.setSummary(asString(metadata.get("summary")));
            ref.setLink(asString(metadata.get("link")));
            ref.setTags(parseTags(metadata.get("tags")));
            refs.add(ref);
        }
        return refs;
    }

    /**
     * 将业务笔记实体转换为向量库文档（正文 + 元数据）。
     */
    private Document buildPostDocument(Post post, User author, List<String> tags) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "post-note");
        metadata.put("postId", String.valueOf(post.getId()));
        metadata.put("title", post.getTitle());
        metadata.put("author", author == null ? "匿名用户" : author.getNickname());
        metadata.put("summary", truncate(post.getContent(), 180));
        metadata.put("link", "/post?id=" + post.getId());
        metadata.put("tags", tags == null ? "" : String.join(",", tags));

        String content = "标题: " + safe(post.getTitle()) + "\n"
                + "作者: " + (author == null ? "匿名用户" : safe(author.getNickname())) + "\n"
                + "标签: " + (tags == null || tags.isEmpty() ? "无" : String.join("、", tags)) + "\n"
                + "正文: " + safe(post.getContent());

        return new Document(content, metadata);
    }

    /**
     * 批量加载并构建“用户ID -> 用户实体”映射，用于补齐作者信息。
     *
     * @param posts 笔记列表
     * @return 用户映射表
     */
    private Map<Long, User> buildUserMap(List<Post> posts) {
        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, x -> x));
    }

    /**
     * 批量加载并构建“笔记ID -> 标签名列表”映射。
     *
     * @param postIds 笔记ID列表
     * @return 标签映射表
     */
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

    /**
     * 发送单条 SSE 事件。
     *
     * @param emitter   SSE 发射器
     * @param eventName 事件名
     * @param data      事件数据
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, String data) {
        try {
            String safeEventName = Objects.requireNonNull(eventName == null ? "message" : eventName);
            Object safeData = Objects.requireNonNull(data == null ? "" : data);
            emitter.send(SseEmitter.event().name(safeEventName).data(safeData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 安全序列化对象为 JSON，失败时返回空数组文本。
     *
     * @param obj 任意对象
     * @return JSON 字符串
     */
    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 模型调用失败时的兜底回复文本。
     *
     * @param userPrompt 用户原始问题
     * @return 兜底答复
     */
    private String fallbackAnswer(String userPrompt) {
        return "暂时无法使用大模型生成，已回退到基础建议。你可以补充目的地、天数、预算，我会继续生成更精准攻略。\n需求: " + userPrompt;
    }

    /**
     * 将对象安全转换为 Long；无法解析时返回 null。
     */
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

    /**
     * 将对象安全转换为字符串；null 时返回空串。
     */
    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 将逗号分隔标签文本解析为标签列表。
     */
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

    /**
     * 空值安全处理：null 转为空字符串。
     */
    private String safe(String text) {
        return text == null ? "" : text;
    }

    /**
     * 规范化文本并按最大长度截断，超长时追加省略号。
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }

    private record RagContext(List<TravelChatResponse.TravelNoteReference> references, String contextText) {
    }
}
