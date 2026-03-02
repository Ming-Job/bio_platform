package com.example.bio_platform.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.UserCourseEnrollment;

public interface UserCourseEnrollmentService extends IService<UserCourseEnrollment> {
    // 检查用户是否已报名该课程，返回报名记录（包含进度）
    UserCourseEnrollment getEnrollmentRecord(Long userId, Long courseId);

    // 用户加入课程
    UserCourseEnrollment enrollCourse(Long userId, Long courseId);
}