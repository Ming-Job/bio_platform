package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.FileUpload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface FileUploadMapper extends BaseMapper<FileUpload> {

    /**
     * 根据上传会话ID查找
     */
    @Select("SELECT * FROM bio_file_uploads WHERE upload_session = #{uploadSession}")
    FileUpload selectBySession(@Param("uploadSession") String uploadSession);

    /**
     * 更新上传进度
     */
    @Update("UPDATE bio_file_uploads SET uploaded_chunks = #{uploadedChunks}, bytes_uploaded = #{bytesUploaded}, " +
            "progress_percent = #{progressPercent}, update_time = #{updateTime} WHERE upload_session = #{uploadSession}")
    int updateProgress(@Param("uploadSession") String uploadSession,
                       @Param("uploadedChunks") Integer uploadedChunks,
                       @Param("bytesUploaded") Long bytesUploaded,
                       @Param("progressPercent") Double progressPercent,
                       @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新上传状态
     */
    @Update("UPDATE bio_file_uploads SET status = #{status}, error_message = #{errorMessage}, " +
            "end_time = #{endTime} WHERE upload_session = #{uploadSession}")
    int updateStatus(@Param("uploadSession") String uploadSession,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage,
                     @Param("endTime") LocalDateTime endTime);

    /**
     * 清理过期的上传记录（状态为cancelled或超过24小时未完成的）
     */
    @Update("DELETE FROM bio_file_uploads WHERE (status IN ('cancelled', 'failed') AND end_time < #{expireTime}) " +
            "OR (status IN ('initializing', 'uploading') AND start_time < #{expireTime})")
    int cleanExpiredRecords(@Param("expireTime") LocalDateTime expireTime);
}