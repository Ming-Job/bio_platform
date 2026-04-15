package com.example.bio_platform.controller;

import org.springframework.core.io.Resource;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.entity.FileUpload;
import com.example.bio_platform.service.FileService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Api(tags = "文件管理")
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @ApiOperation("上传单个文件")
    public ResponseEntity<FileUpload> uploadFile(
            @ApiParam(value = "文件", required = true) @RequestParam("file") MultipartFile file,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId,
            @ApiParam(value = "项目ID") @RequestParam(required = false) Long projectId,
            @ApiParam(value = "文件描述") @RequestParam(required = false) String description,
            @ApiParam(value = "文件标签") @RequestParam(required = false) Map<String, Object> tags) {

        FileUpload result = fileService.uploadFile(file, userId, projectId, description, tags);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch-upload")
    @ApiOperation("批量上传文件")
    public ResponseEntity<List<FileUpload>> batchUploadFiles(
            @ApiParam(value = "文件列表", required = true) @RequestParam("files") List<MultipartFile> files,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId,
            @ApiParam(value = "项目ID") @RequestParam(required = false) Long projectId,
            @ApiParam(value = "文件描述") @RequestParam(required = false) String description) {

        List<FileUpload> results = fileService.uploadFiles(files, userId, projectId, description);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/list")
    @ApiOperation("获取文件列表")
    public ResponseEntity<List<File>> getFileList(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId,
            @ApiParam(value = "项目ID") @RequestParam(required = false) Long projectId,
            @ApiParam(value = "文件类型") @RequestParam(required = false) String fileType,
            @ApiParam(value = "文件状态") @RequestParam(required = false) String status) {

        List<File> files = fileService.getUserFiles(userId, projectId, fileType, status);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/all")
    @ApiOperation("获取全量文件列表（专供数据舱使用，包含分析产出物）")
    public ResponseEntity<List<File>> getAllFileList(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId,
            @ApiParam(value = "项目ID") @RequestParam(required = false) Long projectId,
            @ApiParam(value = "文件类型") @RequestParam(required = false) String fileType,
            @ApiParam(value = "文件状态") @RequestParam(required = false) String status) {

        // 🚀 调用刚刚写的全量查询服务
        List<File> files = fileService.getAllUserFiles(userId, projectId, fileType, status);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{fileId}")
    @ApiOperation("获取文件详情")
    public ResponseEntity<File> getFileDetail(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId) {

        File file = fileService.getFileDetail(fileId, userId);
        return file != null ? ResponseEntity.ok(file) : ResponseEntity.notFound().build();
    }

    @ApiOperation("删除文件（软删除）")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<String> deleteFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId) {

        boolean success = fileService.softDeleteFile(fileId, userId);
        return success ? ResponseEntity.ok("文件删除成功") :
                ResponseEntity.badRequest().body("文件删除失败");
    }

    @GetMapping("/stats")
    @ApiOperation("获取文件统计信息")
    public ResponseEntity<Map<String, Object>> getFileStats(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId) {

        Map<String, Object> stats = fileService.getFileStats(userId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent")
    @ApiOperation("获取最近上传的文件")
    public ResponseEntity<List<File>> getRecentUploaded(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId,
            @ApiParam(value = "限制数量", defaultValue = "10") @RequestParam(defaultValue = "10") int limit,
            @ApiParam(value = "项目ID") @RequestParam(required = false) Long projectId) {

        List<File> recentFiles = fileService.getRecentlyUploadedFiles(userId, limit, projectId);
        return ResponseEntity.ok(recentFiles);
    }

    @GetMapping("/preview/{fileId}")
    @ApiOperation("预览大文件内容（截取头部）")
    public ResponseEntity<Map<String, Object>> previewFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId) {

        try {
            // 调用 Service 获取文件文本内容
            String content = fileService.getFilePreviewContent(fileId, userId);

            // 封装成 JSON 对象返回给前端
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", content);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 捕获二进制拦截等异常，返回给前端展示
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/download/{fileId}")
    @ApiOperation("下载文件")
    public ResponseEntity<Resource> downloadFile(
            @ApiParam(value = "文件ID", required = true) @PathVariable Long fileId,
            @ApiParam(value = "用户ID", required = true) @RequestParam Long userId) {

        // 直接返回 Service 构建好的包含了 Stream 和 Header 的 ResponseEntity
        return fileService.downloadFile(fileId, userId);
    }





}