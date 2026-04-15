package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result;
import com.example.bio_platform.entity.TaskGeneExpression;
import com.example.bio_platform.service.AnalysisTaskService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/task/result")
@Slf4j
public class TaskResultController {

    @Autowired
    private AnalysisTaskService analysisTaskService;

    /**
     * GET /api/task/result/expression/{taskId}
     * 提供给前端 Echarts 图表渲染的数据接口
     */
    @GetMapping("/expression/{taskId}")
    public List<TaskGeneExpression> getGeneExpression(@PathVariable Long taskId) {
        // 在严谨的工程中，这里可以统一包装一个 Result<T> 返回体
        // 为了快速联调，这里直接返回 List 供前端解析
        return analysisTaskService.getGeneExpressionData(taskId);
    }


    /**
     * 获取指定任务的 GWAS 曼哈顿图数据
     */
    @GetMapping("/{taskId}/manhattan")
    @ApiOperation("获取 GWAS 曼哈顿图数据")
    public Result<Map<String, Object>> getGwasManhattanData(@PathVariable Long taskId) {
        try {
            Map<String, Object> data = analysisTaskService.getGwasManhattanData(taskId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取曼哈顿图数据失败，任务ID: {}", taskId, e);
            return Result.error("获取图表数据失败: " + e.getMessage());
        }
    }
}