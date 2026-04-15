package com.example.bio_platform.controller;

import com.example.bio_platform.common.Result;
import com.example.bio_platform.entity.Project;
import com.example.bio_platform.service.ProjectService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

//    /**
//     * 获取用户项目列表 (带统计信息)
//     */
//    @GetMapping("/user/{userId}")
//    public Result<List<Project>> getUserProjects(@PathVariable Long userId) {
//        List<Project> projects = projectService.getUserProjectsWithStats(userId);
//        return Result.success(projects);
//    }

    /**
     * 🌟 获取用户的科研空间列表 (包含任务数、文件数等统计信息)
     * 对应前端 getUserProjects(userId)
     */
    @GetMapping("/user/{userId}")
    @ApiOperation("获取用户的科研空间列表(含大盘统计数据)")
    public ResponseEntity<Map<String, Object>> getUserProjects(@PathVariable Long userId) {


        try {
            // 直接调用 Service 层那个已经“缝合”好所有统计数据的超级方法
            List<Project> projects = projectService.getUserProjects(userId);

            // 包装成前端 Vue 期望的 { data: [...] } 格式
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", projects,
                    "message", "科研空间大盘数据拉取成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "大盘数据获取失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 创建新项目
     */
    @PostMapping
    public Result<Boolean> createProject(@RequestBody Project project) {
        // ID 自动生成，时间和创建人等由前端或 Mybatis-plus 拦截器填充
        boolean success = projectService.save(project);
        return success ? Result.success(true) : Result.error("创建失败");
    }

    /**
     * 更新项目信息 (名称、描述)
     */
    @PutMapping("/{id}")
    public Result<Boolean> updateProject(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        boolean success = projectService.updateById(project);
        return success ? Result.success(true) : Result.error("更新失败");
    }

    /**
     * 删除/销毁项目
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteProject(@PathVariable Long id) {
        // 注意：物理删除。如果在企业级项目中通常会做逻辑删除，或连带删除所属的文件。
        // 毕设中直接 removeById 即可。
        boolean success = projectService.removeById(id);
        return success ? Result.success(true) : Result.error("销毁失败");
    }

    /**
     * 归档/取消归档
     */
    @PutMapping("/{id}/archive")
    public Result<Boolean> toggleArchive(@PathVariable Long id, @RequestParam Boolean isArchived) {
        boolean success = projectService.toggleArchive(id, isArchived);
        return success ? Result.success(true) : Result.error("操作失败");
    }


}