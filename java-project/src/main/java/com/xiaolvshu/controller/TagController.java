package com.xiaolvshu.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.entity.Tag;
import com.xiaolvshu.service.TagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {
    private final TagService tagService;

    /**
     * 获取所有标签
     */
    @GetMapping
    public Result<List<Tag>> getAllTags() {
        List<Tag> tags = tagService.list();
        return Result.success(tags);
    }

    /**
     * 获取热门标签
     */
    @GetMapping("/hot")
    public Result<List<Tag>> getHotTags(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        List<Tag> hotTags = tagService.lambdaQuery().orderByDesc(Tag::getUseCount).last("LIMIT " + limit).list();
        return Result.success(hotTags);
    }
}
