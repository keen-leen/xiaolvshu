package com.xiaolvshu.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiaolvshu.dto.admin.*;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理端标签管理控制器
 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    /**
     * 分页查询标签列表
     */
    @GetMapping
    public AdminResult<?> getTagList(AdminTagQueryDTO queryDTO) {
        Page<Tag> pageParam = new Page<>(queryDTO.getPage(), queryDTO.getLimit());
        
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        
        // 名称模糊搜索
        if (queryDTO.getName() != null && !queryDTO.getName().trim().isEmpty()) {
            wrapper.like(Tag::getName, queryDTO.getName().trim());
        }
        
        // 排序
        List<String> allowedSortFields = Arrays.asList("id", "use_count", "created_at");
        if (queryDTO.getSortField() != null && allowedSortFields.contains(queryDTO.getSortField())) {
            boolean isAsc = "asc".equalsIgnoreCase(queryDTO.getSortOrder());
            switch (queryDTO.getSortField()) {
                case "id":
                    wrapper.orderBy(true, isAsc, Tag::getId);
                    break;
                case "use_count":
                    wrapper.orderBy(true, isAsc, Tag::getUseCount);
                    break;
                case "created_at":
                    wrapper.orderBy(true, isAsc, Tag::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Tag::getCreatedAt);
        }
        
        IPage<Tag> result = tagService.page(pageParam, wrapper);
        return AdminResult.success(result.getRecords(), result.getTotal(), queryDTO.getPage(), queryDTO.getLimit());
    }

    /**
     * 获取单个标签详情
     */
    @GetMapping("/{id}")
    public AdminResult<Tag> getTagById(@PathVariable Integer id) {
        Tag tag = tagService.getById(id);
        if (tag == null) {
            return AdminResult.notFound("标签不存在");
        }
        return AdminResult.success("操作成功", tag);
    }

    /**
     * 创建标签
     */
    @PostMapping
    public AdminResult<Map<String, Integer>> createTag(@RequestBody Tag tag) {
        // 验证必填字段
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: name");
        }
        
        // 检查标签名称是否已存在
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, tag.getName().trim());
        if (tagService.count(wrapper) > 0) {
            return AdminResult.conflict("标签名称已存在");
        }
        
        tag.setName(tag.getName().trim());
        tag.setUseCount(0);
        tagService.save(tag);
        
        return AdminResult.success("标签创建成功", Map.of("id", tag.getId()));
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public AdminResult<Void> updateTag(@PathVariable Integer id, @RequestBody Tag tag) {
        Tag existingTag = tagService.getById(id);
        if (existingTag == null) {
            return AdminResult.notFound("标签不存在");
        }
        
        if (tag.getName() != null && !tag.getName().trim().isEmpty()) {
            // 检查名称是否与其他标签冲突
            LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Tag::getName, tag.getName().trim()).ne(Tag::getId, id);
            if (tagService.count(wrapper) > 0) {
                return AdminResult.conflict("标签名称已存在");
            }
            existingTag.setName(tag.getName().trim());
        }
        
        tagService.updateById(existingTag);
        return AdminResult.success("标签更新成功");
    }

    /**
     * 删除单个标签
     */
    @DeleteMapping("/{id}")
    @Transactional
    public AdminResult<Void> deleteTag(@PathVariable Integer id) {
        Tag tag = tagService.getById(id);
        if (tag == null) {
            return AdminResult.notFound("标签不存在");
        }
        
        tagService.removeById(id);
        return AdminResult.success("标签删除成功");
    }

    /**
     * 批量删除标签
     */
    @DeleteMapping
    @Transactional
    public AdminResult<Map<String, Integer>> deleteTags(@RequestBody BatchDeleteDTO deleteDTO) {
        if (deleteDTO.getIds() == null || deleteDTO.getIds().isEmpty()) {
            return AdminResult.badRequest("缺少必填字段: ids");
        }
        
        int deletedCount = tagService.removeByIds(deleteDTO.getIds()) ? deleteDTO.getIds().size() : 0;
        return AdminResult.success("成功删除" + deletedCount + "个标签", Map.of("deletedCount", deletedCount));
    }
}
