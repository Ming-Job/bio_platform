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
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("project")
@ApiModel(value = "Project对象", description = "项目表")
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "项目ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "用户ID", required = true, example = "1")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "项目名称", required = true, example = "肝癌转录组分析")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "项目描述", example = "研究肝癌组织的转录组特征")
    @TableField("description")
    private String description;

    @ApiModelProperty(value = "是否归档", example = "false")
    @TableField("is_archived")
    private Boolean isArchived = false;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    // 以下为不映射到数据库的字段

    @ApiModelProperty(value = "项目创建者信息")
    @TableField(exist = false)
    private User user;

    @ApiModelProperty(value = "项目文件列表")
    @TableField(exist = false)
    private List<File> files;

    @ApiModelProperty(value = "项目文件数量", example = "10")
    @TableField(exist = false)
    private Integer fileCount = 0;

    @ApiModelProperty(value = "项目文件总大小(字节)", example = "1073741824")
    @TableField(exist = false)
    private Long totalFileSizeBytes = 0L;

    @ApiModelProperty(value = "格式化后的项目文件总大小", example = "1.0 GB")
    @TableField(exist = false)
    private String formattedTotalFileSize;

    @ApiModelProperty(value = "最近上传的文件")
    @TableField(exist = false)
    private File latestFile;

    @ApiModelProperty(value = "项目成员数量", example = "5")
    @TableField(exist = false)
    private Integer memberCount = 1; // 默认为1（项目创建者）

    @ApiModelProperty(value = "项目分析任务数量", example = "3")
    @TableField(exist = false)
    private Integer analysisTaskCount = 0;

}