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
@TableName("analysis_task_file")
@ApiModel(value = "AnalysisTaskFile对象", description = "任务与输入文件关联表")
public class AnalysisTaskFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "关联ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "分析任务ID")
    @TableField("task_id")
    private Long taskId;

    @ApiModelProperty(value = "云端数据舱的文件ID")
    @TableField("file_id")
    private Long fileId;

    @ApiModelProperty(value = "文件角色(input/control)", example = "input")
    @TableField("file_role")
    private String fileRole;

    @ApiModelProperty(value = "关联创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}