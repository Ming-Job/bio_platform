package com.example.bio_platform.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class MyCourseVO {
    private Long courseId;
    private String title;
    private String coverImage;
    private Integer progress;

    // 🌟 给前端返回格式化好的上次学习时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLearnedAt;
}