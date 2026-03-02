package com.example.bio_platform.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CourseLessonVO {
    private Long id;
    private String title;
    private String contentType;
    private Integer isFreePreview;
    private Integer sortOrder;
    private Long relatedToolId;

    private String videoUrl;  // 用于播放视频
    private String content;   // 用于渲染图文和实操指导
}