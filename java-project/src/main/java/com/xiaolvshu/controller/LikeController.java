package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.LikeRequest;
import com.xiaolvshu.dto.LikeResponse;
import com.xiaolvshu.service.LikeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Slf4j
public class LikeController {

    private final LikeService likeService;
    /**
     * 点赞/取消点赞接口
     */
    @PostMapping
    public Result<LikeResponse> like(@Valid @RequestBody LikeRequest likeRequest)  {
        log.info("点赞目标：{}，点赞类型：{}", likeRequest.getTargetId(), likeRequest.getTargetType());
        LikeResponse response = likeService.likes(likeRequest);
        return Result.success("点赞成功", response);
    }
    @DeleteMapping
    public Result<LikeResponse> unlike(@Valid @RequestBody LikeRequest likeRequest)  {
        log.info("取消点赞目标：{}，点赞类型：{}", likeRequest.getTargetId(), likeRequest.getTargetType());
        LikeResponse response = likeService.likes(likeRequest);
        return Result.success("取消点赞成功", response);
    }
}
