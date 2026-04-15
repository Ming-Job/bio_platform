package com.example.bio_platform.service.impl;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.config.FileUploadProperties;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.entity.FileUpload;
import com.example.bio_platform.entity.Project;
import com.example.bio_platform.mapper.FileMapper;
import com.example.bio_platform.mapper.FileUploadMapper;
import com.example.bio_platform.mapper.ProjectMapper;
import com.example.bio_platform.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    private final FileMapper fileMapper;
    private final FileUploadMapper fileUploadMapper;
    private final ProjectMapper projectMapper;
    private final FileUploadProperties fileUploadProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUpload uploadFile(MultipartFile multipartFile, Long userId, Long projectId,
                                 String description, Map<String, Object> tags) {

        try {
            if (fileUploadProperties == null) {
                log.error("FileUploadProperties 配置未注入！");
                throw new RuntimeException("系统配置错误，请联系管理员");
            }

            String baseDir = fileUploadProperties.getBaseDir();
            if (baseDir == null || baseDir.trim().isEmpty()) {
                baseDir = "./bio_uploads/files";
                log.warn("配置文件基础目录为空，使用默认值: {}", baseDir);
            }

            validateFile(multipartFile);

            String originalName = multipartFile.getOriginalFilename();
            String fileExt = getFileExtension(originalName);
            String fileType = determineFileType(fileExt);
            long fileSize = multipartFile.getSize();
            LocalDateTime now = LocalDateTime.now();

            String md5Hash;
            try (InputStream md5Stream = multipartFile.getInputStream()) {
                md5Hash = DigestUtils.md5DigestAsHex(md5Stream);
            }

            // ==========================================
            // 🌟 1. 严格查重：防止用户在“同一个课题”下重复传同一个文件
            // ==========================================
            LambdaQueryWrapper<File> dupQuery = new LambdaQueryWrapper<>();
            dupQuery.eq(File::getUserId, userId)
                    .eq(File::getMd5Hash, md5Hash)
                    .ne(File::getStatus, "deleted");
            if (projectId != null) {
                dupQuery.eq(File::getProjectId, projectId);
            } else {
                dupQuery.isNull(File::getProjectId);
            }
            if (fileMapper.selectCount(dupQuery) > 0) {
                FileUpload dupRecord = new FileUpload();
                dupRecord.setIsDuplicate(true);
                dupRecord.setStatus("error");
                dupRecord.setMessage("提示：该文件已存在于当前课题中，无需重复上传。");
                return dupRecord;
            }

            // ==========================================
            // 🌟 2. 物理秒传：去全库找有没有这个 MD5 的物理文件
            // ==========================================
            File existingPhysicalFile = fileMapper.selectByMd5AndUserId(md5Hash, userId);

            // 🌟 核心修复：为了不触发数据库的 uk_stored_name 唯一约束，永远生成全新的 UUID 作为 storedName
            String storedName = UUID.randomUUID().toString() + fileExt;
            String relativePath;
            boolean isPhysicalUploadNeeded = true; // 是否需要真的把文件写入 D 盘

            if (existingPhysicalFile != null && existingPhysicalFile.getStoragePath() != null) {
                // 找到物理文件了！直接复用它的相对路径，省下 D 盘空间
                log.info("【极速秒传】发现物理复用文件: {}，直接复用路径: {}", originalName, existingPhysicalFile.getStoragePath());
                relativePath = existingPhysicalFile.getStoragePath(); // 物理路径用老的！
                isPhysicalUploadNeeded = false;
            } else {
                // 没找到，老老实实生成新路径
                relativePath = String.format("%d/%d/%02d/%02d/%s",
                        userId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), storedName);
            }

            // ==========================================
            // 🌟 3. 物理落盘 (如果是秒传，这步直接跳过，速度极快！)
            // ==========================================
            if (isPhysicalUploadNeeded) {
                Path filePath = Paths.get(baseDir, relativePath);
                Files.createDirectories(filePath.getParent());
                try (InputStream saveStream = multipartFile.getInputStream()) {
                    Files.copy(saveStream, filePath);
                }
                log.info("新文件物理落盘成功: {} -> {}", originalName, relativePath);
            }

            // ==========================================
            // 🌟 4. 逻辑入库：不管是不是秒传，都要在数据库里新建一条记录！
            // ==========================================
            String projectName = null;
            if (projectId != null) {
                Project project = projectMapper.selectById(projectId);
                if (project != null) {
                    projectName = project.getName();
                }
            }

            File newFileRecord = new File()
                    .setOriginalName(originalName)
                    .setStoredName(storedName)
                    .setFileType(fileType)
                    .setFileExt(fileExt)
                    .setSizeBytes(fileSize)
                    .setStoragePath(relativePath) // 如果是秒传，这里存的就是复用的路径
                    .setMd5Hash(md5Hash)
                    .setUserId(userId)
                    .setProjectId(projectId)      // 绑定到新的课题下！
                    .setStatus("ready")
                    .setDescription(description)
                    .setUploadTime(now)
                    .setUpdateTime(now)
                    .setFileSource("upload");

            this.save(newFileRecord); // 🌟 插入全新的数据库记录

            newFileRecord.setFormattedSize(formatFileSize(fileSize));
            newFileRecord.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + newFileRecord.getId());
            newFileRecord.setProjectName(projectName);

            // ==========================================
            // 🌟 5. 返回成功记录给前端
            // ==========================================
            FileUpload uploadRecord = createUploadRecord(newFileRecord, userId, fileSize, now);
            uploadRecord.setIsDuplicate(false); // 对前端来说，这是一次成功的全新上传
            uploadRecord.setStatus("completed");

            return uploadRecord;

        } catch (IOException e) {
            log.error("文件流读取或写入失败", e);
            throw new RuntimeException("文件处理失败，请重试");
        }
    }

    private FileUpload createUploadRecord(File file, Long userId, Long fileSize, LocalDateTime startTime) {
        FileUpload fileUpload = new FileUpload()
                .setFileId(file.getId())
                .setUserId(userId)
                .setUploadSession(UUID.randomUUID().toString())
                .setUploadMethod("direct")
                .setBytesUploaded(fileSize)
                .setBytesTotal(fileSize)
                .setStatus("completed")
                .setStartTime(startTime)
                .setEndTime(LocalDateTime.now());

        fileUploadMapper.insert(fileUpload);
        fileUpload.setFormattedBytesUploaded(formatFileSize(fileSize));
        fileUpload.setFormattedBytesTotal(formatFileSize(fileSize));
        fileUpload.setFile(file);

        return fileUpload;
    }

    @Override
    public List<FileUpload> uploadFiles(List<MultipartFile> files, Long userId, Long projectId, String description) {
        List<FileUpload> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                FileUpload result = uploadFile(file, userId, projectId, description, null);
                results.add(result);
            } catch (Exception e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);
                FileUpload failedResult = new FileUpload();
                failedResult.setStatus("error");
                failedResult.setMessage(e.getMessage() != null ? e.getMessage(): "上传过程中发生系统异常");
                results.add(failedResult);
            }
        }
        return results;
    }

    @Override
    public List<File> getUserFiles(Long userId, Long projectId, String fileType, String status) {
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId);

        if (projectId != null) {
            queryWrapper.eq(File::getProjectId, projectId);
        }

        if (StringUtils.hasText(fileType)) {
            queryWrapper.eq(File::getFileType, fileType);
        }

        if (StringUtils.hasText(status)) {
            queryWrapper.eq(File::getStatus, status);
        } else {
            queryWrapper.ne(File::getStatus, "deleted");
        }

        // 过滤掉系统生成的产出物
        queryWrapper.ne(File::getFileSource, "generate");

        queryWrapper.orderByDesc(File::getUploadTime);
        List<File> files = this.list(queryWrapper);

        for (File file : files) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());
            if (file.getProjectId() != null) {
                Project project = projectMapper.selectById(file.getProjectId());
                if (project != null) {
                    file.setProjectName(project.getName());
                }
            }
        }
        return files;
    }

    @Override
    public File getFileDetail(Long fileId, Long userId) {
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getId, fileId)
                .eq(File::getUserId, userId)
                .ne(File::getStatus, "deleted");
        File file = this.getOne(queryWrapper);

        if (file != null) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());
            if (file.getProjectId() != null) {
                Project project = projectMapper.selectById(file.getProjectId());
                if (project != null) {
                    file.setProjectName(project.getName());
                }
            }
        }
        return file;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean softDeleteFile(Long fileId, Long userId) {
        File file = getFileDetail(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        file.setStatus("deleted");
        file.setUpdateTime(LocalDateTime.now());
        return this.updateById(file);
    }

    @Override
    public Map<String, Object> getFileStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        List<Map<String, Object>> typeStats = fileMapper.countByFileType(userId);
        stats.put("typeStats", typeStats);
        Long totalStorage = fileMapper.selectTotalStorageByUserId(userId);
        stats.put("totalStorage", totalStorage);
        stats.put("formattedTotalStorage", formatFileSize(totalStorage));
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId).ne(File::getStatus, "deleted");
        Long totalFiles = this.count(queryWrapper);
        stats.put("totalFiles", totalFiles);
        return stats;
    }

    @Override
    public String getFilePreviewContent(Long fileId, Long userId) {
        File file = getFileDetail(fileId, userId);
        if (file == null){
            throw new RuntimeException("文件不存在或无权访问");
        }
        String ext = file.getFileExt().toLowerCase();
        if (ext.equals(".bam") || ext.equals(".bai")){
            throw new RuntimeException("二进制比对文件暂不支持在线预览，请下载后查看");
        }
        String baseDir = fileUploadProperties.getBaseDir();
        if (baseDir == null || baseDir.trim().isEmpty()){
            baseDir = "./bio_uploads/files";
        }
        Path filePath = Paths.get(baseDir, file.getStoragePath());
        if (!Files.exists(filePath)){
            throw new RuntimeException("物理文件已丢失");
        }
        StringBuilder contentBuilder = new StringBuilder();
        int maxLines = 100;
        try{
            InputStream fileStream = Files.newInputStream(filePath);
            if (ext.endsWith(".gz")){
                fileStream =  new GZIPInputStream(fileStream);
            }
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream))){
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < maxLines) {
                    contentBuilder.append(line).append("\n");
                    lineCount++;
                }
                if (line != null){
                    contentBuilder.append("\n\n... (文件体积过大，为保证系统性能，仅展示前)").append(maxLines).append(" 行) ...");
                }
            }
            return contentBuilder.toString();
        } catch (IOException e) {
            log.error("读取预览文件失败: {}", file.getOriginalName(), e);
            throw new RuntimeException("读取文件内容失败: " + e.getMessage());
        }
    }

    @Override
    public List<File> getRecentlyUploadedFiles(Long userId, int limit, Long projectId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (limit <= 0) {
            limit = 10;
        }

        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId)
                .ne(File::getStatus, "deleted")
                // 坚决剔除系统生成的产出物，只展示真实的上传文件
                .ne(File::getFileSource, "generate");

        // 🌟 按需求：在项目页进来的只查该项目；在全局大厅则不加限制查所有
        if (projectId != null) {
            queryWrapper.eq(File::getProjectId, projectId);
        }

        queryWrapper.orderByDesc(File::getUploadTime)
                .last("LIMIT " + limit);

        List<File> files = this.list(queryWrapper);

        for (File file : files) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());
            if (file.getProjectId() != null) {
                Project project = projectMapper.selectById(file.getProjectId());
                if (project != null) {
                    file.setProjectName(project.getName());
                }
            }
        }
        return files;
    }

    @Override
    public ResponseEntity<Resource> downloadFile(Long fileId, Long userId) {
        File file = getFileDetail(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }
        String baseDir = fileUploadProperties.getBaseDir();
        if (baseDir == null || baseDir.trim().isEmpty()) {
            baseDir = "./bio_uploads/files";
        }
        Path filePath = Paths.get(baseDir, file.getStoragePath());
        Resource resource;
        String originalName = file.getOriginalName();

        if (!Files.exists(filePath)) {
            log.warn("物理文件丢失或为模拟任务产出物，触发降级下载: {}", filePath);
            String mockContent = "【Bio-OS 系统提示】\n" +
                    "=========================================\n" +
                    "这是一份模拟计算产出的生信分析数据。\n" +
                    "因为目前处于模拟调度阶段，真正的文件并未在服务器物理磁盘上生成。\n\n" +
                    "请求的文件名：" + originalName + "\n" +
                    "归属任务/文件ID：" + fileId + "\n" +
                    "=========================================\n" +
                    "请在接入真实算力集群后，再获取真实结果！";
            resource = new org.springframework.core.io.ByteArrayResource(mockContent.getBytes(StandardCharsets.UTF_8));
        } else {
            try {
                resource = new UrlResource(filePath.toUri());
            } catch (Exception e) {
                log.error("物理文件流转换失败", e);
                throw new RuntimeException("文件读取异常");
            }
        }

        try {
            String encodedFileName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("文件下载响应构建失败", e);
            throw new RuntimeException("文件打包下载失败: " + e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String fileExt = getFileExtension(filename).toLowerCase();
        List<String> allowedExtensions = fileUploadProperties.getAllowedExtensions();
        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            if (!allowedExtensions.contains(fileExt)) {
                throw new IllegalArgumentException("不支持的文件类型: " + fileExt +
                        "，允许的类型: " + allowedExtensions);
            }
        }
        if (file.getSize() > fileUploadProperties.getMaxFileSize()) {
            throw new IllegalArgumentException("文件大小超过限制: " + file.getSize() +
                    "，最大允许: " + fileUploadProperties.getMaxFileSize());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "";
        }
        String lowerFilename = filename.toLowerCase();
        String[] doubleExtensions = {
                ".fastq.gz", ".fq.gz", ".fasta.gz", ".fa.gz", ".tar.gz", ".pdb.gz"
        };
        for (String ext : doubleExtensions) {
            if (lowerFilename.endsWith(ext)) {
                return filename.substring(filename.length() - ext.length());
            }
        }
        int lastDot = filename.lastIndexOf(".");
        return lastDot == -1 ? "" : filename.substring(lastDot);
    }

    private String determineFileType(String fileExt) {
        fileExt = fileExt.toLowerCase();
        if (fileExt.contains("fastq") || fileExt.contains("fq")) {
            return "fastq";
        } else if (fileExt.contains("fasta") || fileExt.contains("fa")) {
            return "fasta";
        }else if (fileExt.contains("pdb")) {
            return "pdb";
        } else if (fileExt.contains("sdf")) {
            return "sdf";
        }else if (fileExt.contains("bam")) {
            return "bam";
        } else if (fileExt.contains("sam")) {
            return "sam";
        } else if (fileExt.contains("vcf")) {
            return "vcf";
        } else if (fileExt.contains("bed")) {
            return "bed";
        } else if (fileExt.contains("gtf")) {
            return "gtf";
        }
        return "other";
    }

    private String formatFileSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes == 0) {
            return "0 B";
        }
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format("%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @Override
    public List<File> getAllUserFiles(Long userId, Long projectId, String fileType, String status) {
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId);

        if (projectId != null) {
            queryWrapper.eq(File::getProjectId, projectId);
        }
        if (StringUtils.hasText(fileType)) {
            queryWrapper.eq(File::getFileType, fileType);
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(File::getStatus, status);
        } else {
            queryWrapper.ne(File::getStatus, "deleted");
        }

        // 🚀 纯净的全量查询，不加 .ne(FileSource, "generate") 限制！
        queryWrapper.orderByDesc(File::getUploadTime);
        List<File> files = this.list(queryWrapper);

        for (File file : files) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());
            if (file.getProjectId() != null) {
                Project project = projectMapper.selectById(file.getProjectId());
                if (project != null) {
                    file.setProjectName(project.getName());
                }
            }
        }
        return files;
    }

    @Override
    public Map<Long, Map<String, Number>> getProjectFileStatsMap(Long userId) {
        List<Map<String, Object>> list = baseMapper.countFileStatsGroupByProject(userId);

        Map<Long, Map<String, Number>> statsMap = new HashMap<>();
        for (Map<String, Object> map : list) {
            Long projectId = ((Number) map.get("project_id")).longValue();
            Integer count = ((Number) map.get("file_count")).intValue();
            Long totalSize = map.get("total_size") != null ? ((Number) map.get("total_size")).longValue() : 0L;

            Map<String, Number> innerMap = new HashMap<>();
            innerMap.put("fileCount", count);
            innerMap.put("totalSize", totalSize);

            statsMap.put(projectId, innerMap);
        }
        return statsMap;
    }
}