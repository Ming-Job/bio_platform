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