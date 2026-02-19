package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.Project;

import java.util.List;
import java.util.Map;

public interface ProjectService extends IService<Project> {

    /**
     * 创建项目
     */
    Project createProject(String name, String description, Long userId);

    /**
     * 获取用户项目列表（带统计）
     */
    List<Map<String, Object>> getUserProjectsWithStats(Long userId);

    /**
     * 获取项目详情（带文件列表）
     */
    Map<String, Object> getProjectDetail(Long projectId, Long userId);

    /**
     * 删除项目（同时删除项目中的文件）
     */
    boolean deleteProject(Long projectId, Long userId);

    /**
     * 归档项目
     */
    boolean archiveProject(Long projectId, Long userId, boolean archive);
}