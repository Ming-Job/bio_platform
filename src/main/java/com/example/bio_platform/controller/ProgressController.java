package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result; // 替换为你的 Result 路径
import com.example.bio_platform.service.UserLessonProgressService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "学习进度管理")
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private UserLessonProgressService progressService;

    @ApiOperation("记录学习进度")
    @PostMapping("/record")
    public Result<Boolean> recordProgress(@RequestParam Long userId,
                                          @RequestParam Long lessonId,
                                          @RequestParam(required = false, defaultValue = "0") Integer watchSeconds,
                                          @RequestParam(required = false, defaultValue = "false") Boolean isCompleted) {
        boolean success = progressService.recordProgress(userId, lessonId, watchSeconds, isCompleted);
        return Result.success(success);
    }

    @ApiOperation("获取课程已完成的课时ID列表")
    @GetMapping("/completed/{courseId}")
    public Result<List<Long>> getCompletedLessons(@PathVariable Long courseId,
                                                  @RequestParam Long userId) {
        List<Long> completedIds = progressService.getCompletedLessonIds(userId, courseId);
        return Result.success(completedIds);
    }
}