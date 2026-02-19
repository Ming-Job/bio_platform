package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 获取用户项目列表（带文件统计）
     */
    @Select("SELECT p.*, " +
            "COUNT(f.id) as file_count, " +
            "COALESCE(SUM(f.size_bytes), 0) as total_size " +
            "FROM project p " +
            "LEFT JOIN bio_files f ON p.id = f.project_id AND f.status != 'deleted' " +
            "WHERE p.user_id = #{userId} " +
            "GROUP BY p.id " +
            "ORDER BY p.created_at DESC")
    List<Map<String, Object>> selectProjectsWithStats(@Param("userId") Long userId);

    /**
     * 获取项目详情（包含统计信息）
     */
    @Select("SELECT p.*, " +
            "COUNT(f.id) as file_count, " +
            "COALESCE(SUM(f.size_bytes), 0) as total_size, " +
            "MAX(f.upload_time) as last_upload_time " +
            "FROM project p " +
            "LEFT JOIN bio_files f ON p.id = f.project_id AND f.status != 'deleted' " +
            "WHERE p.id = #{projectId} AND p.user_id = #{userId} " +
            "GROUP BY p.id")
    Map<String, Object> selectProjectDetail(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /**
     * 检查项目是否属于用户
     */
    @Select("SELECT COUNT(*) FROM project WHERE id = #{projectId} AND user_id = #{userId}")
    int checkProjectOwnership(@Param("projectId") Long projectId, @Param("userId") Long userId);
}