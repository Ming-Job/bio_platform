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
@TableName("course_lesson")
@ApiModel(value = "CourseLesson对象", description = "课程课时章节表")
public class CourseLesson implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "归属课程ID", required = true)
    private Long courseId;

    @ApiModelProperty(value = "章节名称", example = "第一章 测序原理")
    private String chapterName;

    @ApiModelProperty(value = "课时标题", required = true)
    private String title;

    @ApiModelProperty(value = "内容类型：VIDEO, ARTICLE, PRACTICE")
    private String contentType;

    @ApiModelProperty(value = "图文内容或实操指导")
    private String content;

    @ApiModelProperty(value = "视频播放地址")
    private String videoUrl;

    @ApiModelProperty(value = "关联的分析工具ID")
    private Long relatedToolId;

    @ApiModelProperty(value = "是否支持免费试看：0-否，1-是")
    private Integer isFreePreview;

    @ApiModelProperty(value = "课时排序")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}