package com.example.bio_platform.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class AiServiceImpl {

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    // ⚠️ 这里一定要填你申请到的真实 API KEY ！！！
    private static final String API_KEY = "sk-efd8f6768eda40dc89a8877cd456ec07";

    // 🌟 这是一个全新的流式处理方法
// 🌟 注意这里第二个参数变成了 ResponseBodyEmitter
    public void streamBioAi(List<Map<String, String>> historyMessages, org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter emitter) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            // 1. 构建提示词
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个资深的生物信息学专家。请用专业、严谨且易懂的中文解答提问，遇到代码请给出示例。保持回答精炼。");
            messages.add(systemMsg);
            messages.addAll(historyMessages);

            // 2. 构建 JSON (stream = true)
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = new HashMap<>();
            body.put("model", "deepseek-chat");
            body.put("stream", true);
            body.put("messages", messages);

            String jsonInputString = mapper.writeValueAsString(body);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() != 200) {
                emitter.send("\n[AI 接口响应失败，状态码: " + conn.getResponseCode() + "]");
                emitter.complete();
                return;
            }

            // 3. 读取流并实时发送给前端
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                if (responseLine.startsWith("data: ")) {
                    String data = responseLine.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        emitter.complete(); // 🌟 告诉前端数据发完了，关闭水管
                        break;
                    }
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(data);
                        com.fasterxml.jackson.databind.JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();

                                System.out.print(content); // 后端继续打印监控

                                // 🌟 核心：直接把字推送给前端！
                                emitter.send(content);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            try {
                emitter.send("\n[网络连接异常: " + e.getMessage() + "]");
                emitter.completeWithError(e); // 发生异常时妥善关闭
            } catch (Exception ignored) {
            }
        }
    }


    // 🌟 新增方法：让 AI 从大白话中提取 JSON 关键词数组
    public List<String> extractKeywords(String userQuery) {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            // 1. 🌟 极其严苛的提示词魔法
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个专业的生物信息学选课系统后台。请从用户的文字中提取最核心的3个生信技能关键词（如Linux, R, Python, RNA-seq, 单细胞等）。" +
                    "⚠️ 警告：你必须且只能返回一个合法的 JSON 字符串数组，绝对不允许包含任何解释、前缀或 Markdown 标记！" +
                    "正确示例：[\"RNA-seq\", \"差异表达\", \"DESeq2\"]");
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userQuery);
            messages.add(userMsg);

            // 2. 发送请求 (注意这里 stream 为 false，我们要一次性拿到完整结果)
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = new HashMap<>();
            body.put("model", "deepseek-chat");
            body.put("stream", false);
            body.put("messages", messages);
            body.put("temperature", 0.1); // 🌟 温度调到极低，让它绝对理智，不乱编废话

            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = mapper.writeValueAsString(body).getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // 3. 读取 AI 返回的结果
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 解析 JSON
                JsonNode rootNode = mapper.readTree(response.toString());
                String aiReply = rootNode.path("choices").get(0).path("message").path("content").asText();

                // 4. 安全处理：万一 AI 还是带了 ```json 这种标记，手动帮它剔除
                aiReply = aiReply.replace("```json", "").replace("```", "").trim();

                // 5. 把 JSON 字符串数组转成 Java 的 List<String>
                return mapper.readValue(aiReply, new TypeReference<List<String>>(){});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 如果出错，返回一个空列表兜底
        return new ArrayList<>();
    }
}