package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.dto.CourseChapterDTO;
import com.example.bio_platform.entity.CourseLesson;

import java.util.List;

public interface CourseLessonService extends IService<CourseLesson> {
    /**
     * 获取指定课程的层级大纲
     */
    List<CourseChapterDTO> getCourseOutline(Long courseId);

}
