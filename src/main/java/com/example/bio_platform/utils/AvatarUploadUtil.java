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
 * 头像上传工具类
 */
@Component
public class AvatarUploadUtil {

    @Resource
    private ProjectPathUtil projectPathUtil;

    // 相对项目根目录的上传路径（uploads/avatar/）
    @Value("${avatar.upload-relative-path}")
    private String uploadRelativePath;

    // 头像访问前缀（/avatar/）
    @Value("${avatar.access-prefix}")
    private String accessPrefix;

    // 允许的图片类型
    private static final String[] ALLOWED_TYPES = {"image/jpg", "image/jpeg", "image/png", "image/gif"};

    /**
     * 上传头像文件
     * @return 数据库存储路径：/avatar/xxx.jpg
     */
    public String upload(MultipartFile file) throws IOException {
        // 1. 空文件校验
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        // 2. 文件类型校验
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

        // 3. 文件大小校验（2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new RuntimeException("文件大小不能超过2MB");
        }

        // 4. 动态拼接真实存储路径：项目根目录 + uploads/avatar/
        String projectRoot = projectPathUtil.getProjectRootPath();
        String realUploadPath = projectRoot + uploadRelativePath;

        // 5. 创建上传目录（不存在则自动创建）
        File dir = new File(realUploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 6. 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String ext = Objects.requireNonNull(originalFilename).substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + ext;

        // 7. 保存文件
        File destFile = new File(realUploadPath + fileName);
        file.transferTo(destFile);

        // 8. 返回数据库存储路径
        return accessPrefix + fileName;
    }

    /**
     * 获取真实存储路径（供静态资源映射使用）
     */
    public String getRealUploadPath() {
        String projectRoot = projectPathUtil.getProjectRootPath();
        return projectRoot + uploadRelativePath;
    }
}