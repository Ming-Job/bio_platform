package com.example.bio_platform.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl {

    // application.properties 注入配置
    @Value("${ai.deepseek.api-url}")
    private String apiUrl;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.deepseek.model}")
    private String modelName;

    // 流式处理方法
// 🌟 1. 方法签名增加 courseContext 参数（前端传过来的当前章节内容/标题）
    public void streamBioAi(String courseContext, List<Map<String, String>> historyMessages, ResponseBodyEmitter emitter) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            // 1. 构建动态提示词
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");

            // 🌟 2. 核心大招：把上下文动态拼接到 Prompt 中，真正实现“上下文感知”
            String dynamicPrompt = "你是一个专属的生物信息学AI助教。" +
                    "学生目前正在学习的课程章节上下文是：【" + courseContext + "】。" +
                    "请务必结合该章节的背景知识，用专业、严谨且易懂的中文解答学生的提问。" +
                    "遇到代码请给出示例。保持回答精炼。如果学生的问题偏离了该课程主题，请委婉地引导回课程内容。";

            systemMsg.put("content", dynamicPrompt);
            messages.add(systemMsg);

            // 加上用户的历史提问记录
            messages.addAll(historyMessages);

            // 2. 构建 JSON (stream = true)
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("stream", true);
            body.put("messages", messages);
            // 🌟 可选：把温度调到 0.4 左右，让助教的语气既严谨又有一定的讲课亲和力
            body.put("temperature", 0.4);

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

            // 3. 读取流并实时发送给前端（下方代码保持你原来的逻辑完全不变）
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                if (responseLine.startsWith("data: ")) {
                    String data = responseLine.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        emitter.complete();
                        break;
                    }
                    try {
                        JsonNode node = mapper.readTree(data);
                        JsonNode choices = node.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String content = delta.get("content").asText();
                                System.out.print(content); // 后端继续打印监控
                                emitter.send(content);     // 核心：推给前端
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            try {
                emitter.send("\n[网络连接异常: " + e.getMessage() + "]");
                emitter.completeWithError(e);
            } catch (Exception ignored) {
            }
        }
    }

    // ==========================================
    // 🌟 终极增强版：智能文件探针大模型推理
    // ==========================================
    public List<String> generateSmartPrompts(String fileName, String fileHeader) {
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");

            systemMsg.put("content", "你是一个顶级的生物信息学数据分析专家。用户上传了一个数据文件，请根据文件名的后缀和前5行内容，精确推断其数据类型（例如：基因表达矩阵、测序序列、分子3D结构、变异位点等），并生成3个专业、具体的代码分析需求提示词（要求生成使用 Python 分析或绘图的提示词）。\n" +
                    "🎯 核心要求：\n" +
                    "1. 必须根据【真实的文件特征】量身定制需求，切勿生搬硬套！\n" +
                    "2. 你生成的这3个需求提示词【必须完全使用中文】描述！并在每个提示词的末尾固定加上“ (全英文)”字样。\n" +
                    "⚠️ 绝对红线：你必须且只能返回一个合法的 JSON 字符串数组，绝对不允许包含任何解释、前缀或 Markdown 标记！\n" +
                    "💡 格式参考（你需要根据实际文件的内容替换业务逻辑）：\n" +
                    "[\"读取表达矩阵文件，计算不同组间的差异表达基因并绘制火山图 (全英文)\", \"解析PDB文件提取C-alpha原子坐标，可视化分子的三维骨架 (全英文)\", \"统计测序文件中的序列长度分布，绘制密度直方图 (全英文)\"]");
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "文件名：" + fileName + "\n\n前5行探针数据如下：\n" + fileHeader);
            messages.add(userMsg);

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("messages", messages);
            body.put("temperature", 0.4);

            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = mapper.writeValueAsString(body).getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                JsonNode rootNode = mapper.readTree(response.toString());
                String aiReply = rootNode.path("choices").get(0).path("message").path("content").asText();

                // 🌟 强力监控：看看大模型到底回了什么鬼东西
                System.out.println("【大模型原始回复】: " + aiReply);

                // 🌟 工业级暴力截取：无视大模型的任何废话，强行抠出 [ ] 里面的内容
                int startIndex = aiReply.indexOf('[');
                int endIndex = aiReply.lastIndexOf(']');

                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    String cleanJson = aiReply.substring(startIndex, endIndex + 1);
                    return mapper.readValue(cleanJson, new TypeReference<List<String>>(){});
                } else {
                    System.err.println("【解析失败】AI 没有返回合法的 JSON 数组结构！");
                }
            } else {
                // 🌟 核心排错补丁：把 HTTP 的报错信息大声喊出来！
                BufferedReader errorBr = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorBr.readLine()) != null) {
                    errorResponse.append(line);
                }
                System.err.println("【DeepSeek API 请求彻底失败】状态码: " + responseCode + ", 错误详情: " + errorResponse.toString());
            }
        } catch (Exception e) {
            System.err.println("【探针执行发生内部异常】: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>(); // 只有当上方全部失败时，才会返回空数组去触发保底容错
    }

    // ==========================================
    // 🌟 基于真实课程库的语义级 AI 推荐引擎
    // ==========================================

    public Map<String, Object> recommendCoursesFromCatalog(String userQuery, List<Map<String, Object>> simpleCourseCatalog) {
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("ids", new ArrayList<Long>());
        finalResult.put("reason", "AI 未能生成推荐理由或解析失败");

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);

            ObjectMapper mapper = new ObjectMapper();
            String catalogJson = mapper.writeValueAsString(simpleCourseCatalog);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");

            // 🌟 核心修改：改变 Prompt，逼迫 AI 输出结构化的 JSON 对象
            systemMsg.put("content", "你是一个极其专业的生物信息学课程推荐助手。我会给你一份我平台真实的【课程目录】(包含id, title, desc)。\n" +
                    "请深刻理解用户的学习需求，并从目录中挑选出最匹配的 1 到 3 门课程，同时给出专业的推荐理由。\n" +
                    "⚠️ 绝对红线：\n" +
                    "1. 绝不允许推荐【课程目录】中不存在的课程！\n" +
                    "2. 你必须且只能返回一个合法的 JSON 对象，绝对不允许包含任何解释、前缀或 Markdown 标记！\n" +
                    "正确输出格式示例：\n" +
                    "{\"ids\": [12, 45], \"reason\": \"用户希望学习基因组变异分析，课程12涵盖了GATK变异检测流程，课程45详细讲解了GWAS全基因组关联分析，完全契合用户需求。\"}");
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "【课程目录】：" + catalogJson + "\n\n【用户学习需求】：" + userQuery);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", modelName);
            body.put("stream", false);
            body.put("messages", messages);
            body.put("temperature", 0.2); // 稍微给点温度，让它写的理由更自然流畅一点

            try(OutputStream os = conn.getOutputStream()) {
                byte[] input = mapper.writeValueAsString(body).getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                JsonNode rootNode = mapper.readTree(response.toString());
                String aiReply = rootNode.path("choices").get(0).path("message").path("content").asText();

                // 暴力清洗，防止大模型抽风带上 ```json
                int startIndex = aiReply.indexOf('{');
                int endIndex = aiReply.lastIndexOf('}');

                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    String cleanJson = aiReply.substring(startIndex, endIndex + 1);
                    JsonNode replyNode = mapper.readTree(cleanJson);

                    // 🌟 提取 IDs 数组和 Reason 字符串
                    if (replyNode.has("ids")) {
                        List<Long> ids = mapper.convertValue(replyNode.get("ids"), new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){});
                        finalResult.put("ids", ids);
                    }
                    if (replyNode.has("reason")) {
                        finalResult.put("reason", replyNode.get("reason").asText());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("AI 课程推荐接口解析异常：" + e.getMessage());
        }
        return finalResult;
    }
}