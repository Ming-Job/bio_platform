package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.entity.Course;
import com.example.bio_platform.service.CourseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/courses")
@Api(tags = "管理员-课程管理接口")
public class AdminCourseController {

    @Autowired
    private CourseService courseService;


    @GetMapping
    @ApiOperation("管理员分页获取课程列表(包含所有状态)")
    public ResponseEntity<Map<String, Object>> getAdminCoursePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "6") Integer pageSize,
            @RequestParam(required = false) String searchKey) {

        Page<Course> page = courseService.getAdminCoursePage(pageNum, pageSize, searchKey);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", page
        ));
    }

    @PostMapping
    @ApiOperation("管理员新增课程")
    public ResponseEntity<Map<String, Object>> addCourse(@RequestBody Course course) {
        // 强制初始化状态和默认值
        course.setStatus("DRAFT"); // 默认存为草稿
        if (course.getSortOrder() == null) {
            course.setSortOrder(0);
        }

        boolean saved = courseService.save(course);
        return ResponseEntity.ok(Map.of("success", saved, "message", saved ? "新增成功" : "新增失败"));
    }

    @PutMapping("/{id}")
    @ApiOperation("管理员更新课程基本信息")
    public ResponseEntity<Map<String, Object>> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        course.setId(id);
        boolean updated = courseService.updateById(course);
        return ResponseEntity.ok(Map.of("success", updated, "message", updated ? "更新成功" : "更新失败"));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("管理员删除课程")
    public ResponseEntity<Map<String, Object>> deleteCourse(@PathVariable Long id) {
        boolean removed = courseService.removeById(id);
        return ResponseEntity.ok(Map.of("success", removed, "message", removed ? "删除成功" : "删除失败"));
    }

    @PatchMapping("/{id}/status")
    @ApiOperation("管理员快速上下架课程")
    public ResponseEntity<Map<String, Object>> updateCourseStatus(@PathVariable Long id, @RequestParam String status) {
        Course course = new Course();
        course.setId(id);
        course.setStatus(status);
        boolean updated = courseService.updateById(course);
        return ResponseEntity.ok(Map.of("success", updated, "message", "状态更新成功"));
    }
}