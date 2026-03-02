package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.UserLessonProgress;
import java.util.List;

public interface UserLessonProgressService extends IService<UserLessonProgress> {
    // 记录或更新学习进度
    boolean recordProgress(Long userId, Long lessonId, Integer watchSeconds, boolean isCompleted);

    // 获取用户在某门课程下【已完成】的课时 ID 列表
    List<Long> getCompletedLessonIds(Long userId, Long courseId);
}