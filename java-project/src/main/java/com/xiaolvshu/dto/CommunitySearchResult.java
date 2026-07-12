package com.xiaolvshu.dto;

import lombok.Data;

import java.util.List;

@Data
public class CommunitySearchResult {

    private String query;

    private String contextText;

    private List<TravelChatResponse.TravelNoteReference> references;
}
