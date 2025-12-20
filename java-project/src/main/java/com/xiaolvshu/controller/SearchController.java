package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.SearchRequest;
import com.xiaolvshu.dto.SearchResponse;
import com.xiaolvshu.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public Result<SearchResponse> search(SearchRequest request) {
        log.info("通用搜索 - 关键词: {}, 标签: {}, 类型: {}, 页码: {}, 每页: {}", 
                request.getKeyword(), request.getTag(), request.getType(), request.getPage(), request.getLimit());
        SearchResponse response = searchService.search(request);
        return Result.success(response);
    }
}
