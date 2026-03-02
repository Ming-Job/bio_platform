package com.example.bio_platform.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 课程列表展示视图对象
 */
@Data
@Accessors(chain = true)
@ApiModel(value = "CourseVO对象", description = "前端课程列表展示数据")
public class CourseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "课程ID")
    private Long id;

    @ApiModelProperty(value = "课程标题")
    private String title;

    @ApiModelProperty(value = "课程介绍")
    private String description;

    @ApiModelProperty(value = "难度级别")
    private String difficulty;

    @ApiModelProperty(value = "课程封面")
    private String coverImage;

    @ApiModelProperty(value = "占位渐变色(封面为空时前端使用)")
    private String bgColor;

    @ApiModelProperty(value = "课程评分", example = "4.8")
    private Double rating;

    @ApiModelProperty(value = "预计学习时长(小时)", example = "12")
    private Integer estimatedHours;

    @ApiModelProperty(value = "报名/学习人数", example = "1250")
    private Integer enrolledCount;

    @ApiModelProperty(value = "包含课时数", example = "24")
    private Integer lessonCount;

    @ApiModelProperty(value = "讲师姓名")
    private String instructorName;

    @ApiModelProperty(value = "讲师头像")
    private String instructorAvatar;
}