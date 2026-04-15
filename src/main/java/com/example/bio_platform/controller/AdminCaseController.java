package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.entity.BioCase;
import com.example.bio_platform.service.BioCaseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 🌟 核心修正：导入 Spring 官方的 core.io 资源类，绝不能用 Tomcat 的！
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/cases")
@CrossOrigin(origins = "*")
@Api(tags = "管理员-案例矩阵管理")
public class AdminCaseController {

    @Autowired
    private BioCaseService bioCaseService;

    @GetMapping
    @ApiOperation("分页获取案例列表(带检索与过滤)")
    public ResponseEntity<Map<String, Object>> getAdminCasePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) String category) {

        Page<BioCase> page = bioCaseService.getAdminCasePage(pageNum, pageSize, searchKey, category);
        return ResponseEntity.ok(Map.of("success", true, "data", page));
    }

    @PostMapping
    @ApiOperation("新增案例节点")
    public ResponseEntity<Map<String, Object>> addCase(@RequestBody BioCase bioCase) {
        boolean saved = bioCaseService.save(bioCase);
        return ResponseEntity.ok(Map.of("success", saved, "message", saved ? "案例部署成功" : "部署失败"));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新案例引擎参数")
    public ResponseEntity<Map<String, Object>> updateCase(@PathVariable Long id, @RequestBody BioCase bioCase) {
        bioCase.setId(id);
        boolean updated = bioCaseService.updateById(bioCase);
        return ResponseEntity.ok(Map.of("success", updated, "message", updated ? "参数更新成功" : "更新失败"));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("彻底销毁案例节点")
    public ResponseEntity<Map<String, Object>> deleteCase(@PathVariable Long id) {
        boolean removed = bioCaseService.removeById(id);
        return ResponseEntity.ok(Map.of("success", removed, "message", removed ? "案例已彻底销毁" : "销毁失败"));
    }

    @GetMapping("/download-dataset")
    @ApiOperation("下载案例关联的数据集压缩包")
    public ResponseEntity<Resource> downloadCaseDataset(@RequestParam String fileName) {
        try {
            // 🚨 1. 绝对安全防御：禁止包含 "/" 或 ".." 的非法路径，防止黑客扒服务器文件
            if (fileName == null || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            // 2. 锁定你本地物理机的存放目录
            Path fileStorageLocation = Paths.get("D:/biofile/analysis_data/").toAbsolutePath().normalize();
            Path filePath = fileStorageLocation.resolve(fileName).normalize();

            Resource resource = new UrlResource(filePath.toUri());

            // 3. 检查文件到底存不存在
            if (resource.exists() && resource.isReadable()) {
                // 4. 设置响应头，告诉浏览器这是个附件，赶紧弹窗下载！
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/octet-stream"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}