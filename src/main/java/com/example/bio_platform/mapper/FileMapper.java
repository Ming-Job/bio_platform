package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface FileMapper extends BaseMapper<File> {

    /**
     * 根据MD5和用户ID查找文件（用于当前用户去重）
     */
    @Select("SELECT * FROM bio_files WHERE md5_hash = #{md5Hash} AND user_id = #{userId} AND status != 'deleted'")
    File selectByMd5AndUserId(@Param("md5Hash") String md5Hash, @Param("userId") Long userId);

    /**
     * 获取用户文件数量统计
     */
    @Select("SELECT file_type, COUNT(*) as count FROM bio_files WHERE user_id = #{userId} AND status != 'deleted' GROUP BY file_type")
    List<Map<String, Object>> countByFileType(@Param("userId") Long userId);

    /**
     * 获取用户总存储空间使用量
     */
    @Select("SELECT COALESCE(SUM(size_bytes), 0) FROM bio_files WHERE user_id = #{userId} AND status != 'deleted'")
    Long selectTotalStorageByUserId(@Param("userId") Long userId);


    /**
     * 获取项目文件列表（带分页信息）
     */
    @Select("SELECT * FROM bio_files WHERE project_id = #{projectId} AND status != 'deleted' ORDER BY upload_time DESC")
    List<File> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据文件ID和用户ID查询文件信息（用于权限验证及获取文件元数据）
     * 仅返回未被删除的记录
     */
    @Select("SELECT * FROM bio_files WHERE id = #{id} AND user_id = #{userId} AND status != 'deleted'")
    File selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT project_id, COUNT(1) AS file_count, SUM(size_bytes) AS total_size " +
            "FROM bio_files " +
            "WHERE user_id = #{userId} AND project_id IS NOT NULL " +
            "GROUP BY project_id")
    List<Map<String, Object>> countFileStatsGroupByProject(@Param("userId") Long userId);

}