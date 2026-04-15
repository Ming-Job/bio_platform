package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.Project;

import java.util.List;

public interface ProjectService extends IService<Project> {

    List<Project> getUserProjects(Long userId);

    /**
     * 获取用户的项目列表，并动态计算每个项目下的文件数、总大小等统计信息
     */
    List<Project> getUserProjectsWithStats(Long userId);

    /**
     * 切换项目的归档状态
     */
    boolean toggleArchive(Long projectId, Boolean isArchived);
}