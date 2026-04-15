package com.example.bio_platform.service;



import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;

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



@Service

public class AnalysisService {



    @Value("${ai.deepseek.api-url}")

    private String apiUrl;



    @Value("${ai.deepseek.api-key}")

    private String apiKey;



    @Value("${ai.deepseek.model}")

    private String modelName;



    private static final String SYSTEM_PROMPT = """
            你是一个为“生物信息学云原生分析平台”生成生产级代码的资深生信工程师。
            你的代码将在严格的 Linux Docker 沙箱中静默执行，必须 100% 严格遵守以下指令：

            ### 1. ⚡【性能指令：严禁使用 apply】
            - **禁止逻辑**：严禁使用 `df.apply(..., axis=1)` 或 `for` 循环进行统计检验！这会导致大数据量下处理极慢，且极易因数据类型退化为 object 引发崩溃。
            - **必须做法**：必须使用 **True Vectorization (矩阵运算)**。先将组别切片为独立的 DataFrame/Numpy 矩阵，然后一次性传入 `stats.ttest_ind(matrix1, matrix2, axis=1)`。

            ### 2. 🛡️【类型安全与隔离】
            - **数值强制转换**：在进行任何数学运算前，必须先执行 `data_matrix = df.iloc[:, 1:].apply(pd.to_numeric, errors='coerce').astype(float)`。
            - **计算隔离**：确保传给统计函数的参数是纯粹的 float64 矩阵，严禁混入 GeneID 等字符串列。
            - **维度对齐**：进行样本维度分析（如 PCA）时，必须确保数值矩阵的样本数与分组标签长度完全一致（注意排除 ID 列）。
            - **零值处理**：必须对数值矩阵执行 `.fillna(1e-5)`，防止 NaN 或 Inf 破坏后续 Log 运算。

            ### 3. 📁【IO 与路径法则】
            - **绝对路径**：数据读取路径强制锁定在 `/tmp/sandbox/{filename}`。不准瞎猜路径。
            - **标识符获取**：代码开头必须通过 `import os; task_id = os.environ.get('TASK_ID', 'default_task')` 获取任务 ID。严禁硬编码伪造 ID。

            ### 4. 📊【可视化规范：全英文与标注】
            - **全英文强制**：Linux 沙箱无中文字体。Title, Labels, Legend, Annotations 严禁出现中文字符！
            - **智能标注**：必须筛选出 P 值最小（或最显著）的前 5 个基因，并使用 `plt.text` 在图中标注其 ID。
            - **静默出图**：严禁使用 `plt.show()`。
            - **保存规范**：必须使用且只能使用 `plt.savefig(f'/tmp/sandbox/{task_id}.png', dpi=300, bbox_inches='tight')`。
            - **内存释放**：保存完毕后，必须紧跟 `plt.close()` 释放内存。

            ### 5. 🛠️【输出协议：纯净脚本】
            - **无围栏输出**：只输出纯净 Python 源码，严禁输出 Markdown 格式围栏（如不要输出 ```python 或 ```）。
            - **禁止废话**：不要输出任何前置解释文字或后续代码建议。
            """;



    /**

     * @param userQuestion 用户需求

     * @param attachedFileName 挂载的文件名（可选）

     */

    public String generateAnalysisCode(String userQuestion, String attachedFileName) {

        try {

            URL url = new URL(apiUrl);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");

            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            conn.setDoOutput(true);



// 1. 动态拼接文件上下文

            String context = "";

            if (attachedFileName != null && !attachedFileName.isEmpty()) {

                context = String.format("【重要上下文】：用户已挂载数据文件，沙箱内路径为：/tmp/sandbox/%s。请在代码中直接读取此路径进行分析。\n\n", attachedFileName);

            }

            String finalUserMsg = context + "用户需求：" + userQuestion;



// 2. 构建消息体

            List<Map<String, String>> messages = new ArrayList<>();

            messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

            messages.add(Map.of("role", "user", "content", finalUserMsg));



            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> body = new HashMap<>();

            body.put("model", modelName);

            body.put("stream", false);

            body.put("messages", messages);

            body.put("temperature", 0.1);



            try (OutputStream os = conn.getOutputStream()) {

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

                return rootNode.path("choices").get(0).path("message").path("content").asText();

            } else {

                throw new RuntimeException("API 响应异常，状态码: " + conn.getResponseCode());

            }

        } catch (Exception e) {

            throw new RuntimeException("AI 代码生成失败: " + e.getMessage());

        }

    }



}