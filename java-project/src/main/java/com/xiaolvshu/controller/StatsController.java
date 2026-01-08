package com.xiaolvshu.controller;

import com.xiaolvshu.common.Result;
import com.xiaolvshu.dto.SystemStatsResponse;
import com.xiaolvshu.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取系统统计信息
     * GET /stats
     */
    @GetMapping
    public Result<SystemStatsResponse> getSystemStats() {
        log.info("获取系统统计信息");
        SystemStatsResponse stats = statsService.getSystemStats();
        return Result.success("获取统计信息成功", stats);
    }
}
