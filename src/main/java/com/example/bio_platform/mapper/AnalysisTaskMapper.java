package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.AnalysisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface AnalysisTaskMapper extends BaseMapper<AnalysisTask> {
    /**
     * 🌟 核心提速 SQL：按项目分组，一次性统计出该用户所有项目的任务总数
     */
    @Select("SELECT project_id, COUNT(1) AS task_count " +
            "FROM analysis_task " +
            "WHERE user_id = #{userId} AND project_id IS NOT NULL " +
            "GROUP BY project_id")
    List<Map<String, Object>> countTasksGroupByProject(@Param("userId") Long userId);
}