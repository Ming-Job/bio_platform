package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.entity.Project;
import com.example.bio_platform.mapper.FileMapper;
import com.example.bio_platform.mapper.ProjectMapper;
import com.example.bio_platform.service.AnalysisTaskService;
import com.example.bio_platform.service.FileService;
import com.example.bio_platform.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final FileMapper fileMapper;

    @Autowired
    private AnalysisTaskService analysisTaskService; // 注入任务服务

    @Autowired
    private FileService fileService; // 🌟 注入文件服务

    @Override
    public List<Project> getUserProjects(Long userId) {
        // 1. 查出该用户所有的科研空间
        List<Project> projects = this.list(new QueryWrapper<Project>()
                .eq("user_id", userId)
                .orderByDesc("updated_at")); // 按最新更新时间排序

        if (projects.isEmpty()) {
            return projects;
        }

        // 🌟 2. 批量拉取统计数据 (拒绝 for 循环查库，性能拉满！)
        // 拉取任务分布
        Map<Long, Integer> taskCountMap = analysisTaskService.getProjectTaskCountMap(userId);
        // 拉取文件分布
        Map<Long, Map<String, Number>> fileStatsMap = fileService.getProjectFileStatsMap(userId);

        // 🌟 3. 在内存中进行终极大缝合
        for (Project project : projects) {
            Long pid = project.getId();

            // 缝合任务数
            project.setAnalysisTaskCount(taskCountMap.getOrDefault(pid, 0));

            // 缝合文件数和总存储量
            Map<String, Number> fStats = fileStatsMap.getOrDefault(pid, Collections.emptyMap());
            project.setFileCount(fStats.getOrDefault("fileCount", 0).intValue());
            project.setTotalFileSizeBytes(fStats.getOrDefault("totalSize", 0L).longValue());
        }

        return projects;
    }
    @Override
    public List<Project> getUserProjectsWithStats(Long userId) {
        // 1. 查出该用户的所有项目，按更新时间降序排列
        LambdaQueryWrapper<Project> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Project::getUserId, userId)
                .orderByDesc(Project::getUpdatedAt);

        List<Project> projects = this.list(queryWrapper);

        // 2. 🌟 毕设亮点：动态装配统计数据
        for (Project project : projects) {
            // 查询该项目下所有未删除的文件
            LambdaQueryWrapper<File> fileQuery = new LambdaQueryWrapper<>();
            fileQuery.eq(File::getProjectId, project.getId())
                    .ne(File::getStatus, "deleted");

            List<File> projectFiles = fileMapper.selectList(fileQuery);

            // 统计文件数量
            project.setFileCount(projectFiles.size());

            // 统计文件总大小
            long totalSize = 0L;
            for (File file : projectFiles) {
                if (file.getSizeBytes() != null) {
                    totalSize += file.getSizeBytes();
                }
            }
            project.setTotalFileSizeBytes(totalSize);
            project.setFormattedTotalFileSize(formatFileSize(totalSize));
        }

        return projects;
    }

    @Override
    public boolean toggleArchive(Long projectId, Boolean isArchived) {
        Project project = new Project();
        project.setId(projectId);
        project.setIsArchived(isArchived);
        return this.updateById(project);
    }

    // 辅助方法：将字节转换为人类可读的格式 (复用你之前的逻辑)
    private String formatFileSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes == 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        return String.format("%.2f %s", sizeBytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}