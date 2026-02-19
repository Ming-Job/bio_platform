package com.example.bio_platform.controller;

import com.example.bio_platform.service.DockerSandboxService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Api(tags = "docker管理接口")
@RequestMapping("/api/docker")
@Slf4j
public class DockerController {

    @Autowired
    private DockerSandboxService dockerSandboxService;

    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        log.info("获取 Docker 健康报告");
        Map<String, Object> healthReport = dockerSandboxService.getDockerHealthReport();
        log.info("健康报告: {}", healthReport);
        return healthReport;
    }

    @GetMapping("/containers")
    public Map<String, Object> getAllContainers() {
        Map<String, Object> response = new HashMap<>();
        response.put("containers", dockerSandboxService.getAllContainers());
        response.put("timestamp", new java.util.Date());
        return response;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("dockerConnected", dockerSandboxService.isDockerConnected());

        if (dockerSandboxService.isDockerConnected()) {
            status.put("pythonContainerRunning", dockerSandboxService.isContainerRunning("python-sandbox"));
            status.put("rContainerRunning", dockerSandboxService.isContainerRunning("r-sandbox"));

            // 获取容器信息
            status.put("pythonInfo", dockerSandboxService.getContainerInfoSafe("python-sandbox"));
            status.put("rInfo", dockerSandboxService.getContainerInfoSafe("r-sandbox"));
        }

        status.put("timestamp", new java.util.Date());

        return status;
    }

    @PostMapping("/python/start")
    public Map<String, Object> startPythonContainer() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dockerSandboxService.startContainer("python-sandbox");
            result.put("success", success);
            result.put("message", success ? "Python容器启动成功" : "Python容器启动失败");
            result.put("container", "python-sandbox");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "启动Python容器时出错: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/r/start")
    public Map<String, Object> startRContainer() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dockerSandboxService.startContainer("r-sandbox");
            result.put("success", success);
            result.put("message", success ? "R容器启动成功" : "R容器启动失败");
            result.put("container", "r-sandbox");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "启动R容器时出错: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/python/stop")
    public Map<String, Object> stopPythonContainer() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dockerSandboxService.stopContainer("python-sandbox");
            result.put("success", success);
            result.put("message", success ? "Python容器停止成功" : "Python容器停止失败");
            result.put("container", "python-sandbox");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "停止Python容器时出错: " + e.getMessage());
        }
        return result;
    }

    @PostMapping("/r/stop")
    public Map<String, Object> stopRContainer() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dockerSandboxService.stopContainer("r-sandbox");
            result.put("success", success);
            result.put("message", success ? "R容器停止成功" : "R容器停止失败");
            result.put("container", "r-sandbox");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "停止R容器时出错: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "Docker沙箱服务测试");
        result.put("timestamp", new java.util.Date());
        result.put("dockerConnected", dockerSandboxService.isDockerConnected());
        return result;
    }
}