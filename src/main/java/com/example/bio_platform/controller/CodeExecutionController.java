package com.example.bio_platform.controller;

import com.example.bio_platform.common.ExecutionResult;
import com.example.bio_platform.service.CodeExecutionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@Api(tags = "代码执行接口")
@RequestMapping("/api/code")
@Slf4j
public class CodeExecutionController {

    @Autowired
    private CodeExecutionService codeExecutionService;

    @Data
    public static class CodeRequest {
        private String code;
        private String language; // python / r
        private String taskId;   // 可选
    }

    /**
     * 异步执行代码
     */
    @PostMapping("/execute")
    @ApiOperation("异步执行代码")
    public ResponseEntity<Map<String, Object>> executeCodeAsync(@RequestBody CodeRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        String taskId = request.getTaskId();

        log.info("收到异步代码执行请求: language={}, codeLength={}",
                language, code != null ? code.length() : 0);

        // 参数验证
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "代码不能为空",
                    "status", "error"
            ));
        }

        if (language == null || (!language.equalsIgnoreCase("python") && !language.equalsIgnoreCase("r"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的语言，只支持 python 和 r",
                    "status", "error"
            ));
        }

        // 生成任务ID
        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = generateTaskId(language);
        }

        // 启动异步执行
        CompletableFuture<ExecutionResult> future = codeExecutionService.executeAsync(
                taskId, code, language.toLowerCase()
        );

        // 返回任务信息
        return ResponseEntity.ok(Map.of(
                "success", true,
                "taskId", taskId,
                "status", "pending",
                "message", "代码执行任务已提交，请稍后查询结果",
                "pollUrl", "/api/code/result/" + taskId,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 同步执行代码
     */
    @PostMapping("/execute-sync")
    @ApiOperation("同步执行代码")
    public ResponseEntity<ExecutionResult> executeCodeSync(@RequestBody CodeRequest request) {
        String code = request.getCode();
        String language = request.getLanguage();
        String taskId = request.getTaskId();

        log.info("收到同步代码执行请求: language={}", language);

        // 参数验证
        if (code == null || code.trim().isEmpty()) {
            ExecutionResult result = new ExecutionResult(taskId, "error", "代码不能为空");
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();
            return ResponseEntity.badRequest().body(result);
        }

        if (language == null || (!language.equalsIgnoreCase("python") && !language.equalsIgnoreCase("r"))) {
            ExecutionResult result = new ExecutionResult(taskId, "error", "不支持的语言，只支持 python 和 r");
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();
            return ResponseEntity.badRequest().body(result);
        }

        // 生成任务ID
        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = generateTaskId(language);
        }

        // 同步执行
        ExecutionResult result = codeExecutionService.executeSync(taskId, code, language.toLowerCase());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取执行结果
     */
    @GetMapping("/result/{taskId}")
    @ApiOperation("获取执行结果")
    public ResponseEntity<ExecutionResult> getResult(@PathVariable String taskId) {
        log.info("获取执行结果: {}", taskId);
        ExecutionResult result = codeExecutionService.getExecutionResult(taskId);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 轮询接口（兼容老版本）
     */
    @GetMapping("/poll/{taskId}")
    @ApiOperation("轮询执行结果")
    public ResponseEntity<ExecutionResult> pollResult(@PathVariable String taskId) {
        return getResult(taskId);
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(String language) {
        return language.toLowerCase() + "_" +
                System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}