package com.xiaolvshu.dto;

import lombok.Data;
import java.util.List;

@Data
public class SearchResponse {

    @Data
    public static class SearchResponseItem<T> {
        private List<T> data;
        private List<TagStatsDTO> tagStats;
        private PaginationDTO pagination;
    }
    private String keyword;
    private String tag;
    private String type;
    
    // For type = 'all'
    private List<PostResponse> data;
    private List<TagStatsDTO> tagStats;
    private PaginationDTO pagination;
    

    // For type = 'posts' or 'videos'
    private SearchResponseItem<PostResponse> posts;

    // For type = 'users'
    private SearchResponseItem<UserResponse> users;
}
