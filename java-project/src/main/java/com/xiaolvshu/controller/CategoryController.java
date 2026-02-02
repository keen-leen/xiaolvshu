package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.common.constant.RedisExpireConstant;
import com.xiaolvshu.dto.CategoryQueryRequest;
import com.xiaolvshu.dto.CategoryResponse;
import com.xiaolvshu.service.CacheService;
import com.xiaolvshu.service.CategoryService;
import com.xiaolvshu.utils.RedisKeyUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    
    private final CategoryService categoryService;
    private final CacheService cacheService;
    
    /**
     * 获取分类列表（支持搜索、排序和笔记数量统计）
     * GET /categories?name=xxx&sortField=id&sortOrder=asc
     */
    @GetMapping
    public Result<List<CategoryResponse>> getCategories(CategoryQueryRequest request) {
        log.info("获取分类列表 - 名称: {}, 排序字段: {}, 排序方式: {}", request.getName(), request.getSortField(), request.getSortOrder());
        String categoryListKey = RedisKeyUtil.getCategoryListKey();
        List<CategoryResponse> categories = cacheService.getOrLoadList(categoryListKey, RedisExpireConstant.CATEGORY_LIST_EXPIRE, () -> categoryService.getCategoriesWithPostCount(request));

        return Result.success("获取成功", categories);
    }
}
