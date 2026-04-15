package com.example.bio_platform.service;

import org.springframework.core.io.Resource;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.entity.FileUpload;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface FileService extends IService<File> {

    /**
     * 上传单个文件
     */
    FileUpload uploadFile(MultipartFile multipartFile, Long userId, Long projectId,
                          String description, Map<String, Object> tags);

    /**
     * 上传多个文件
     */
    List<FileUpload> uploadFiles(List<MultipartFile> files, Long userId, Long projectId, String description);

    /**
     * 获取用户文件列表
     */
    List<File> getUserFiles(Long userId, Long projectId, String fileType, String status);

    /**
     * 获取文件详情
     */
    File getFileDetail(Long fileId, Long userId);

    /**
     * 删除文件（软删除）
     */
    boolean softDeleteFile(Long fileId, Long userId);

    /**
     * 获取文件统计信息
     */
    Map<String, Object> getFileStats(Long userId);


    /**
     *  获取文件预览内容 （截取前100行，支持 .gz 自取）
     *  @param fileId 文件ID
     *  @param userId 用户ID
     *  @return 文件文本内容
     * */
    String getFilePreviewContent(Long fileId, Long userId);


    /**
     * 获取最近上传的文件
     * @param userId 用户ID
     * @param limit 返回数量限制
     * @param projectId 项目ID（可选）
     * @return 最近上传的文件列表
     */
    List<File> getRecentlyUploadedFiles(Long userId, int limit, Long projectId);


    /**
     * 下载文件 (流式传输，防 OOM)
     */
    ResponseEntity<Resource> downloadFile(Long fileId, Long userId);

    // 🌟 新增：获取用户的所有文件（包含上传的和分析产出的）
    List<File> getAllUserFiles(Long userId, Long projectId, String fileType, String status);


    Map<Long, Map<String, Number>> getProjectFileStatsMap(Long userId);
}