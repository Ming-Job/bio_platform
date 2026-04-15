package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("analysis_task")
@ApiModel(value = "AnalysisTask对象", description = "云端分析任务表")
public class AnalysisTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "任务ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "提交任务的用户ID")
    @TableField("user_id")
    private Long userId;

    // 🌟 核心新增：映射数据库中的项目 ID
    @ApiModelProperty(value = "关联的科研项目ID")
    @TableField("project_id")
    private Long projectId;

    @ApiModelProperty(value = "关联的流程模板ID")
    @TableField("pipeline_id")
    private Long pipelineId;

    @ApiModelProperty(value = "任务名称", example = "肝癌样本分析-001")
    @TableField("task_name")
    private String taskName;

    @ApiModelProperty(value = "任务状态", example = "PENDING, RUNNING, COMPLETED, FAILED")
    @TableField("status")
    private String status;

    @ApiModelProperty(value = "用户设定的参数(JSON格式)")
    @TableField("parameters")
    private String parameters;

    @ApiModelProperty(value = "当前进度(0-100)", example = "45")
    @TableField("progress")
    private Integer progress;

    @ApiModelProperty(value = "进度提示信息", example = "正在进行FastQC质控...")
    @TableField("progress_msg")
    private String progressMsg;

    @ApiModelProperty(value = "错误信息")
    @TableField("error_message")
    private String errorMessage;

    @ApiModelProperty(value = "实际开始执行时间")
    @TableField("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;

    @ApiModelProperty(value = "任务完成时间")
    @TableField("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime completedAt;

    @ApiModelProperty(value = "任务提交时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;


    // 🌟 新增：用于向前端展示归属课题名称，exist = false 表示数据库没这个列
    @TableField(exist = false)
    private String projectName;
}