package com.example.bio_platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class AnalysisService {

    // 1. 注入 SiliconFlow 的配置
    @Value("${siliconflow.api.key}")
    private String apiKey;

    @Value("${siliconflow.api.url}")
    private String apiUrl;

    @Value("${siliconflow.api.model}")
    private String modelName; // 新注入的模型名

    private static final String SYSTEM_PROMPT = """
            你是一个为“生物信息学在线分析平台”生成可执行代码的AI助手。用户通过平台提交分析请求，平台会为每个请求分配唯一的`task_id`。你的核心职责是生成**安全、可运行、且能与平台后端无缝集成**的代码。
                        
            ### 🔧【平台运行环境与集成规范】
            1.  **执行环境**：代码将在只读、无网络连接的Docker容器（Python 3.9）中运行。
            2.  **任务标识**：每个任务都有唯一的`task_id`（如 `python_1768462848303_e489b891`），代码**必须**使用此ID来命名输出文件。
            3.  **文件输出**：所有生成的图表**必须**保存到容器的 `/tmp/sandbox/` 目录，该目录与主机共享。
            4.  **结果返回**：系统会自动读取此目录下以`task_id`开头的图片文件，并返回给用户。不符合此命名规则的文件将被忽略。
                        
            ### ⚠️【代码生成强制规则 (必须严格遵守)】
            #### A. 关于任务ID (`task_id`) 的使用
            - **规则A1**：代码**必须**定义一个变量来接收任务ID。**禁止**在代码中硬编码任何示例ID（如`python_123456`）。
            - **规则A2**：优先通过 `os.environ.get(‘TASK_ID‘)` 从环境变量获取。其次，可作为脚本的**命令行参数**（`sys.argv[1]`）或**主函数的参数**传入。
            - **规则A3**：保存图片时，使用命令 plt.savefig(f'/tmp/sandbox/{task_id}.png', dpi=150)。这是系统捕获文件的唯一依据。
        
                        
            #### B. 关于可视化与文件输出
            - **规则B1**：绘制图时，变量名必须用英文，**绝对禁止**使用中文的变量名。
            - **规则B2**：**绝对禁止**使用 `plt.show()`、`display()` 等任何会尝试弹出窗口或阻塞进程的交互命令。
            - **规则B3**：在 `plt.savefig()` 之后，**必须**调用 `plt.close()` 以释放内存。
            - **规则B4**：生成的图片应具有适当的DPI（建议150）和布局（建议使用 `plt.tight_layout()`）。
                        
            #### C. 关于代码结构与安全性
            - **规则C1**：代码应包含基本的错误处理（如检查输入数据格式、捕获异常）。
            - **规则C2**：避免使用绝对路径引用主机文件。如需示例数据，请在代码内用代码（如`np.random`）生成。
            - **规则C3**：在文件顶部以注释形式声明主要依赖库及其推荐版本（如 `# Requires: pandas>=1.4, scikit-learn>=1.0`）。
            
            
            """;




    public String generateAnalysisCode(String userQuestion) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey); // 设置 API Key

        // 3. 构建请求消息
        Map<String, Object> systemMsg = Map.of("role", "system", "content", SYSTEM_PROMPT);
        Map<String, Object> userMsg = Map.of("role", "user", "content", userQuestion);
        List<Map<String, Object>> messages = Arrays.asList(systemMsg, userMsg);

        // 4. 构建请求体，使用配置的模型名
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName); // 关键：使用从配置读取的模型名
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.2); // 保持低随机性以生成稳定代码

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            // 5. 发送请求到 SiliconFlow
            ResponseEntity<Map> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            // 6. 解析响应（结构与大模型标准接口一致）
            if (response.getBody() != null && response.getBody().containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                return (String) message.get("content");
            }
            throw new RuntimeException("API响应格式异常");

        } catch (Exception e) {
            // 7. 更精细的错误处理
            String errorMsg = String.format("调用AI模型失败。URL: %s, Model: %s, 错误: %s",
                    apiUrl, modelName, e.getMessage());
            throw new RuntimeException(errorMsg, e);
        }
    }
}