package com.example.bio_platform.listener;

import com.example.bio_platform.config.FileUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadDirInitRunner implements CommandLineRunner {

    private final FileUploadProperties fileUploadProperties;

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("开始初始化上传目录...");

            // 1. 检查配置是否注入
            if (fileUploadProperties == null) {
                log.error("FileUploadProperties 配置未注入！");
                return;
            }

            // 2. 使用默认值或配置文件中的值
            String baseDir = fileUploadProperties.getBaseDir() != null
                    ? fileUploadProperties.getBaseDir()
                    : "./bio_uploads/files";

            String chunkTempDir = fileUploadProperties.getChunkTempDir() != null
                    ? fileUploadProperties.getChunkTempDir()
                    : "./bio_uploads/temp";

            log.info("baseDir: {}", baseDir);
            log.info("chunkTempDir: {}", chunkTempDir);

            // 3. 创建文件存储目录
            Path baseDirPath = Paths.get(baseDir);
            if (!Files.exists(baseDirPath)) {
                Files.createDirectories(baseDirPath);
                log.info("创建文件存储目录: {}", baseDirPath.toAbsolutePath());
            }

            // 4. 创建分片临时目录
            Path chunkTempDirPath = Paths.get(chunkTempDir);
            if (!Files.exists(chunkTempDirPath)) {
                Files.createDirectories(chunkTempDirPath);
                log.info("创建分片临时目录: {}", chunkTempDirPath.toAbsolutePath());
            }

            // 5. 创建小文件上传目录（原有功能保持）
            Path uploadDir = Paths.get("uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("创建上传目录: {}", uploadDir.toAbsolutePath());
            }

            Path avatarDir = Paths.get("uploads/avatar");
            if (!Files.exists(avatarDir)) {
                Files.createDirectories(avatarDir);
                log.info("创建头像目录: {}", avatarDir.toAbsolutePath());
            }

            log.info("上传目录初始化完成！");

        } catch (Exception e) {
            log.error("初始化上传目录失败", e);
            // 这里可以选择不抛出异常，让应用继续启动
            // 因为目录创建失败可能不是致命错误
        }
    }
}