package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.bio_platform.entity.Course;
import com.example.bio_platform.service.UserService;
import com.example.bio_platform.service.CourseService;
import com.example.bio_platform.service.BioCaseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/statistics") // 🌟 匹配你前端 api/user.js 里的路径前缀
@Api(tags = "管理员-仪表盘统计接口")
public class AdminDashboardController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private BioCaseService bioCaseService;

    // 1. 获取四大核心指标统计
    @GetMapping("/overview")
    @ApiOperation("获取四个核心指标总数")
    public ResponseEntity<Map<String, Object>> getOverviewStatistics() {
        Map<String, Object> res = new HashMap<>();
        try {
            // 这里调用你 UserService 原本就有的方法（如果里面已经写好了，就直接用）
            Map<String, Object> userStats = userService.getUserStatistics();
            long totalUsers = userStats.containsKey("totalUsers") ?
                    Long.parseLong(userStats.get("totalUsers").toString()) : 0L;

            // 查课程总数
            long totalCourses = courseService.count();

            // 查案例/数据集总数
            long totalDatasets = bioCaseService.count();

            // 计算今日新增用户 (我们马上会在 Service 里实现这个方法)
            long todayNewUsers = userService.countTodayNewUsers();

            Map<String, Object> data = new HashMap<>();
            data.put("totalUsers", totalUsers);
            data.put("totalCourses", totalCourses);
            data.put("totalDatasets", totalDatasets);
            data.put("todayNewUsers", todayNewUsers);

            res.put("success", true);
            res.put("data", data);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("大盘数据统计失败", e);
            res.put("success", false);
            res.put("message", "统计数据查询失败");
            return ResponseEntity.status(500).body(res);
        }
    }

    // 2. 真实的分组聚合折线图数据
    @GetMapping("/user-growth/chart")
    @ApiOperation("获取用户真实增长曲线")
    public ResponseEntity<Map<String, Object>> getUserGrowthChart(@RequestParam(defaultValue = "30d") String period) {
        Map<String, Object> res = new HashMap<>();
        try {
            int days = "7d".equals(period) ? 7 : "90d".equals(period) ? 90 : 30;

            // 调用 Service 层去查真实的聚合数据 (马上实现它)
            Map<String, Object> chartData = userService.getRealUserGrowthChart(days);

            res.put("success", true);
            res.put("data", chartData);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取折线图数据失败", e);
            res.put("success", false);
            res.put("message", "折线图数据获取失败");
            return ResponseEntity.status(500).body(res);
        }
    }

    // 3. 获取课程难度分布饼图
    @GetMapping("/course-distribution")
    @ApiOperation("获取课程难度分布饼图")
    public ResponseEntity<Map<String, Object>> getCourseDistribution() {
        Map<String, Object> res = new HashMap<>();
        try {
            // 执行 SQL: SELECT difficulty AS name, COUNT(id) AS value FROM course GROUP BY difficulty
            QueryWrapper<Course> wrapper = new QueryWrapper<>();
            wrapper.select("difficulty as name", "COUNT(id) as value")
                    .groupBy("difficulty");

            // 🌟 这里改用 courseService
            List<Map<String, Object>> list = courseService.listMaps(wrapper);

            // 把英文难度翻译成图表上好看的中文标签
            for (Map<String, Object> map : list) {
                String name = String.valueOf(map.get("name"));
                if ("BEGINNER".equalsIgnoreCase(name)) {
                    map.put("name", "初级课程");
                } else if ("INTERMEDIATE".equalsIgnoreCase(name)) {
                    map.put("name", "中级课程");
                } else if ("ADVANCED".equalsIgnoreCase(name)) {
                    map.put("name", "高级课程");
                } else {
                    map.put("name", "未定级");
                }
            }

            res.put("success", true);
            res.put("data", list);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取课程分布饼图数据失败", e);
            res.put("success", false);
            res.put("message", "饼图数据获取失败");
            return ResponseEntity.status(500).body(res);
        }
    }
}