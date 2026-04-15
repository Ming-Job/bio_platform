package com.example.bio_platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
// 换个新地盘，原来的老接口冲突！
@RequestMapping("/api/sandbox-files")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final String uploadPath = "D:/docker_share/";

    @PostMapping("/upload") // 完整路径： /api/sandbox-files/upload
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> res = new HashMap<>();
        try {
            if (file.getSize() > 10 * 1024 * 1024) {
                res.put("success", false);
                res.put("message", "文件太大！科教模式仅支持 10MB 以内的数据矩阵");
                return ResponseEntity.badRequest().body(res);
            }
            String fileName = file.getOriginalFilename();
            Path path = Paths.get(uploadPath + fileName);
            Files.write(path, file.getBytes());

            res.put("success", true);
            res.put("fileName", fileName);
            res.put("sandboxPath", "/tmp/sandbox/" + fileName);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.status(500).body(res);
        }
    }
}