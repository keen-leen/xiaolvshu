package com.xiaolvshu.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiaolvshu.dto.CategoryDTO;
import com.xiaolvshu.entity.Category;
import com.xiaolvshu.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 分类服务
 */
@Service
@RequiredArgsConstructor
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {
    
    private final CategoryMapper categoryMapper;
    
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
