package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bio_platform.common.Result;
import com.example.bio_platform.dto.AiChatRequest;
import com.example.bio_platform.entity.Course;
import com.example.bio_platform.service.CourseService;
import com.example.bio_platform.service.impl.AiServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "AI接口")
@RestController
@RequestMapping("/api/ai")
@CrossOrigin
public class AiController {

    @Autowired
    private AiServiceImpl aiService;

    @Autowired
    private CourseService courseService;

    // 🌟 核心改动 1：把 MediaType 改成 OCTET_STREAM (二进制流)
    @ApiOperation(value = "流式打字机输出聊天")
    @PostMapping(value = "/chat/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    // 🌟 核心改动 2：入参从 List 变成了刚刚新建的 AiChatRequest
    public ResponseEntity<ResponseBodyEmitter> chatStream(@RequestBody AiChatRequest request) {

        // 使用 Spring 官方推荐的 ResponseBodyEmitter
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(300000L); // 5分钟超时不中断

        // 开启一个新线程去接水，彻底释放 Tomcat 主线程，杜绝超时拦截
        new Thread(() -> {
            // 🌟 核心改动 3：把 request 里的上下文和历史记录拆解开，传给你改好的 Service 方法
            aiService.streamBioAi(request.getCourseContext(), request.getHistoryMessages(), emitter);
        }).start();

        return ResponseEntity.ok().body(emitter);
    }


    // 🌟 AI 选课-课程学习规划
    @ApiOperation(value = "AI 基于课程内容的自然语言选课")
    @PostMapping("/recommend-courses")
    public Result<List<Course>> recommendCourses(@RequestBody Map<String, String> requestData) {
        String userQuery = requestData.get("query");
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return Result.error("请输入你想学习的需求");
        }

        // 1. 从数据库捞出所有【已上架】的课程
        List<Course> allPublishedCourses = courseService.list(
                new LambdaQueryWrapper<Course>().eq(Course::getStatus, "PUBLISHED")
        );

        if (allPublishedCourses.isEmpty()) {
            return Result.error("当前平台暂无上架课程");
        }

        // 2. 核心瘦身：提取 ID、标题、简介
        List<Map<String, Object>> simpleCatalog = new ArrayList<>();
        for (Course c : allPublishedCourses) {
            Map<String, Object> simpleCourse = new HashMap<>();
            simpleCourse.put("id", c.getId());
            simpleCourse.put("title", c.getTitle());
            simpleCourse.put("desc", c.getDescription());
            simpleCatalog.add(simpleCourse);
        }

        // 3. 接收 AI 返回的包含 ids 和 reason 的复合对象
        Map<String, Object> aiResult = aiService.recommendCoursesFromCatalog(userQuery, simpleCatalog);

        // 🌟 强转取出数据
        List<Long> recommendedIds = (List<Long>) aiResult.get("ids");
        String aiReason = (String) aiResult.get("reason");

        // 🌟 满足你的需求：在后端控制台大声打印出 AI 的推荐理由！
        System.out.println("==================================================");
        System.out.println("🤖 用户需求: " + userQuery);
        System.out.println("💡 AI 推荐理由: " + aiReason);
        System.out.println("🎯 命中的课程 ID: " + recommendedIds);
        System.out.println("==================================================");

        if (recommendedIds == null || recommendedIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        // 4. 去数据库把完整的课程信息捞出来返回给前端
        List<Course> recommendedCourses = courseService.listByIds(recommendedIds);

        // 如果你希望前端也能展示这段话，你可以改变 Result 结构，比如把 reason 塞进 message 里返回
        // 这里的代码维持原样，仅在后台打印
        return Result.success(recommendedCourses);
    }

}