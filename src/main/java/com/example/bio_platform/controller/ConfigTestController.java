package com.example.bio_platform.controller;

import com.example.bio_platform.config.FileUploadProperties;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Api(tags = "配置测试接口")
public class ConfigTestController {

    private final FileUploadProperties fileUploadProperties;

    @GetMapping("/file-upload")
    @ApiOperation("查看文件上传配置")
    public Object getFileUploadConfig() {
        return fileUploadProperties;
    }

    @GetMapping("/check")
    @ApiOperation("检查配置注入状态")
    public String checkConfig() {
        if (fileUploadProperties == null) {
            return "❌ FileUploadProperties 配置未注入！";
        }

        if (fileUploadProperties.getBaseDir() == null) {
            return "❌ 基础目录配置为空";
        }

        return String.format("✅ 配置已正确注入\n" +
                        "基础目录: %s\n" +
                        "临时目录: %s\n" +
                        "允许扩展名: %s\n" +
                        "最大文件大小: %d bytes",
                fileUploadProperties.getBaseDir(),
                fileUploadProperties.getChunkTempDir(),
                fileUploadProperties.getAllowedExtensions(),
                fileUploadProperties.getMaxFileSize());
    }
}