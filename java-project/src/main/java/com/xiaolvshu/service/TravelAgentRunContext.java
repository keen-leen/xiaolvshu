package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import com.xiaolvshu.dto.TravelNoteReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单次旅行 Agent 运行的业务上下文。
 *
 * <p>Spring AI 2.0 会通过 {@code ToolContext} 把这个对象传给工具，但不会把它序列化给模型。
 * 因此它适合保存服务端专用状态：工具成本上限、SSE 状态回调和引用注册表。把这些状态放在
 * 请求对象中，而不是 ThreadLocal 中，也能正确适配 ToolCallingAdvisor 在 Reactor 线程间切换。</p>
 */
final class TravelAgentRunContext {

    static final String TOOL_CONTEXT_KEY = "xiaolvshu.travelAgentRun";

    private static final Pattern LOCAL_SOURCE_PATTERN = Pattern.compile("\\[S(\\d+)]");
    private static final String UNTRUSTED_BEGIN = "--- BEGIN UNTRUSTED COMMUNITY NOTES ---";
    private static final String UNTRUSTED_END = "--- END UNTRUSTED COMMUNITY NOTES ---";
    private static final String CITATION_REQUIREMENTS_BEGIN = "--- BEGIN CITATION REQUIREMENTS ---";
    private static final String CITATION_REQUIREMENTS_END = "--- END CITATION REQUIREMENTS ---";
    private static final String NO_RELIABLE_NOTES = "未检索到可靠社区笔记";

    private final int requestedTopK;
    private final int maxToolCalls;
    // 负责处理agent状态的回调，通常是向SSE发送status事件
    private final Consumer<AgentStatus> statusConsumer;
    private final Map<Long, TravelNoteReference> referencesByPostId = new LinkedHashMap<>();
    private int toolCalls;

    TravelAgentRunContext(int requestedTopK,
                          int maxToolCalls,
                          Consumer<AgentStatus> statusConsumer) {
        this.requestedTopK = Math.max(1, Math.min(10, requestedTopK));
        this.maxToolCalls = Math.max(1, maxToolCalls);
        this.statusConsumer = statusConsumer == null ? ignored -> { } : statusConsumer;
    }

    /**
     * 为一次社区检索准备调用配额、运行状态和允许的召回数量。
     *
     * <p>模型可以为了一个更聚焦的问题主动请求更小的 topK，但不能突破用户请求和服务端
     * 校验确定的上限。计数在执行前增加，保证模型反复请求工具时最多消耗固定次数。</p>
     */
    int prepareCommunitySearch(Integer modelTopK) {
        toolCalls++;
        if (toolCalls > maxToolCalls) {
            throw new IllegalStateException("本次对话的社区检索次数已达上限");
        }
        statusConsumer.accept(new AgentStatus("searching", "正在检索社区旅行笔记"));
        int desired = modelTopK == null ? requestedTopK : Math.max(1, modelTopK);
        return Math.min(requestedTopK, Math.min(10, desired));
    }

    /**
     * 把一次 RAG 调用内部的 S1、S2 编号转换为整次 Agent 运行共享的编号。
     *
     * <p>RAG 每次检索都会从 S1 开始。如果模型在一轮中进行了两次检索，直接拼接结果会让两个
     * 不同帖子都叫 S1。这里按引用列表顺序建立“本地编号 → 全局编号”映射，并以 postId 去重，
     * 最终正文中的编号和 refs 卡片由同一个注册表产生。</p>
     */
    String registerSearchResult(CommunitySearchResult result) {
        if (result == null || result.getContextText() == null || result.getContextText().isBlank()) {
            statusConsumer.accept(new AgentStatus("writing", "正在整理回答"));
            return NO_RELIABLE_NOTES;
        }

        List<TravelNoteReference> localReferences = result.getReferences() == null
                ? Collections.emptyList() : result.getReferences();
        Map<String, String> sourceMapping = new LinkedHashMap<>();
        for (int i = 0; i < localReferences.size(); i++) {
            TravelNoteReference reference = localReferences.get(i);
            if (reference == null || reference.getPostId() == null) {
                continue;
            }
            TravelNoteReference global = referencesByPostId.computeIfAbsent(
                    reference.getPostId(), ignored -> copyWithSourceId(reference, referencesByPostId.size() + 1));
            sourceMapping.put("S" + (i + 1), global.getSourceId());
        }
        /*
         * RagService 的空结果文本是“未检索到可靠社区笔记”，它本身不是空字符串；因此是否成功不能
         * 只看 contextText。至少注册到一个带 postId 的引用，才说明模型确实获得了可追溯资料，
         * 也才允许要求正文生成尾注。这样可以避免无来源时诱导模型虚构 [S1]。
         */
        if (sourceMapping.isEmpty()) {
            statusConsumer.accept(new AgentStatus("writing", "正在整理回答"));
            return NO_RELIABLE_NOTES;
        }

        Matcher matcher = LOCAL_SOURCE_PATTERN.matcher(result.getContextText());
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String globalSource = sourceMapping.get("S" + matcher.group(1));
            String replacement = globalSource == null ? "[来源不可用]" : "[" + globalSource + "]";
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        statusConsumer.accept(new AgentStatus("writing", "正在结合社区笔记整理回答"));
        /*
         * 社区正文由用户发布，可能故意包含“忽略系统指令”等提示注入文本。
         * 明确的数据边界会随 ToolResponseMessage 一起交给模型，配合系统提示把它限定为引用资料。
         *
         * 引用规则由应用生成并放在不可信数据边界之外，而且只在本次工具确实注册到引用时出现。
         * 合法编号取自整次运行的引用注册表：多次检索时后一次会看到累计集合，既允许继续使用
         * 前一次资料，又不会因每次 RAG 都从 S1 编号而产生歧义。
         */
        return UNTRUSTED_BEGIN + "\n"
                + rewritten
                + "\n" + UNTRUSTED_END
                + "\n\n" + renderCitationRequirements();
    }

    List<TravelNoteReference> references() {
        return new ArrayList<>(referencesByPostId.values());
    }

    /**
     * 生成仅对成功 RAG 结果可见的最终回答约束。
     *
     * <p>尾注采用运行级引用注册表中的真实编号，而不是在提示词中预设数量。模型必须至少使用
     * 一条检索事实，但只能标注这里列出的编号，并且尾注要紧邻受该来源直接支持的句子。</p>
     */
    private String renderCitationRequirements() {
        String allowedSources = referencesByPostId.values().stream()
                .map(reference -> "[" + reference.getSourceId() + "]")
                .reduce((left, right) -> left + "、" + right)
                .orElseThrow();
        return CITATION_REQUIREMENTS_BEGIN + "\n"
                + "本次运行允许使用的尾注仅限：" + allowedSources + "。\n"
                + "生成最终回答时必须遵守：\n"
                + "1. 正文至少使用一条上述社区笔记直接支持的具体事实或建议。\n"
                + "2. 在该事实或建议所在句子的末尾紧邻标注对应尾注，例如 [S1] 或 [S1][S2]。\n"
                + "3. 只能使用允许集合中实际存在的编号，不得虚构、猜测或引用其他编号。\n"
                + "4. 每个尾注必须直接支持它前面的内容，不能为了满足格式而随意标注。\n"
                + CITATION_REQUIREMENTS_END;
    }

    private TravelNoteReference copyWithSourceId(TravelNoteReference source, int sourceNumber) {
        TravelNoteReference copy = new TravelNoteReference();
        copy.setSourceId("S" + sourceNumber);
        copy.setPostId(source.getPostId());
        copy.setTitle(source.getTitle());
        copy.setAuthor(source.getAuthor());
        copy.setSummary(source.getSummary());
        copy.setLink(source.getLink());
        copy.setTags(source.getTags() == null ? Collections.emptyList() : List.copyOf(source.getTags()));
        copy.setScore(source.getScore());
        return copy;
    }

    record AgentStatus(String code, String message) {
    }
}
