package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bio_platform.entity.AnalysisPipeline;
import com.example.bio_platform.service.AnalysisPipelineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipelines")
@RequiredArgsConstructor
@Api(tags = "云端分析流程模板管理")
public class AnalysisPipelineController {

    private final AnalysisPipelineService pipelineService;

    @ApiOperation("获取所有分析流程列表")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPipelines() {
        // 按照 sort_order 排序返回
        LambdaQueryWrapper<AnalysisPipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AnalysisPipeline::getSortOrder)
                .orderByDesc(AnalysisPipeline::getCreatedAt);

        List<AnalysisPipeline> list = pipelineService.list(wrapper);

        // 兼容前端需要的 { code: 200, data: [...] } 格式
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", list);
        result.put("message", "获取成功");

        return ResponseEntity.ok(result);
    }

    @ApiOperation("新建分析流程")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPipeline(@RequestBody AnalysisPipeline pipeline) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = pipelineService.createPipeline(pipeline);
            result.put("code", success ? 200 : 400);
            result.put("message", success ? "流程创建成功" : "创建失败");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @ApiOperation("修改分析流程配置")
    @PutMapping
    public ResponseEntity<Map<String, Object>> updatePipeline(@RequestBody AnalysisPipeline pipeline) {
        Map<String, Object> result = new HashMap<>();
        if (pipeline.getId() == null) {
            result.put("code", 400);
            result.put("message", "ID不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        // pipelineCode 不允许随意修改，保障底层稳定
        pipeline.setPipelineCode(null);

        boolean success = pipelineService.updateById(pipeline);
        result.put("code", success ? 200 : 400);
        result.put("message", success ? "流程更新成功" : "更新失败");
        return ResponseEntity.ok(result);
    }

    @ApiOperation("删除分析流程")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePipeline(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        // 🚨 可选扩展：在这里可以校验该流程是否还有正在运行的任务，如果有则禁止删除
        boolean success = pipelineService.removeById(id);

        result.put("code", success ? 200 : 400);
        result.put("message", success ? "流程删除成功" : "流程不存在或删除失败");
        return ResponseEntity.ok(result);
    }
}