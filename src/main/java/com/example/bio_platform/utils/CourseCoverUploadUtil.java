package com.example.bio_platform.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 课程封面上传工具类
 */
@Component
public class CourseCoverUploadUtil {

    @Resource
    private ProjectPathUtil projectPathUtil;

    // 相对项目根目录的上传路径（uploads/course/）
    @Value("${course.cover.upload-relative-path}")
    private String uploadRelativePath;

    // 课程封面访问前缀（/course/）
    @Value("${course.cover.access-prefix}")
    private String accessPrefix;

    private static final String[] ALLOWED_TYPES = {"image/jpg", "image/jpeg", "image/png", "image/gif"};

    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        String contentType = file.getContentType();
        boolean isAllowed = false;
        for (String type : ALLOWED_TYPES) {
            if (Objects.equals(type, contentType)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            throw new RuntimeException("仅支持上传jpg/jpeg/png/gif格式的图片");
        }

        // 🌟 修改：课程封面允许最大 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new RuntimeException("文件大小不能超过5MB");
        }

        String projectRoot = projectPathUtil.getProjectRootPath();
        String realUploadPath = projectRoot + uploadRelativePath;

        File dir = new File(realUploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String ext = Objects.requireNonNull(originalFilename).substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + ext;

        File destFile = new File(realUploadPath + fileName);
        file.transferTo(destFile);

        return accessPrefix + fileName;
    }

    public String getRealUploadPath() {
        String projectRoot = projectPathUtil.getProjectRootPath();
        return projectRoot + uploadRelativePath;
    }
}