package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.mapper.PostMapper;
import com.xiaolvshu.mapper.PostTagMapper;
import com.xiaolvshu.mapper.TagMapper;
import com.xiaolvshu.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final RagService ragService = new RagService(null, null, null, null, null);

    @BeforeAll
    static void initializePostLambdaMetadata() {
        /*
         * 批量同步测试会执行 MyBatis-Plus 的 LambdaQueryWrapper/LambdaUpdateWrapper。
         * 纯单元测试没有启动 Spring/MyBatis 容器，因此显式注册 Post 表信息，避免包装器在解析字段引用时失败。
         */
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.xiaolvshu.mapper.PostMapper");
        TableInfoHelper.initTableInfo(assistant, Post.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreTagsAsKeywordArrayInChunkMetadata() {
        Post post = new Post();
        post.setId(1L);
        post.setTitle("成都美食攻略");
        post.setContent("火锅和川菜推荐。");

        List<Document> documents = (List<Document>) ReflectionTestUtils.invokeMethod(
                ragService, "buildPostDocuments", post, null, List.of("成都", "美食"));

        assertEquals("火锅和川菜推荐。", documents.getFirst().getText());
        assertFalse(documents.getFirst().getText().contains("成都美食攻略"));
        assertEquals(List.of("成都", "美食"), documents.getFirst().getMetadata().get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreEmptyTagArrayWhenPostHasNoTags() {
        Post post = new Post();
        post.setId(2L);
        post.setTitle("无标签笔记");
        post.setContent("正文。");

        List<Document> documents = (List<Document>) ReflectionTestUtils.invokeMethod(
                ragService, "buildPostDocuments", post, null, null);

        assertEquals(List.of(), documents.getFirst().getMetadata().get("tags"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldParseTagArrayReturnedByElasticsearch() {
        List<String> tags = (List<String>) ReflectionTestUtils.invokeMethod(
                ragService, "parseTags", List.of("成都", " ", "美食"));

        assertEquals(List.of("成都", "美食"), tags);
    }

    @Test
    void shouldRenderTitleTagsAndRawChunkForAgentContext() {
        Document document = new Document("火锅和川菜推荐。", Map.of(
                "title", "成都美食攻略",
                "author", "测试作者",
                "tags", List.of("成都", "美食")));

        String context = ReflectionTestUtils.invokeMethod(ragService, "renderContextText", List.of(document));

        assertEquals("标题: 成都美食攻略\n标签: 成都、美食\n片段: 火锅和川菜推荐。\n\n", context);
        assertFalse(context.contains("测试作者"));
    }

    @Test
    void shouldExplainThatNoReliableCommunityNoteWasFoundAfterRejection() {
        // 当可靠性策略过滤掉全部候选时，上下文必须明确表示“可靠结果不足”。
        String context = ReflectionTestUtils.invokeMethod(ragService, "renderContextText", List.of());

        assertEquals("未检索到可靠社区笔记", context);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBatchTwelveSingleChunkPostsAsTenAndTwoThenMarkAllPosts() {
        PostMapper postMapper = mock(PostMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        RagIndexService ragIndex = mock(RagIndexService.class);
        RagService service = new RagService(postMapper, postTagMapper, tagMapper, userMapper, ragIndex);

        List<Post> posts = publishedPosts(12);
        when(postMapper.selectList(any(Wrapper.class))).thenReturn(posts);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        when(postTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(ragIndex.embeddingBatchSize()).thenReturn(10);
        when(ragIndex.replaceChunksBatch(anyMap())).thenAnswer(invocation -> {
            Map<Long, List<Document>> batch = invocation.getArgument(0);
            return batch.values().stream().mapToInt(List::size).sum();
        });

        assertEquals(12, service.syncPostChunksToElasticsearch(false));

        /*
         * 每篇短帖只生成一个 chunk，因此 12 篇应形成 10+2 两批；Map 使用插入顺序，
         * 同时也能验证批处理没有遗漏或重排帖子。
         */
        org.mockito.ArgumentCaptor<Map<Long, List<Document>>> batches =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(ragIndex, times(2)).replaceChunksBatch(batches.capture());
        assertEquals(List.of(10, 2), batches.getAllValues().stream().map(Map::size).toList());
        assertEquals(posts.stream().map(Post::getId).toList(), batches.getAllValues().stream()
                .flatMap(batch -> batch.keySet().stream())
                .toList());
        verify(postMapper, times(12)).update(isNull(), any());
    }

    @Test
    void shouldNotMarkAnyPostInBatchWhenEmbeddingOrIndexWriteFails() {
        PostMapper postMapper = mock(PostMapper.class);
        PostTagMapper postTagMapper = mock(PostTagMapper.class);
        TagMapper tagMapper = mock(TagMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        RagIndexService ragIndex = mock(RagIndexService.class);
        RagService service = new RagService(postMapper, postTagMapper, tagMapper, userMapper, ragIndex);

        when(postMapper.selectList(any(Wrapper.class))).thenReturn(publishedPosts(2));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        when(postTagMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(ragIndex.embeddingBatchSize()).thenReturn(10);
        when(ragIndex.replaceChunksBatch(anyMap()))
                .thenThrow(new IllegalStateException("模拟 Embedding 服务限流"));

        assertThrows(IllegalStateException.class,
                () -> service.syncPostChunksToElasticsearch(false));

        // 整批失败时不得提前标记任何帖子，否则下次增量同步无法自动补偿。
        verify(postMapper, never()).update(isNull(), any());
    }

    /** 构造指定数量的已发布短帖，保证每篇只生成一个 RAG chunk。 */
    private List<Post> publishedPosts(int count) {
        List<Post> posts = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            Post post = new Post();
            post.setId((long) index);
            post.setUserId(9L);
            post.setTitle("测试攻略 " + index);
            post.setContent("这是第 " + index + " 篇已发布的短帖正文。");
            post.setIsDraft(0);
            posts.add(post);
        }
        return posts;
    }

}
