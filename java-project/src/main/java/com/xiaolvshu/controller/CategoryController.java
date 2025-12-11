package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {
    
    private final CategoryService categoryService;
    
    /**
     * 获取分类列表
     */
    @GetMapping
    public Result<List<Category>> getCategories() {
        List<Category> categories = categoryService.list();
        return Result.success(categories);
    }
}
