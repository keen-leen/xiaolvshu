package com.xiaolvshu.service;

import com.xiaolvshu.dto.CommunitySearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TravelAgentTools {

    private final RagService travelRagService;

    /**
     * 社区笔记检索工具。
     * <p>
     * RAG 在 Agent 中被当作普通工具使用，而不是固定前置步骤；攻略、路线、避坑等问题通常会优先调用它。
     * 当前 Agent 仍由后端手动执行工具，@Tool 主要作为工具 schema 元信息和后续接入 Spring AI 原生 tool-calling 的准备。
     */
    @Tool(name = "search_community_notes", description = "检索小旅书社区真实旅行笔记，适合攻略、路线、景点、美食、避坑、小众玩法等问题。")
    public CommunitySearchResult searchCommunityNotes(
            @ToolParam(description = "用户查询文本") String query,
            @ToolParam(description = "目的地，可为空", required = false) String destination,
            @ToolParam(description = "兴趣偏好列表，可为空", required = false) List<String> interests,
            @ToolParam(description = "返回条数，1到10", required = false) Integer topK) {
        return travelRagService.searchCommunityNotes(query, destination, interests, topK);
    }

}
