package com.example.bio_platform.service.impl;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.config.FileUploadProperties;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.entity.FileMetadata;
import com.example.bio_platform.entity.FileUpload;
import com.example.bio_platform.entity.Project;
import com.example.bio_platform.mapper.FileMapper;
import com.example.bio_platform.mapper.FileMetadataMapper;
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
    private final FileMetadataMapper fileMetadataMapper;
    private final ProjectMapper projectMapper;
    private final FileUploadProperties fileUploadProperties;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileUpload uploadFile(MultipartFile multipartFile, Long userId, Long projectId,
                                 String description, Map<String, Object> tags) {

        try {
            // 1. 检查配置是否注入
            if (fileUploadProperties == null) {
                log.error("FileUploadProperties 配置未注入！");
                throw new RuntimeException("系统配置错误，请联系管理员");
            }

            String baseDir = fileUploadProperties.getBaseDir();
            if (baseDir == null || baseDir.trim().isEmpty()) {
                baseDir = "./bio_uploads/files";
                log.warn("配置文件基础目录为空，使用默认值: {}", baseDir);
            }

            // 2. 验证文件 (假设已校验大小和格式)
            validateFile(multipartFile);

            // 3. 提取基本信息
            String originalName = multipartFile.getOriginalFilename();
            String fileExt = getFileExtension(originalName);
            String fileType = determineFileType(fileExt);
            long fileSize = multipartFile.getSize();
            LocalDateTime now = LocalDateTime.now();

            // ==================== 核心优化部分开始 ====================

            // 4. 【优化】先计算 MD5（直接获取一次流进行计算，避免使用 reset）
            String md5Hash;
            try (InputStream md5Stream = multipartFile.getInputStream()) {
                md5Hash = DigestUtils.md5DigestAsHex(md5Stream);
            }

            // 5. 【优化】拦截重复文件（在落盘前拦截，彻底杜绝垃圾物理文件）
            File existingFile = fileMapper.selectByMd5AndUserId(md5Hash, userId);
            if (existingFile != null && existingFile.getUserId().equals(userId)) {
                log.info("文件已存在，触发秒传机制: {}", existingFile.getOriginalName());

                // 创建上传记录
                FileUpload uploadRecord = createUploadRecord(existingFile, userId, fileSize, now);

                // 塞入前端提示标记（温和提示方案）
                uploadRecord.setIsDuplicate(true);
                uploadRecord.setMessage("文件已存在，已为您极速秒传。");

                return uploadRecord; // 直接返回，完全不执行后续的磁盘 I/O 操作
            }

            // 6. 生成唯一标识和存储路径（只有确认为新文件，才生成路径）
            String storedName = UUID.randomUUID().toString() + fileExt;
            String relativePath = String.format("%d/%d/%02d/%02d/%s",
                    userId, now.getYear(), now.getMonthValue(), now.getDayOfMonth(), storedName);
            Path filePath = Paths.get(baseDir, relativePath);

            // 7. 确保目录存在
            Files.createDirectories(filePath.getParent());

            // 8. 【优化】真正的物理落盘（重新获取一次流写入磁盘）
            try (InputStream saveStream = multipartFile.getInputStream()) {
                Files.copy(saveStream, filePath);
            }

            // ==================== 核心优化部分结束 ====================

            // 9. 获取项目信息（用于返回给前端）
            String projectName = null;
            if (projectId != null) {
                Project project = projectMapper.selectById(projectId);
                if (project != null) {
                    projectName = project.getName();
                }
            }

            // 10. 保存新文件信息到数据库
            File file = new File()
                    .setOriginalName(originalName)
                    .setStoredName(storedName)
                    .setFileType(fileType)
                    .setFileExt(fileExt)
                    .setSizeBytes(fileSize)
                    .setStoragePath(relativePath)
                    .setMd5Hash(md5Hash)
                    .setUserId(userId)
                    .setProjectId(projectId)
                    .setStatus("ready")
                    .setDescription(description)
                    .setUploadTime(now)
                    .setUpdateTime(now);

            this.save(file);
            log.info("新文件物理落盘并上传成功: {} -> {}", originalName, relativePath);

            // 11. 设置计算字段并返回
            file.setFormattedSize(formatFileSize(fileSize));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());
            file.setProjectName(projectName);

            // 创建上传记录并明确标识为新上传
            FileUpload newUploadRecord = createUploadRecord(file, userId, fileSize, now);
            newUploadRecord.setIsDuplicate(false);
            newUploadRecord.setMessage("上传成功！");

            return newUploadRecord;

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

        // 设置上传记录的计算字段
        fileUpload.setFormattedBytesUploaded(formatFileSize(fileSize));
        fileUpload.setFormattedBytesTotal(formatFileSize(fileSize));
        fileUpload.setFile(file); // 关联文件信息

        return fileUpload;
    }

    @Override
    public List<FileUpload> uploadFiles(List<MultipartFile> files, Long userId, Long projectId, String description) {
        List<FileUpload> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                // 调用优化后的单文件上传 如果重复会返回秒传标识，如果成功会返回新对象
                FileUpload result = uploadFile(file, userId, projectId, description, null);
                results.add(result);
            } catch (Exception e) {
                log.error("文件上传失败: {}", file.getOriginalFilename(), e);

                // 构造一个带有错误信息的返回对象，让前端知道死在哪了
                FileUpload failedResult = new FileUpload();
                failedResult.setStatus("error");  // 前端根据这个状态判断失败

                // 提取异常信息传递给前端
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

        queryWrapper.orderByDesc(File::getUploadTime);
        List<File> files = this.list(queryWrapper);

        // 设置计算字段
        for (File file : files) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());

            // 如果需要项目名称，可以单独查询
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
            // 设置计算字段
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());

            // 获取项目信息
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

        // 逻辑删除：更新状态为deleted
        // 注意：数据库中没有delete_time字段，这里只更新状态
        file.setStatus("deleted");
        file.setUpdateTime(LocalDateTime.now());

        return this.updateById(file);
    }

    @Override
    public Map<String, Object> getFileStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 获取文件类型统计
        List<Map<String, Object>> typeStats = fileMapper.countByFileType(userId);
        stats.put("typeStats", typeStats);

        // 获取总存储空间使用量
        Long totalStorage = fileMapper.selectTotalStorageByUserId(userId);
        stats.put("totalStorage", totalStorage);
        stats.put("formattedTotalStorage", formatFileSize(totalStorage));

        // 获取文件总数
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId)
                .ne(File::getStatus, "deleted");
        Long totalFiles = this.count(queryWrapper);
        stats.put("totalFiles", totalFiles);

        return stats;
    }

    @Override
    public String getFilePreviewContent(Long fileId, Long userId) {
        // 1. 复现现有的查询方法，确保文件属于该用户且未被删除
        File file = getFileDetail(fileId, userId);
        if (file == null){
            throw new RuntimeException("文件不存在或无权访问");
        }
        String ext = file.getFileExt().toLowerCase();

        // 2. 拦截纯二进制文件（生信文件中的 bam 和 bai 无法直接预览）
        if (ext.equals(".bam") || ext.equals(".bai")){
            throw new RuntimeException("二进制比对文件暂不支持在线预览，请下载后查看");
        }

        // 3. 组装物理路径
        String baseDir = fileUploadProperties.getBaseDir();
        if (baseDir == null || baseDir.trim().isEmpty()){
            baseDir = "./bio_uploads/files";
        }
        Path filePath = Paths.get(baseDir, file.getStoragePath());

        if (!Files.exists(filePath)){
            throw new RuntimeException("物理文件已丢失");
        }

        // 4. 流式读取文件前 100 行
        StringBuilder contentBuilder = new StringBuilder();
        int maxLines = 100; // 限制预览行数，防止OOM

        try{
            InputStream fileStream = Files.newInputStream(filePath);

            // 如果是 .gz 结尾的压缩文件, 自动挂载 GZIP 解压流
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

    // 获取最近上传的文件
    @Override
    public List<File> getRecentlyUploadedFiles(Long userId, int limit, Long projectId) {
        // 参数校验
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (limit <= 0) {
            limit = 10; // 默认限制10个文件
        }

        // 构建查询条件
        LambdaQueryWrapper<File> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(File::getUserId, userId)
                .ne(File::getStatus, "deleted"); // 排除已删除的文件

        // 如果指定了项目ID，则添加项目过滤条件
        if (projectId != null) {
            queryWrapper.eq(File::getProjectId, projectId);
        }

        // 按上传时间降序排列，获取最近的文件
        queryWrapper.orderByDesc(File::getUploadTime)
                .last("LIMIT " + limit); // 限制返回数量

        List<File> files = this.list(queryWrapper);

        // 设置计算字段
        for (File file : files) {
            file.setFormattedSize(formatFileSize(file.getSizeBytes()));
            file.setDownloadUrl(fileUploadProperties.getBaseUrl() + "/download/" + file.getId());

            // 设置项目名称（如果需要）
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
        // 1. 验证权限并获取文件记录 (复用你写好的方法)
        File file = getFileDetail(fileId, userId);
        if (file == null) {
            throw new RuntimeException("文件不存在或无权访问");
        }

        // 2. 组装物理路径
        String baseDir = fileUploadProperties.getBaseDir();
        if (baseDir == null || baseDir.trim().isEmpty()) {
            baseDir = "./bio_uploads/files";
        }
        Path filePath = Paths.get(baseDir, file.getStoragePath());

        // 3. 检查物理文件是否存在
        if (!Files.exists(filePath)) {
            log.error("尝试下载丢失的文件: {}", filePath);
            throw new RuntimeException("服务器上的物理文件已丢失");
        }

        try {
            // 4. 将物理文件转换为 Spring 的 Resource 对象 (流式读取的核心)
            Resource resource = new UrlResource(filePath.toUri());

            // 5. 解决下载时文件名（尤其是中文）变乱码或变下划线的问题
            String originalName = file.getOriginalName();
            String encodedFileName = URLEncoder.encode(originalName, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20"); // 修复空格被转成加号的Bug

            // 6. 构建并返回包含附件下载 Header 的响应
            return ResponseEntity.ok()
                    // 标记为二进制流
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    // 告诉浏览器这是一个附件，并告知应该保存的文件名
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    // 返回流对象
                    .body(resource);

        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw new RuntimeException("文件打包下载失败: " + e.getMessage());
        }
    }


    // 辅助方法
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 检查文件扩展名
        String fileExt = getFileExtension(filename).toLowerCase();
        List<String> allowedExtensions = fileUploadProperties.getAllowedExtensions();

        if (allowedExtensions != null && !allowedExtensions.isEmpty()) {
            if (!allowedExtensions.contains(fileExt)) {
                throw new IllegalArgumentException("不支持的文件类型: " + fileExt +
                        "，允许的类型: " + allowedExtensions);
            }
        }

        // 检查文件大小
        if (file.getSize() > fileUploadProperties.getMaxFileSize()) {
            throw new IllegalArgumentException("文件大小超过限制: " + file.getSize() +
                    "，最大允许: " + fileUploadProperties.getMaxFileSize());
        }
    }

    /**
     * 智能获取文件扩展名（支持生信领域的双后缀，如 .fa.gz）
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "";
        }

        String lowerFilename = filename.toLowerCase();

        // 1. 优先匹配生信领域常见的双后缀 (你可以根据需要增删)
        String[] doubleExtensions = {
                ".fastq.gz", ".fq.gz", ".fasta.gz", ".fa.gz", ".tar.gz"
        };

        for (String ext : doubleExtensions) {
            if (lowerFilename.endsWith(ext)) {
                // 截取真实的原始后缀，保留原有大小写
                return filename.substring(filename.length() - ext.length());
            }
        }

        // 2. 如果不是双后缀，退化为常规的单后缀截取
        int lastDot = filename.lastIndexOf(".");
        return lastDot == -1 ? "" : filename.substring(lastDot);
    }

    private String determineFileType(String fileExt) {
        fileExt = fileExt.toLowerCase();
        if (fileExt.contains("fastq") || fileExt.contains("fq")) {
            return "fastq";
        } else if (fileExt.contains("fasta") || fileExt.contains("fa")) {
            return "fasta";
        } else if (fileExt.contains("bam")) {
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

    // 格式化文件大小
    private String formatFileSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes == 0) {
            return "0 B";
        }

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));

        return String.format("%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }









}