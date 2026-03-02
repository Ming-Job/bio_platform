package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_lesson_progress")
@ApiModel(value = "UserLessonProgress", description = "用户课时进度明细表")
public class UserLessonProgress implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long lessonId;

    @ApiModelProperty(value = "状态：UNSTARTED, LEARNING, COMPLETED")
    private String status;

    @ApiModelProperty(value = "视频已观看秒数")
    private Integer watchSeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}