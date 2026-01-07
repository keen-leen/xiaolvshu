package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.AdminCategoryDTO;
import com.xiaolvshu.dto.admin.AdminCategoryQueryDTO;
import com.xiaolvshu.dto.admin.AdminResult;
import com.xiaolvshu.dto.admin.BatchDeleteDTO;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 管理端分类管理控制器
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * 分页查询分类列表（包含笔记数量）
     */
    @GetMapping
    public AdminResult<?> getCategoryList(AdminCategoryQueryDTO queryDTO) {
        Page<Category> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();

        // 搜索条件
        if (queryDTO.getName() != null && !queryDTO.getName().trim().isEmpty()) {
            wrapper.like(Category::getName, queryDTO.getName().trim());
        }
        if (queryDTO.getCategoryTitle() != null && !queryDTO.getCategoryTitle().trim().isEmpty()) {
            wrapper.like(Category::getCategoryTitle, queryDTO.getCategoryTitle().trim());
        }

        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "name", "category_title", "created_at", "post_count");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Category::getId);
                    break;
                case "name":
                    wrapper.orderBy(true, isAsc, Category::getName);
                    break;
                case "category_title":
                    wrapper.orderBy(true, isAsc, Category::getCategoryTitle);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Category::getCreatedAt);
                    break;
                case "post_count":
                    wrapper.orderBy(true, isAsc, Category::getPostCount);
                    break;
            }
        } else {
            // 默认按id升序
            wrapper.orderByAsc(Category::getId);
        }

        IPage<Category> result = categoryService.page(pageParam, wrapper);

        // 转换为DTO并添加笔记数量
        List<AdminCategoryDTO> categoryDTOs = new ArrayList<>();
        for (Category category : result.getRecords()) {
            AdminCategoryDTO dto = new AdminCategoryDTO();
            dto.setId(category.getId());
            dto.setName(category.getName());
            dto.setCategoryTitle(category.getCategoryTitle());
            dto.setCreatedAt(category.getCreatedAt());
            dto.setPostCount(category.getPostCount());
            
            categoryDTOs.add(dto);
        }

        return AdminResult.success(categoryDTOs, result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个分类详情
     */
    @GetMapping("/{id}")
    public AdminResult<Category> getCategoryById(@PathVariable Integer id) {
        Category category = categoryService.getById(id);
        if (category == null) {
            return AdminResult.notFound("分类不存在");
        }
        return AdminResult.success("操作成功", category);
    }

    /**
     * 创建分类
     */
    @PostMapping
    public AdminResult<Map<String, Integer>> createCategory(@RequestBody Category category) {
        // 验证必填字段
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            return AdminResult.badRequest("分类名称不能为空");
        }
        if (category.getCategoryTitle() == null || category.getCategoryTitle().trim().isEmpty()) {
            return AdminResult.badRequest("分类英文标题不能为空");
        }

        // 清理数据
        category.setName(category.getName().trim());
        category.setCategoryTitle(category.getCategoryTitle().trim());

        // 检查分类名称是否已存在
        LambdaQueryWrapper<Category> nameWrapper = new LambdaQueryWrapper<>();
        nameWrapper.eq(Category::getName, category.getName());
        if (categoryService.count(nameWrapper) > 0) {
            return AdminResult.conflict("分类名称已存在");
        }

        // 检查分类英文标题是否已存在
        LambdaQueryWrapper<Category> titleWrapper = new LambdaQueryWrapper<>();
        titleWrapper.eq(Category::getCategoryTitle, category.getCategoryTitle());
        if (categoryService.count(titleWrapper) > 0) {
            return AdminResult.conflict("分类英文标题已存在");
        }

        categoryService.save(category);
        return AdminResult.success("分类创建成功", Map.of("id", category.getId()));
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateCategory(@PathVariable Integer id, @RequestBody Category category) {
        Category existingCategory = categoryService.getById(id);
        if (existingCategory == null) {
            return AdminResult.notFound("分类不存在");
        }

        // 更新名称
        if (category.getName() != null) {
            if (category.getName().trim().isEmpty()) {
                return AdminResult.badRequest("分类名称不能为空");
            }
            
            String newName = category.getName().trim();
            // 检查名称是否与其他分类冲突
            LambdaQueryWrapper<Category> nameWrapper = new LambdaQueryWrapper<>();
            nameWrapper.eq(Category::getName, newName).ne(Category::getId, id);
            if (categoryService.count(nameWrapper) > 0) {
                return AdminResult.conflict("分类名称已存在");
            }
            existingCategory.setName(newName);
        }

        // 更新英文标题
        if (category.getCategoryTitle() != null) {
            if (category.getCategoryTitle().trim().isEmpty()) {
                return AdminResult.badRequest("分类英文标题不能为空");
            }
            
            String newTitle = category.getCategoryTitle().trim();
            // 检查英文标题是否与其他分类冲突
            LambdaQueryWrapper<Category> titleWrapper = new LambdaQueryWrapper<>();
            titleWrapper.eq(Category::getCategoryTitle, newTitle).ne(Category::getId, id);
            if (categoryService.count(titleWrapper) > 0) {
                return AdminResult.conflict("分类英文标题已存在");
            }
            existingCategory.setCategoryTitle(newTitle);
        }

        categoryService.updateById(existingCategory);
        return AdminResult.success("分类更新成功");
    }

    /**
     * 删除单个分类
     */
    @DeleteMapping("/{id}")
    public AdminResult<Void> deleteCategory(@PathVariable Integer id) {
        Category category = categoryService.getById(id);
        if (category == null) {
            return AdminResult.notFound("分类不存在");
        }

        // 检查是否有笔记使用此分类
        Long postCount = category.getPostCount() != null ? category.getPostCount() : 0L;
        if (postCount > 0) {
            return AdminResult.badRequest("该分类下还有 " + postCount + " 篇笔记，无法删除");
        }

        categoryService.removeById(id);
        return AdminResult.success("分类删除成功");
    }

    /**
     * 批量删除分类
     */
    @DeleteMapping
    public AdminResult<Map<String, Integer>> deleteCategories(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }

        // 检查每个分类下是否有笔记
        List<Integer> categoryIds = deleteDTO.getIds().stream()
                .map(Long::intValue)
                .toList();
        
        for (Integer categoryId : categoryIds) {
            Category category = categoryService.getById(categoryId);
            Long postCount = (category != null && category.getPostCount() != null) ? category.getPostCount() : 0L;
            if (postCount > 0) {
                return AdminResult.badRequest("分类 " + categoryId + " 下还有笔记，无法删除");
            }
        }

        int deletedCount = categoryService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "个分类", Map.of("deletedCount", deletedCount));
    }
}
