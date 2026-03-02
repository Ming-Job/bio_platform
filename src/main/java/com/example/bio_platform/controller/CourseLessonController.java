package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result;
import com.example.bio_platform.dto.CourseChapterDTO;
import com.example.bio_platform.entity.CourseLesson;
import com.example.bio_platform.service.CourseLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "课程课时管理")
@RestController
@RequestMapping("/api/lessons")
public class CourseLessonController {

    @Autowired
    private CourseLessonService lessonService;

    @ApiOperation("获取课程大纲（章节嵌套课时）")
    @GetMapping("/outline/{courseId}")
    public Result<List<CourseChapterDTO>> getOutline(@PathVariable Long courseId) {
        return Result.success(lessonService.getCourseOutline(courseId));
    }

    @ApiOperation("获取课时详细内容（用于播放页/文章页）")
    @GetMapping("/{id}")
    public Result<CourseLesson> getLessonDetail(@PathVariable Long id) {
        return Result.success(lessonService.getById(id));
    }

    @ApiOperation("新增课时")
    @PostMapping
    public Result<Boolean> save(@RequestBody CourseLesson lesson) {
        return Result.success(lessonService.save(lesson));
    }

    @ApiOperation("删除课时")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(lessonService.removeById(id));
    }
}
