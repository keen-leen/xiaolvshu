package com.xiaolvshu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xiaolvshu.dto.CategoryDTO;
import com.xiaolvshu.dto.CategoryQueryRequest;
import com.xiaolvshu.dto.CategoryResponse;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.entity.Post;
import com.xiaolvshu.mapper.CategoryMapper;
import com.xiaolvshu.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类服务
 */
@Service
@RequiredArgsConstructor
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {
    
    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;
    
    /**
     * 获取分类列表（带笔记数量统计、搜索和排序）
     *
     * @param request 查询参数
     * @return 分类列表
     */
    public List<CategoryResponse> getCategoriesWithPostCount(CategoryQueryRequest request) {
        // 构建查询条件
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        
        // 名称模糊搜索
        if (StringUtils.hasText(request.getName())) {
            wrapper.like(Category::getName, request.getName().trim());
        }
        
        // 英文标题模糊搜索
        if (StringUtils.hasText(request.getCategoryTitle())) {
            wrapper.like(Category::getCategoryTitle, request.getCategoryTitle().trim());
        }
        
        // 排序处理
        List<String> allowedSortFields = Arrays.asList("id", "name", "created_at", "post_count");
        String sortField = request.getSortField();
        boolean isAsc = "asc".equalsIgnoreCase(request.getSortOrder());
        
        if (sortField != null && allowedSortFields.contains(sortField)) {
            switch (sortField) {
                case "id":
                    wrapper.orderBy(true, isAsc, Category::getId);
                    break;
                case "name":
                    wrapper.orderBy(true, isAsc, Category::getName);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Category::getCreatedAt);
                    break;
                case "post_count":
                    // post_count需要在Java中排序
                    break;
                default:
                    wrapper.orderBy(true, isAsc, Category::getId);
            }
        } else {
            wrapper.orderByAsc(Category::getId);
        }
        
        List<Category> categories = this.list(wrapper);
        
        if (categories.isEmpty()) {
            return List.of();
        }
        
        // 统计每个分类的笔记数量
        List<Integer> categoryIds = categories.stream()
                .map(Category::getId)
                .collect(Collectors.toList());
        
        // 查询每个分类的笔记数量
        LambdaQueryWrapper<Post> postWrapper = new LambdaQueryWrapper<>();
        postWrapper.in(Post::getCategoryId, categoryIds);
        postWrapper.eq(Post::getIsDraft, 0);
        List<Post> posts = postMapper.selectList(postWrapper);
        
        Map<Integer, Long> postCountMap = posts.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Post::getCategoryId, Collectors.counting()));
        
        // 转换为响应DTO
        List<CategoryResponse> responses = categories.stream().map(category -> {
            CategoryResponse response = new CategoryResponse();
            response.setId(category.getId());
            response.setName(category.getName());
            response.setCategoryTitle(category.getCategoryTitle());
            response.setCreatedAt(category.getCreatedAt());
            response.setPostCount(postCountMap.getOrDefault(category.getId(), 0L));
            return response;
        }).collect(Collectors.toList());
        
        // 如果按post_count排序
        if ("post_count".equals(sortField)) {
            if (isAsc) {
                responses.sort((a, b) -> Long.compare(a.getPostCount(), b.getPostCount()));
            } else {
                responses.sort((a, b) -> Long.compare(b.getPostCount(), a.getPostCount()));
            }
        }
        
        return responses;
    }
    
    /**
     * 根据ID获取分类
     */
    public CategoryDTO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return null;
        }
        return convertToDTO(category);
    }
    
    /**
     * 转换为DTO
     */
    private CategoryDTO convertToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        BeanUtils.copyProperties(category, dto);
        return dto;
    }
}
