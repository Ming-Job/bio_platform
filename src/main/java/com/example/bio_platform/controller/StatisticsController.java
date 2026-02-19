package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result;
import com.example.bio_platform.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据统计控制器 - 精简版
 */
@RestController
@RequestMapping("/api/statistics")
@Api(tags = "数据统计")
@Slf4j
public class StatisticsController {

    @Autowired
    private UserService userService;

    /**
     * 获取用户增长图表数据 - 核心接口
     * 返回ECharts可以直接使用的格式
     */
    @GetMapping("/user-growth/chart")
    @ApiOperation("获取用户增长图表数据")
    public Result getUserGrowthChartData(
            @ApiParam(value = "时间段: 7d, 30d, 90d", defaultValue = "30d")
            @RequestParam(value = "period", defaultValue = "30d") String period) {

        log.info("获取用户增长图表数据，时间段: {}", period);

        try {
            // 将period转换为天数
            int days;
            switch (period.toLowerCase()) {
                case "7d":
                    days = 7;
                    break;
                case "30d":
                    days = 30;
                    break;
                case "90d":
                    days = 90;
                    break;
                default:
                    days = 30;
            }

            // 获取原始数据
            List<Map<String, Object>> growthData = userService.getUserGrowthTrend(days);

            // 转换为ECharts图表格式
            Map<String, Object> chartData = convertToChartFormat(growthData);
            chartData.put("period", period);

            return Result.success("获取图表数据成功", chartData);

        } catch (Exception e) {
            log.error("获取用户增长图表数据失败", e);
            return Result.error("获取图表数据失败");
        }
    }

    /**
     * 获取用户增长原始数据（备用接口）
     */
    @GetMapping("/user-growth")
    @ApiOperation("获取用户增长原始数据")
    public Result getUserGrowthData(
            @RequestParam(value = "period", defaultValue = "30d") String period) {

        try {
            int days;
            switch (period.toLowerCase()) {
                case "7d": days = 7; break;
                case "30d": days = 30; break;
                case "90d": days = 90; break;
                default: days = 30;
            }

            List<Map<String, Object>> growthData = userService.getUserGrowthTrend(days);

            Map<String, Object> result = new HashMap<>();
            result.put("period", period);
            result.put("data", growthData);

            return Result.success("获取数据成功", result);

        } catch (Exception e) {
            log.error("获取用户增长数据失败", e);
            return Result.error("获取数据失败");
        }
    }

    /**
     * 将数据库查询结果转换为ECharts图表格式
     */
    private Map<String, Object> convertToChartFormat(List<Map<String, Object>> growthData) {
        Map<String, Object> chartData = new HashMap<>();

        if (growthData == null || growthData.isEmpty()) {
            // 返回空数据结构
            chartData.put("dates", new ArrayList<>());
            chartData.put("newUsers", new ArrayList<>());
            chartData.put("totalUsers", new ArrayList<>());
            return chartData;
        }

        List<String> dates = new ArrayList<>();
        List<Integer> newUsers = new ArrayList<>();
        List<Integer> totalUsers = new ArrayList<>();

        for (Map<String, Object> dayData : growthData) {
            // 日期格式化：只显示月-日，避免显示过长
            String date = (String) dayData.get("date");
            String displayDate = formatDateForDisplay(date);

            dates.add(displayDate);
            newUsers.add(((Number) dayData.get("new_users")).intValue());
            totalUsers.add(((Number) dayData.get("total_users")).intValue());
        }

        chartData.put("dates", dates);
        chartData.put("newUsers", newUsers);
        chartData.put("totalUsers", totalUsers);

        // 添加一些统计数据供前端显示
        chartData.put("totalNewUsers", calculateSum(newUsers));
        chartData.put("maxNewUsers", calculateMax(newUsers));

        return chartData;
    }

    /**
     * 日期格式化：将 "2025-12-18" 转换为 "12-18"
     */
    private String formatDateForDisplay(String date) {
        if (date == null || date.length() < 10) {
            return date;
        }
        return date.substring(5); // 返回 "MM-DD"
    }

    private int calculateSum(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).sum();
    }

    private int calculateMax(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}