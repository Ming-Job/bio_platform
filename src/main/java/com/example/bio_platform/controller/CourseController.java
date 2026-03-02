package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.common.Result; // 请替换为你实际的 Result 路径
import com.example.bio_platform.entity.UserCourseEnrollment;
import com.example.bio_platform.service.CourseService;
import com.example.bio_platform.service.UserCourseEnrollmentService;
import com.example.bio_platform.vo.CourseDetailVO;
import com.example.bio_platform.vo.CourseVO;
import com.example.bio_platform.vo.MyCourseVO;
import com.example.bio_platform.vo.RecentCourseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "课程中心接口")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;
    @Autowired
    private UserCourseEnrollmentService enrollmentService;

    @ApiOperation(value = "分页查询前端课程列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "当前页码", defaultValue = "1", paramType = "query", dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", defaultValue = "6", paramType = "query", dataType = "Integer"),
            @ApiImplicitParam(name = "title", value = "课程名称(模糊)", paramType = "query", dataType = "String"),
            @ApiImplicitParam(name = "difficulty", value = "难度(BEGINNER/INTERMEDIATE/ADVANCED)", paramType = "query", dataType = "String")
    })
    @GetMapping("/page")
    public Result<Page<CourseVO>> getCoursePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "6") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String difficulty) {

        Page<CourseVO> pageData = courseService.getCoursePage(pageNum, pageSize, title, difficulty);
        return Result.success(pageData); // 根据你项目的统一返回类进行修改
    }

    @ApiOperation(value = "获取单门课程详情及大纲")
    @GetMapping("/{id}")
    public Result<CourseDetailVO> getCourseDetail(@PathVariable Long id) {
        CourseDetailVO detail = courseService.getCourseDetail(id);
        if (detail == null) {
            return Result.error("该课程不存在或已下架");
        }
        return Result.success(detail);
    }

    @ApiOperation(value = "获取用户对某门课的报名状态")
    @GetMapping("/{courseId}/enrollment-status")
    public Result<UserCourseEnrollment> getEnrollmentStatus(@PathVariable Long courseId,
                                                            @RequestParam Long userId) {
        // 注意：实际企业开发中，userId 通常从 Token 中解析（如 request.getHeader("Authorization")）
        // 毕设阶段为了方便联调，可以先作为参数传过来
        UserCourseEnrollment record = enrollmentService.getEnrollmentRecord(userId, courseId);
        return Result.success(record); // 如果未报名会返回 null，前端根据 null 判断
    }

    @ApiOperation(value = "用户加入课程学习")
    @PostMapping("/{courseId}/enroll")
    public Result<UserCourseEnrollment> enrollCourse(@PathVariable Long courseId,
                                                     @RequestParam Long userId) {
        UserCourseEnrollment record = enrollmentService.enrollCourse(userId, courseId);
        return Result.success(record);
    }

    @ApiOperation(value = "获取用户最近学习的课程")
    @GetMapping("/recent")
    public Result<List<RecentCourseVO>> getRecentCourses(@RequestParam Long userId) {
        // 默认取最近学的 3 门课展示在顶部
        List<RecentCourseVO> list = courseService.getRecentLearnedCourses(userId, 3);
        return Result.success(list);
    }

    @ApiOperation(value = "获取当前用户的所有学习课程")
    @GetMapping("/user/my")
    public Result<List<MyCourseVO>> getMyCourses(@RequestParam Long userId) {
        List<MyCourseVO> list = courseService.getMyCourses(userId);
        return Result.success(list);
    }
}