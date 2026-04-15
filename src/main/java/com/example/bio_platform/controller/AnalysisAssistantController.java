package com.example.bio_platform.controller;

import com.example.bio_platform.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*")
public class AnalysisAssistantController {

    @Autowired
    private AnalysisService analysisService;

    @PostMapping("/assist")
    public ResponseEntity<Map<String, Object>> assist(@RequestBody AnalysisRequest request) {
        Map<String, Object> responseBody = new HashMap<>();

        try {
            // 1. 参数校验
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                responseBody.put("success", false);
                responseBody.put("message", "问题内容不能为空");
                return ResponseEntity.badRequest().body(responseBody);
            }

            // 2. 🌟 调用服务层：同时传入问题、文件名和新加的案例 ID
            String generatedCode = analysisService.generateAnalysisCode(
                    request.getQuestion(),
                    request.getFileName()
            );

            responseBody.put("success", true);
            responseBody.put("code", generatedCode);
            responseBody.put("message", "分析完成");
            responseBody.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            e.printStackTrace();
            responseBody.put("success", false);
            responseBody.put("message", "分析请求失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
        }
    }

    // 🌟 内部类：增加 caseId 字段接收前端参数
    public static class AnalysisRequest {
        private String question;
        private String fileName;
        private Long caseId; // 🌟 新增字段：用于接收案例大厅传过来的 ID

        public AnalysisRequest() {}

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public Long getCaseId() { return caseId; }
        public void setCaseId(Long caseId) { this.caseId = caseId; }
    }
}