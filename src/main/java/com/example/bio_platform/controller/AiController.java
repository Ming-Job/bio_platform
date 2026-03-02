package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result;
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

    // 🌟 核心改动 1：把 MediaType 改成 OCTET_STREAM (二进制流)，这是防前端代理缓存的最强杀手锏
    @ApiOperation(value = "流式打字机输出聊天")
    @PostMapping(value = "/chat/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> chatStream(@RequestBody List<Map<String, String>> messages) {

        // 🌟 核心改动 2：使用 Spring 官方推荐的 ResponseBodyEmitter
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(300000L); // 5分钟超时不中断

        // 🌟 核心改动 3：开启一个新线程去接水，彻底释放 Tomcat 主线程，杜绝超时拦截
        new Thread(() -> {
            aiService.streamBioAi(messages, emitter);
        }).start();

        return ResponseEntity.ok().body(emitter);
    }


    // 🌟 新增：AI 自然语言选课接口
    @ApiOperation(value = "ai自然语言选课")
    @PostMapping("/recommend-courses")
    public Result<List<Course>> recommendCourses(@RequestBody Map<String, String> requestData) {
        String userQuery = requestData.get("query");
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return Result.error("请输入你想学习的需求");
        }

        // 1. 让 AI 提取关键词
        List<String> keywords = aiService.extractKeywords(userQuery);

        // 顺便在控制台打印一下，看看 AI 提取准不准！
        System.out.println("====== AI 提取的关键词: " + keywords + " ======");

        if (keywords.isEmpty()) {
            return Result.success(new ArrayList<>()); // 没提取出来，返回空列表
        }

        // 2. 拿着关键词去查数据库
        List<Course> recommendedCourses = courseService.searchCoursesByKeywords(keywords);

        // 3. 返回给前端渲染
        return Result.success(recommendedCourses);
    }
}