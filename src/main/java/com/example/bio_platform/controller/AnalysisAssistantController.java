package com.example.bio_platform.controller; // 请根据你的实际包名修改

import com.example.bio_platform.service.AnalysisService; // 导入你的服务
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@CrossOrigin(origins = "*") // 允许所有跨域请求，生产环境应具体指定前端地址
public class AnalysisAssistantController {

    @Autowired
    private AnalysisService analysisService; // 注入我们刚修改的 Service

    @PostMapping("/assist")
    public ResponseEntity<Map<String, Object>> assist(@RequestBody AnalysisRequest request) {
        // 创建标准化的响应体
        Map<String, Object> responseBody = new HashMap<>();

        try {
            // 1. 参数校验
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                responseBody.put("success", false);
                responseBody.put("message", "请求参数错误：问题内容不能为空");
                return ResponseEntity.badRequest().body(responseBody);
            }

            // 2. 调用服务层，获取生成的代码 (核心调用)
            String generatedCode = analysisService.generateAnalysisCode(request.getQuestion());

            // 3. 构建成功响应
            responseBody.put("success", true);
            responseBody.put("code", generatedCode);
            responseBody.put("message", "分析完成");
            responseBody.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(responseBody);

        } catch (SecurityException e) {
            // 处理服务层抛出的安全异常（例如代码包含危险命令）
            responseBody.put("success", false);
            responseBody.put("message", "安全校验失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseBody);
        } catch (IllegalArgumentException e) {
            // 处理参数类异常
            responseBody.put("success", false);
            responseBody.put("message", "请求参数无效: " + e.getMessage());
            return ResponseEntity.badRequest().body(responseBody);
        } catch (Exception e) {
            // 处理所有其他异常（包括调用SiliconFlow API失败、网络异常等）
            e.printStackTrace(); // 服务端打印详细日志，便于调试
            responseBody.put("success", false);
            responseBody.put("message", "分析请求失败: " + e.getMessage());
            // 可以根据e的具体类型返回更精确的状态码，这里统一返回500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
        }
    }

    // 内部静态类，用于接收JSON请求体
    public static class AnalysisRequest {
        private String question;

        // Jackson反序列化需要的无参构造器
        public AnalysisRequest() {}

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }
}