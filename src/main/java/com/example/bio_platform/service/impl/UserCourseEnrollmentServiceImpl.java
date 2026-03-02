package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.entity.UserCourseEnrollment;
import com.example.bio_platform.mapper.UserCourseEnrollmentMapper;
import com.example.bio_platform.service.UserCourseEnrollmentService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserCourseEnrollmentServiceImpl extends ServiceImpl<UserCourseEnrollmentMapper, UserCourseEnrollment> implements UserCourseEnrollmentService {
    @Override
    public UserCourseEnrollment getEnrollmentRecord(Long userId, Long courseId) {
        return this.getOne(new LambdaQueryWrapper<UserCourseEnrollment>()
                .eq(UserCourseEnrollment::getUserId, userId)
                .eq(UserCourseEnrollment::getCourseId, courseId));
    }

    @Override
    public UserCourseEnrollment enrollCourse(Long userId, Long courseId) {
        // 1. 先检查是否已经报名过（防止重复点击插入多条记录）
        UserCourseEnrollment existRecord = getEnrollmentRecord(userId, courseId);
        if (existRecord != null){
            return existRecord; // 已经报过名，直接返回
        }

        // 2. 如果没有报名，则创建新的学习记录
        // 2. 如果没有报名，则创建新的学习记录
        UserCourseEnrollment newRecord = new UserCourseEnrollment()
                .setUserId(userId)
                .setCourseId(courseId)
                .setProgress(0)
                .setStatus("LEARNING")
                .setLastLearnedAt(LocalDateTime.now());

        this.save(newRecord);
        return newRecord;
    }
}
