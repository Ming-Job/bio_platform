package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.entity.CourseLesson;
import com.example.bio_platform.entity.UserCourseEnrollment;
import com.example.bio_platform.entity.UserLessonProgress;
import com.example.bio_platform.mapper.UserLessonProgressMapper;
import com.example.bio_platform.service.CourseLessonService;
import com.example.bio_platform.service.UserCourseEnrollmentService;
import com.example.bio_platform.service.UserLessonProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserLessonProgressServiceImpl extends ServiceImpl<UserLessonProgressMapper, UserLessonProgress> implements UserLessonProgressService {

    @Autowired
    private CourseLessonService lessonService;

    // 🌟 新增：注入报名表 Service，用来更新总进度
    @Autowired
    private UserCourseEnrollmentService enrollmentService;

    @Override
    @Transactional(rollbackFor = Exception.class) // 🌟 新增：加入事务，保证两张表同时更新成功或失败
    public boolean recordProgress(Long userId, Long lessonId, Integer watchSeconds, boolean isCompleted) {

        // 1. 记录单个课时的进度 (你原有的逻辑)
        UserLessonProgress progress = this.getOne(new LambdaQueryWrapper<UserLessonProgress>()
                .eq(UserLessonProgress::getUserId, userId)
                .eq(UserLessonProgress::getLessonId, lessonId));

        String targetStatus = isCompleted ? "COMPLETED" : "LEARNING";
        boolean success;

        if (progress == null) {
            progress = new UserLessonProgress()
                    .setUserId(userId)
                    .setLessonId(lessonId)
                    .setWatchSeconds(watchSeconds != null ? watchSeconds : 0)
                    .setStatus(targetStatus);
            success = this.save(progress);
        } else {
            progress.setWatchSeconds(watchSeconds != null ? watchSeconds : progress.getWatchSeconds());
            if ("COMPLETED".equals(targetStatus) || !"COMPLETED".equals(progress.getStatus())) {
                progress.setStatus(targetStatus);
            }
            success = this.updateById(progress);
        }

        // 🌟 2. 核心联动逻辑：如果课时记录成功，并且传过来的是“已完成”，则去更新总表的进度
        if (success && isCompleted) {
            updateOverallCourseProgress(userId, lessonId);
        }

        return success;
    }

    /**
     * 计算并更新课程总进度
     */
    private void updateOverallCourseProgress(Long userId, Long lessonId) {
        // a. 先通过 lessonId 查出属于哪个 courseId
        CourseLesson lesson = lessonService.getById(lessonId);
        if (lesson == null) return;
        Long courseId = lesson.getCourseId();

        // b. 查出这门课总共有多少节课时
        long totalLessons = lessonService.count(new LambdaQueryWrapper<CourseLesson>()
                .eq(CourseLesson::getCourseId, courseId));

        if (totalLessons == 0) return;

        // c. 查出用户在这门课中【已完成】的课时数量
        List<Long> lessonIds = lessonService.list(new LambdaQueryWrapper<CourseLesson>()
                        .eq(CourseLesson::getCourseId, courseId))
                .stream().map(CourseLesson::getId).collect(Collectors.toList());

        long completedLessons = this.count(new LambdaQueryWrapper<UserLessonProgress>()
                .eq(UserLessonProgress::getUserId, userId)
                .in(UserLessonProgress::getLessonId, lessonIds)
                .eq(UserLessonProgress::getStatus, "COMPLETED"));

        // d. 计算百分比
        int progressPercent = (int) Math.round((double) completedLessons / totalLessons * 100);

        // e. 更新 user_course_enrollment 表
        UserCourseEnrollment enrollment = enrollmentService.getOne(
                new LambdaQueryWrapper<UserCourseEnrollment>()
                        .eq(UserCourseEnrollment::getUserId, userId)
                        .eq(UserCourseEnrollment::getCourseId, courseId)
        );

        if (enrollment != null) {
            enrollment.setProgress(progressPercent);
            enrollment.setLastLearnedAt(LocalDateTime.now()); // 顺便更新最后学习时间

            if (progressPercent >= 100) {
                enrollment.setStatus("COMPLETED");
            } else {
                enrollment.setStatus("LEARNING");
            }

            enrollmentService.updateById(enrollment);
        }
    }

    @Override
    public List<Long> getCompletedLessonIds(Long userId, Long courseId) {
        // ... 此处保留你原有的 getCompletedLessonIds 代码即可 ...
        List<Long> lessonIds = lessonService.list(new LambdaQueryWrapper<CourseLesson>()
                        .eq(CourseLesson::getCourseId, courseId))
                .stream().map(CourseLesson::getId).collect(Collectors.toList());

        if (lessonIds.isEmpty()) {
            return lessonIds;
        }

        return this.list(new LambdaQueryWrapper<UserLessonProgress>()
                        .eq(UserLessonProgress::getUserId, userId)
                        .in(UserLessonProgress::getLessonId, lessonIds)
                        .eq(UserLessonProgress::getStatus, "COMPLETED"))
                .stream().map(UserLessonProgress::getLessonId).collect(Collectors.toList());
    }
}