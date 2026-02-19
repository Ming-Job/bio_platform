package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.FileUpload;

import java.util.List;

public interface FileUploadService extends IService<FileUpload> {

    /**
     * 清理过期的上传记录
     */
    int cleanExpiredUploads();

    /**
     * 获取用户的上传记录
     */
    List<FileUpload> getUserUploads(Long userId, Integer days);
}