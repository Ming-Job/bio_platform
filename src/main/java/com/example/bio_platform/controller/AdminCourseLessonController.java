package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bio_platform.common.Result;
import com.example.bio_platform.entity.CourseLesson;
import com.example.bio_platform.service.CourseLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Api(tags = "管理员-课时大纲管理接口")
@RestController
@RequestMapping("/api/admin/course-lessons")
public class AdminCourseLessonController {

    @Resource
    private CourseLessonService courseLessonService;

    @ApiOperation("获取指定课程的所有课时列表 (按排序权重升序)")
    @GetMapping("/list")
    public Result<List<CourseLesson>> getLessonList(@RequestParam Long courseId) {
        if (courseId == null) {
            return Result.error("课程ID不能为空");
        }

        // 使用 MyBatis-Plus 自带的条件构造器，极其方便
        LambdaQueryWrapper<CourseLesson> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseLesson::getCourseId, courseId)
                .orderByAsc(CourseLesson::getSortOrder) // 按照权重升序排，小的在前
                .orderByAsc(CourseLesson::getCreatedAt); // 权重一样就按时间排

        List<CourseLesson> list = courseLessonService.list(wrapper);
        return Result.success(list);
    }

    @ApiOperation("新增课时")
    @PostMapping
    public Result<String> addLesson(@RequestBody CourseLesson courseLesson) {
        if (courseLesson.getCourseId() == null) {
            return Result.error("归属课程不能为空");
        }
        boolean success = courseLessonService.save(courseLesson);
        return success ? Result.success("新增课时成功", null) : Result.error("新增失败");
    }

    @ApiOperation("修改课时信息")
    @PutMapping("/{id}")
    public Result<String> updateLesson(@PathVariable Long id, @RequestBody CourseLesson courseLesson) {
        courseLesson.setId(id); // 确保 ID 一致
        boolean success = courseLessonService.updateById(courseLesson);
        return success ? Result.success("修改课时成功", null) : Result.error("修改失败，该课时可能不存在");
    }

    @ApiOperation("删除课时")
    @DeleteMapping("/{id}")
    public Result<String> deleteLesson(@PathVariable Long id) {
        boolean success = courseLessonService.removeById(id);
        return success ? Result.success("删除课时成功", null) : Result.error("删除失败");
    }
}