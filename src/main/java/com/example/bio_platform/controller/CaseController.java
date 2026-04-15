package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.bio_platform.entity.BioCase;
import com.example.bio_platform.service.BioCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cases")
@CrossOrigin(origins = "*")
public class CaseController {

    @Autowired
    private BioCaseService bioCaseService;

    // 1. 获取案例列表 (给 CaseSquare.vue 用)
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getCaseList() {
        Map<String, Object> res = new HashMap<>();
        try {
            // 调用 Service 层的 list 方法
            List<BioCase> list = bioCaseService.list(
                    new QueryWrapper<BioCase>().orderByDesc("create_time")
            );
            res.put("success", true);
            res.put("data", list);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "获取案例列表失败：" + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }

    // 2. 获取单个案例详情 (给 CaseDetail.vue 用)
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCaseDetail(@PathVariable Long id) {
        Map<String, Object> res = new HashMap<>();
        try {
            // 调用 Service 层的 getById 方法
            BioCase bioCase = bioCaseService.getById(id);
            if (bioCase != null) {
                res.put("success", true);
                res.put("data", bioCase);
            } else {
                res.put("success", false);
                res.put("message", "案例游标不存在，或已被系统删除");
            }
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "详情解析失败：" + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }
}