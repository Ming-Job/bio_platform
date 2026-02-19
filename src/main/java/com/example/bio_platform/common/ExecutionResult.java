package com.example.bio_platform.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码执行结果实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)  // 添加这行，忽略未知字段
public class ExecutionResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 执行状态
     * pending - 等待中
     * running - 执行中
     * completed - 已完成
     * error - 执行错误
     * timeout - 执行超时
     * expired - 已过期
     */
    // 明确指定JSON字段名  !!!!
    @JsonProperty("status")   // 解决问题：导致redis存储的数据（status=completed）与反序列化的数据（status=error）不一致的问题
    private String status;

    /**
     * 状态消息
     */
    private String message;

    /**
     * 控制台输出
     */
    private String output;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 生成的图片（base64编码）
     */
    private List<String> images;

    /**
     * 执行开始时间
     */
    private Long startTime;

    /**
     * 执行结束时间
     */
    private Long endTime;

    /**
     * 编程语言
     */
    private String language;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 是否包含图表输出
     */
    private Boolean hasVisualization;

    /**
     * 执行环境信息
     */
    private String environment;

    /**
     * 内存使用量（KB）
     */
    private Long memoryUsage;

    /**
     * CPU使用率（百分比）
     */
    private Double cpuUsage;

    // 构造函数重载
    public ExecutionResult(String taskId, String status) {
        this.taskId = taskId;
        this.status = status;
    }

    public ExecutionResult(String taskId, String status, String message) {
        this.taskId = taskId;
        this.status = status;
        this.message = message;
    }

    public ExecutionResult(String taskId, String status, String output, String error) {
        this.taskId = taskId;
        this.status = status;
        this.output = output;
        this.error = error;
    }

    // 工具方法
    @JsonIgnoreProperties(ignoreUnknown = true)  // 为布尔方法添加忽略
    public boolean isCompleted() {
        return "completed".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public boolean isError() {
        return "error".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public boolean isRunning() {
        return "running".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public boolean hasError() {
        return error != null && !error.trim().isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public boolean hasOutput() {
        return output != null && !output.trim().isEmpty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }

    /**
     * 计算执行时间
     */
    public void calculateExecutionTime() {
        if (startTime != null && endTime != null) {
            this.executionTime = endTime - startTime;
        }
    }

    /**
     * 设置为成功状态
     */
    public void setSuccess(String output) {
        this.status = "completed";
        this.output = output;
        this.endTime = System.currentTimeMillis();
        calculateExecutionTime();
    }

    /**
     * 设置为错误状态
     */
    public void setError(String errorMessage) {
        this.status = "error";
        this.error = errorMessage;
        this.endTime = System.currentTimeMillis();
        calculateExecutionTime();
    }

    /**
     * 设置为超时状态
     */
    public void setTimeout() {
        this.status = "timeout";
        this.message = "执行超时";
        this.endTime = System.currentTimeMillis();
        calculateExecutionTime();
    }

    /**
     * 添加图片到结果
     */
    public void addImage(String base64Image) {
        if (images == null) {
            images = new ArrayList<>();
        }
        images.add(base64Image);
        this.hasVisualization = true;
    }

    /**
     * 获取第一张图片（如果有）
     */
    public String getFirstImage() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return null;
    }

    /**
     * 获取所有图片
     */
    public List<String> getImages() {
        if (images == null) {
            images = new ArrayList<>();
        }
        return images;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasVisualization() {
        return hasVisualization;
    }
}