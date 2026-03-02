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

/**
 * 课程主表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("course")
@ApiModel(value = "Course对象", description = "课程主表")
public class Course implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "课程主键ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "课程标题", required = true, example = "RNA-Seq数据分析实战")
    @TableField("title")
    private String title;

    @ApiModelProperty(value = "课程详细介绍")
    @TableField("description")
    private String description;

    @ApiModelProperty(value = "课程封面图URL", example = "/course/cover1.jpg")
    @TableField("cover_image")
    private String coverImage;

    @ApiModelProperty(value = "讲师ID(关联user表)", required = true, example = "2")
    @TableField("instructor_id")
    private Long instructorId;

    @ApiModelProperty(value = "难度级别：BEGINNER, INTERMEDIATE, ADVANCED", example = "BEGINNER")
    @TableField("difficulty")
    private String difficulty;

    @ApiModelProperty(value = "课程状态：DRAFT, PUBLISHED, OFFLINE", example = "PUBLISHED")
    @TableField("status")
    private String status;

    @ApiModelProperty(value = "排序权重", example = "0")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}