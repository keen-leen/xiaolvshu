package com.xiaolvshu.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SearchRequest extends PageRequest {
    private String keyword;
    private String tag;
    private String type; // all, posts, videos, users
}
