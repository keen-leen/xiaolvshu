package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;

/**
 * 旅行 Agent 对外引用。
 *
 * <p>该类型从旧的同步响应对象中独立出来。当前 Agent 只通过
 * SSE 的 refs 事件返回引用，不再维护一份从未使用过的同步响应对象。</p>
 */
@Data
public class TravelNoteReference {

    /** 与最终回答中的 [S1]、[S2] 一一对应。 */
    private String sourceId;

    private Long postId;
    private String title;
    private String author;
    private String summary;
    private String link;
    private List<String> tags;
    private Double score;
}
