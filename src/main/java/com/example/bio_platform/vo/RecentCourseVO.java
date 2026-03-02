package com.example.bio_platform.vo;

import lombok.Data;

@Data
public class RecentCourseVO {
    private Long courseId;
    private String title;
    private String coverImage;
    private Integer progress; // 学习进度 0-100
}