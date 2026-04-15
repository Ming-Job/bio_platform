package com.example.bio_platform.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.example.bio_platform.common.ExecutionResult;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Service
@Slf4j
public class DockerSandboxService {

    private DockerClient dockerClient;
    private final String workDir = "/tmp/sandbox";

    @Value("${sandbox.volume.path:D:/docker_share}")
    private String volumePath;

    @Value("${sandbox.python.container:python-sandbox}")
    private String pythonContainerName;

    @Value("${sandbox.r.container:r-sandbox}")
    private String rContainerName;

    @Value("${sandbox.plot.path:D:/docker_plots}")
    private String plotPath;

    // 🌟 新增：用户上传目录 和 系统内置案例目录
    @Value("${bio.file.upload.base-dir:D:/bio_uploads/files}")
    private String userUploadDir;

    @Value("${bio.file.system-cases.dir:D:/bio_uploads/system_cases}")
    private String systemCaseDir;

    // 记录连接状态
    private boolean dockerConnected = false;

    public DockerSandboxService() {
        log.info("DockerSandboxService 构造函数调用");
    }

    @PostConstruct
    public void init() {
        log.info("🔍 配置注入检查:");
        log.info("   Python容器名: {}", pythonContainerName);
        log.info("   R容器名: {}", rContainerName);
        log.info("   共享目录路径: {}", volumePath);

        log.info("初始化 DockerSandboxService...");
        this.dockerClient = createDockerClient();

        testDockerConnection();

        if (dockerConnected) {
            testContainerStatus();
        }

        // 自动创建基础数据目录
        try {
            Files.createDirectories(Paths.get(systemCaseDir));
            Files.createDirectories(Paths.get(userUploadDir));
            Files.createDirectories(Paths.get(volumePath));
        } catch (Exception e) {
            log.warn("⚠️ 无法创建基础数据目录: {}", e.getMessage());
        }
    }

    /**
     * 🌟 核心黑科技：在代码执行前，自动把需要的数据复制到沙箱共享目录中
     */
    private void prepareSandboxData(String datasetFileName) throws Exception {
        if (datasetFileName == null || datasetFileName.trim().isEmpty() || "null".equals(datasetFileName)) {
            return; // 无依赖数据，直接跳过
        }

        Path targetPath = Paths.get(volumePath, datasetFileName);

        // 如果共享池中已存在该数据（可能是上一次执行时挂载的），为了性能直接复用
        if (Files.exists(targetPath)) {
            log.info("📦 沙箱共享池中已存在数据集复用: {}", datasetFileName);
            return;
        }

        Path userFile = Paths.get(userUploadDir, datasetFileName);
        Path systemFile = Paths.get(systemCaseDir, datasetFileName);

        if (Files.exists(userFile)) {
            Files.copy(userFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ 已将 [用户私有数据] 挂载至沙箱: {}", datasetFileName);
        } else if (Files.exists(systemFile)) {
            Files.copy(systemFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ 已将 [系统云端数据] 静默克隆至沙箱: {}", datasetFileName);
        } else {
            log.error("❌ 数据舱异常：未在用户目录或系统目录中找到数据集 {}", datasetFileName);
            throw new FileNotFoundException("算力节点未能定位到依赖的数据集：" + datasetFileName);
        }
    }

    private DockerClient createDockerClient() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost("tcp://localhost:2375")
                    .withDockerTlsVerify(false)
                    .build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .build();

            return DockerClientImpl.getInstance(config, httpClient);
        } catch (Exception e) {
            log.error("创建 Docker 客户端失败: {}", e.getMessage());
            return null;
        }
    }

    public void testDockerConnection() {
        if (dockerClient == null) {
            log.error("❌ Docker 客户端未创建");
            dockerConnected = false;
            return;
        }

        try {
            String ping = String.valueOf(dockerClient.pingCmd().exec());
            log.info("✅ Docker 连接测试成功: {}", ping);

            Info info = dockerClient.infoCmd().exec();
            log.info("📊 Docker 信息:");
            log.info("   OS: {}", info.getOperatingSystem());
            log.info("   运行中容器: {}", info.getContainersRunning());

            dockerConnected = true;
        } catch (Exception e) {
            log.error("❌ Docker 连接测试失败: {}", e.getMessage());
            dockerConnected = false;
        }
    }

    private void testContainerStatus() {
        log.info("📦 检查沙箱容器状态...");
        checkContainerStatusSafe(pythonContainerName, "Python");
        checkContainerStatusSafe(rContainerName, "R");
    }

    private void checkContainerStatusSafe(String containerName, String containerType) {
        log.info("   --- 检查 {} 容器: {} ---", containerType, containerName);
        try {
            boolean foundByList = false;
            boolean isRunningByList = false;

            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (Container container : containers) {
                for (String name : container.getNames()) {
                    String cleanName = name.startsWith("/") ? name.substring(1) : name;
                    if (cleanName.equals(containerName)) {
                        foundByList = true;
                        isRunningByList = container.getState().equals("running");
                        break;
                    }
                }
                if (foundByList) break;
            }

            if (!foundByList) {
                log.warn("   ⚠️ 在容器列表中未找到 {} 容器: {}", containerType, containerName);
                return;
            }

            if (isRunningByList) {
                log.info("   ✅ {} 容器运行正常", containerType);
            } else {
                log.warn("   ⚠️ {} 容器已停止", containerType);
            }
        } catch (Exception e) {
            log.error("   ❌ 检查 {} 容器状态失败: {}", containerType, e.getMessage());
        }
    }

    public boolean isContainerRunning(String containerName) {
        if (!dockerConnected) return false;
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(false).exec();
            for (Container container : containers) {
                for (String name : container.getNames()) {
                    if ((name.startsWith("/") ? name.substring(1) : name).equals(containerName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getContainerInfoSafe(String containerName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", containerName);
        if (!dockerConnected) {
            info.put("error", "Docker 未连接");
            return info;
        }
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (Container container : containers) {
                for (String name : container.getNames()) {
                    if ((name.startsWith("/") ? name.substring(1) : name).equals(containerName)) {
                        info.put("id", container.getId().substring(0, 12));
                        info.put("running", container.getState().equals("running"));
                        info.put("status", container.getStatus());
                        return info;
                    }
                }
            }
            info.put("error", "容器不存在");
            info.put("exists", false);
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return info;
    }

    public boolean isDockerConnected() {
        return dockerConnected;
    }

    public Map<String, Object> getDockerHealthReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dockerConnected", dockerConnected);
        if (dockerConnected) {
            try {
                Map<String, Object> pythonInfo = getContainerInfoSafe(pythonContainerName);
                Map<String, Object> rInfo = getContainerInfoSafe(rContainerName);
                boolean pythonHealthy = Boolean.TRUE.equals(pythonInfo.get("running"));
                boolean rHealthy = Boolean.TRUE.equals(rInfo.get("running"));
                report.put("status", pythonHealthy && rHealthy ? "HEALTHY" : "DEGRADED");
            } catch (Exception e) {
                report.put("status", "ERROR");
            }
        }
        return report;
    }

    public List<Map<String, Object>> getAllContainers() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!dockerConnected) return result;
        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (Container container : containers) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", container.getId().substring(0, 12));
                info.put("names", container.getNames());
                info.put("status", container.getStatus());
                result.add(info);
            }
        } catch (Exception e) { }
        return result;
    }

    public boolean startContainer(String containerName) {
        if (!dockerConnected) return false;
        try {
            dockerClient.startContainerCmd(containerName).exec();
            Thread.sleep(2000);
            return isContainerRunning(containerName);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stopContainer(String containerName) {
        if (!dockerConnected) return false;
        try {
            dockerClient.stopContainerCmd(containerName).exec();
            Thread.sleep(2000);
            return !isContainerRunning(containerName);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在 Python 容器中执行代码 (升级版：支持自动挂载数据)
     */
    public ExecutionResult executePython(String code, String taskId, String datasetFileName) {
        ExecutionResult result = new ExecutionResult();
        result.setTaskId(taskId);
        result.setLanguage("python");
        result.setCode(code);
        result.setEnvironment("python:3.9-slim (read-only, no network)");
        result.setStartTime(System.currentTimeMillis());
        result.setStatus("running");

        log.info("开始执行 Python 代码，任务ID: {}, 挂载数据: {}", taskId, datasetFileName);

        try {
            // 🌟 在向沙箱写入代码之前，自动准备好数据集！
            prepareSandboxData(datasetFileName);

            // 1. 将代码写入临时文件
            String fileName = taskId + ".py";
            Path codePath = Paths.get(volumePath, fileName);

            log.info("将代码写入文件: {}", codePath);
            Files.write(codePath, code.getBytes(StandardCharsets.UTF_8));

            // 2. 在容器中执行代码
            String[] command = {"python", "/tmp/sandbox/" + fileName};

            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(pythonContainerName)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .withEnv(Collections.singletonList("TASK_ID=" + taskId))
                    .exec();

            String execId = execCreate.getId();

            // 3. 执行并获取输出
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execId)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            byte[] payload = frame.getPayload();
                            if (payload != null) {
                                try {
                                    if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDOUT) {
                                        stdout.write(payload);
                                    } else if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDERR) {
                                        stderr.write(payload);
                                    }
                                } catch (Exception e) {
                                    log.error("处理输出流失败", e);
                                }
                            }
                        }
                    })
                    .awaitCompletion(30, TimeUnit.SECONDS); // 30秒超时

            // 4. 检查执行状态
            InspectExecResponse inspect = dockerClient.inspectExecCmd(execId).exec();
            Integer exitCode = inspect.getExitCodeLong() != null ?
                    inspect.getExitCodeLong().intValue() : null;

            // 5. 构建结果
            String output = stdout.toString(StandardCharsets.UTF_8);
            String error = stderr.toString(StandardCharsets.UTF_8);

            result.setOutput(output);
            result.setError(error);
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();

            if (exitCode == null || exitCode != 0) {
                result.setStatus("error");
                result.setMessage("代码执行失败，退出码: " + exitCode);
            } else {
                result.setStatus("completed");
                result.setMessage("代码执行成功");

                // 检查是否有图片输出
                checkForGeneratedImages(result, taskId);
            }

            // 6. 清理临时文件
            try {
                Files.deleteIfExists(codePath);
                log.info("清理临时文件: {}", codePath);
            } catch (Exception e) {
                log.warn("清理临时文件失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("执行 Python 代码失败", e);
            result.setStatus("error");
            result.setError("执行过程发生异常: " + e.getMessage());
            result.setMessage("执行过程发生异常");
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();
        }

        return result;
    }

    /**
     * 在 R 容器中执行代码 (升级版：支持自动挂载数据)
     */
    public ExecutionResult executeR(String code, String taskId, String datasetFileName) {
        ExecutionResult result = new ExecutionResult();
        result.setTaskId(taskId);
        result.setLanguage("r");
        result.setCode(code);
        result.setEnvironment("rocker/r-base:latest (read-only, no network)");
        result.setStartTime(System.currentTimeMillis());
        result.setStatus("running");

        log.info("开始执行 R 代码，任务ID: {}, 挂载数据: {}", taskId, datasetFileName);

        try {
            // 🌟 在向沙箱写入代码之前，自动准备好数据集！
            prepareSandboxData(datasetFileName);

            // 1. 将代码写入临时文件
            String fileName = taskId + ".R";
            Path codePath = Paths.get(volumePath, fileName);

            log.info("将代码写入文件: {}", codePath);
            Files.write(codePath, code.getBytes(StandardCharsets.UTF_8));

            // 2. 在容器中执行代码
            String[] command = {"Rscript", "/tmp/sandbox/" + fileName};

            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(rContainerName)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .withEnv(Collections.singletonList("TASK_ID=" + taskId))
                    .exec();

            String execId = execCreate.getId();

            // 3. 执行并获取输出
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execId)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            byte[] payload = frame.getPayload();
                            if (payload != null) {
                                try {
                                    if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDOUT) {
                                        stdout.write(payload);
                                    } else if (frame.getStreamType() == com.github.dockerjava.api.model.StreamType.STDERR) {
                                        stderr.write(payload);
                                    }
                                } catch (Exception e) {
                                    log.error("处理输出流失败", e);
                                }
                            }
                        }
                    })
                    .awaitCompletion(30, TimeUnit.SECONDS);

            // 4. 检查执行状态
            InspectExecResponse inspect = dockerClient.inspectExecCmd(execId).exec();
            Integer exitCode = inspect.getExitCodeLong() != null ?
                    inspect.getExitCodeLong().intValue() : null;

            // 5. 构建结果
            String output = stdout.toString(StandardCharsets.UTF_8);
            String error = stderr.toString(StandardCharsets.UTF_8);

            result.setOutput(output);
            result.setError(error);
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();

            if (exitCode == null || exitCode != 0) {
                result.setStatus("error");
                result.setMessage("代码执行失败，退出码: " + exitCode);
            } else {
                result.setStatus("completed");
                result.setMessage("代码执行成功");

                // 检查是否有图片输出
                checkForGeneratedImages(result, taskId);
            }

            // 6. 清理临时文件
            try {
                Files.deleteIfExists(codePath);
                log.info("清理临时文件: {}", codePath);
            } catch (Exception e) {
                log.warn("清理临时文件失败: {}", e.getMessage());
            }

        } catch (Exception e) {
            log.error("执行 R 代码失败", e);
            result.setStatus("error");
            result.setError("执行过程发生异常: " + e.getMessage());
            result.setMessage("执行过程发生异常");
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();
        }

        return result;
    }

    /**
     * 检查并抓取生成的图片文件（支持模糊匹配扫描）
     */
    private void checkForGeneratedImages(ExecutionResult result, String taskId) {
        try {
            List<String> images = new ArrayList<>();
            Path sharedDir = Paths.get(volumePath);
            Path plotDir = Paths.get(plotPath);
            List<Path> searchDirs = Arrays.asList(sharedDir, plotDir);

            for (Path searchDir : searchDirs) {
                if (!Files.exists(searchDir)) continue;

                // 遍历目录下所有文件，只要文件名包含 taskId 并且是图片，统统抓走！
                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(searchDir)) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString().toLowerCase();

                        // 只要文件名里包含这个 taskId，并且后缀是图片格式
                        if (fileName.contains(taskId.toLowerCase()) &&
                                (fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
                                        fileName.endsWith(".jpeg") || fileName.endsWith(".svg") ||
                                        fileName.endsWith(".pdf"))) {

                            log.info("✅ 雷达成功捕捉到图片: {}", file);

                            try {
                                byte[] imageBytes = Files.readAllBytes(file);
                                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                                // 根据后缀名动态判断 MIME 类型
                                String mimeType = fileName.endsWith(".svg") ? "svg+xml" :
                                        fileName.endsWith(".pdf") ? "pdf" :
                                                fileName.substring(fileName.lastIndexOf(".") + 1);

                                images.add("data:image/" + mimeType + ";base64," + base64Image);

                                // 阅后即焚，保持沙箱干净
                                Files.deleteIfExists(file);
                            } catch (Exception e) {
                                log.warn("处理图片文件 {} 失败: {}", file, e.getMessage());
                            }
                        }
                    }
                }
            }

            if (!images.isEmpty()) {
                result.setImages(images);
                result.setHasVisualization(true);
                log.info("📊 成功添加 {} 张图片到任务结果", images.size());
            } else {
                result.setHasVisualization(false);
                log.info("ℹ️ 未找到包含任务ID {} 的图片文件", taskId);
            }
        } catch (Exception e) {
            log.warn("检查生成图片时发生异常: {}", e.getMessage());
            result.setHasVisualization(false);
        }
    }
}