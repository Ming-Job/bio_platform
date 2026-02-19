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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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


    // 记录连接状态
    private boolean dockerConnected = false;

    public DockerSandboxService() {
        log.info("DockerSandboxService 构造函数调用");
    }

    /**
     * 初始化方法 - 在依赖注入完成后调用
     */
    @PostConstruct
    public void init() {
        log.info("🔍 配置注入检查:");
        log.info("   Python容器名: {}", pythonContainerName);
        log.info("   R容器名: {}", rContainerName);
        log.info("   共享目录路径: {}", volumePath);

        log.info("初始化 DockerSandboxService...");
        this.dockerClient = createDockerClient();

        // 测试连接
        testDockerConnection();

        // 如果连接成功，测试容器状态
        if (dockerConnected) {
            testContainerStatus();
        }
    }

    /**
     * 创建 Docker 客户端
     */
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

    /**
     * 测试 Docker 连接
     */
    public void testDockerConnection() {
        if (dockerClient == null) {
            log.error("❌ Docker 客户端未创建");
            dockerConnected = false;
            return;
        }

        try {
            // 测试连接
            String ping = String.valueOf(dockerClient.pingCmd().exec());
            log.info("✅ Docker 连接测试成功: {}", ping);

            // 获取 Docker 信息
            Info info = dockerClient.infoCmd().exec();
            log.info("📊 Docker 信息:");
            log.info("   OS: {}", info.getOperatingSystem());
            log.info("   容器总数: {}", info.getContainers());
            log.info("   运行中容器: {}", info.getContainersRunning());
            log.info("   停止的容器: {}", info.getContainersStopped());
            log.info("   镜像数量: {}", info.getImages());
            log.info("   Docker 版本: {}", info.getServerVersion());
            log.info("   内存总量: {} MB", info.getMemTotal() / (1024 * 1024));

            dockerConnected = true;

        } catch (Exception e) {
            log.error("❌ Docker 连接测试失败: {}", e.getMessage());
            log.info("💡 请检查:");
            log.info("   1. Docker Desktop 是否正在运行");
            log.info("   2. 是否在 Docker Desktop 设置中启用了 TCP 2375 端口");
            log.info("   3. 运行配置中是否设置了环境变量: DOCKER_HOST=tcp://localhost:2375");
            dockerConnected = false;
        }
    }

    /**
     * 测试容器状态 - 修复版本
     */
    private void testContainerStatus() {
        log.info("📦 检查沙箱容器状态...");

        // 使用安全的方式检查 Python 容器
        checkContainerStatusSafe(pythonContainerName, "Python");

        // 使用安全的方式检查 R 容器
        checkContainerStatusSafe(rContainerName, "R");
    }

    /**
     * 安全地检查容器状态 - 避免 JSON 解析错误
     */
    private void checkContainerStatusSafe(String containerName, String containerType) {
        log.info("   --- 检查 {} 容器: {} ---", containerType, containerName);

        try {
            // 方法1: 首先通过 listContainersCmd 检查
            boolean foundByList = false;
            boolean isRunningByList = false;

            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            for (Container container : containers) {
                String[] names = container.getNames();
                for (String name : names) {
                    String cleanName = name.startsWith("/") ? name.substring(1) : name;
                    if (cleanName.equals(containerName)) {
                        foundByList = true;
                        isRunningByList = container.getState().equals("running");

                        log.info("   ✅ 通过 listContainersCmd 找到 {} 容器", containerType);
                        log.info("      容器ID: {}", container.getId().substring(0, 12));
                        log.info("      状态: {}", container.getStatus());
                        log.info("      运行状态: {}", isRunningByList ? "运行中" : "已停止");
                        log.info("      镜像: {}", container.getImage());

                        break;
                    }
                }
                if (foundByList) break;
            }

            if (!foundByList) {
                log.warn("   ⚠️ 在容器列表中未找到 {} 容器: {}", containerType, containerName);
                return;
            }

            // 如果容器正在运行，尝试获取更多信息（使用安全的 inspect）
            if (isRunningByList) {
                log.info("   🔍 尝试获取 {} 容器的详细信息...", containerType);
                try {
                    // 使用 try-catch 包装 inspect，防止 JSON 解析错误
                    InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerName).exec();

                    // 安全地获取信息，避免访问可能引起解析错误的字段
                    try {
                        String containerId = inspect.getId();
                        log.info("      容器ID: {}", containerId.substring(0, 12));
                        log.info("      创建时间: {}", inspect.getCreated());

                        // 检查健康状态
                        if (inspect.getState().getHealth() != null) {
                            log.info("      健康状态: {}", inspect.getState().getHealth().getStatus());
                        }

                        // 检查网络模式 - 使用安全的方法
                        try {
                            String networkMode = inspect.getHostConfig().getNetworkMode();
                            log.info("      网络模式: {}", networkMode);
                        } catch (Exception e) {
                            log.info("      网络模式: 无法获取（解析错误）");
                        }

                        log.info("   ✅ {} 容器运行正常", containerType);

                    } catch (Exception e) {
                        log.info("   ✅ {} 容器运行正常（部分信息获取失败，但不影响运行）", containerType);
                    }

                } catch (com.github.dockerjava.api.exception.NotFoundException e) {
                    log.error("   ❌ {} 容器 '{}' 不存在", containerType, containerName);
                } catch (Exception e) {
                    // 这里是关键的修复：即使 inspect 失败，只要 list 显示容器在运行，就认为容器是正常的
                    if (e.getMessage().contains("Error parsing Bind") ||
                            e.getMessage().contains("ValueInstantiationException") ||
                            e.getMessage().contains("Binds")) {
                        log.info("   ✅ {} 容器运行正常（Windows路径解析问题，不影响容器功能）", containerType);
                        log.debug("   JSON解析警告: {}", e.getMessage());
                    } else {
                        log.error("   ❌ 检查 {} 容器失败: {}", containerType, e.getMessage());
                    }
                }
            } else {
                log.warn("   ⚠️ {} 容器已停止", containerType);
            }

        } catch (Exception e) {
            log.error("   ❌ 检查 {} 容器状态失败: {}", containerType, e.getMessage());
        }
    }

    /**
     * 检查容器是否运行 - 修复版本，避免 inspect 的 JSON 解析问题
     */
    public boolean isContainerRunning(String containerName) {
        if (!dockerConnected) {
            return false;
        }

        try {
            // 使用 listContainersCmd 而不是 inspectContainerCmd
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(false) // 只获取运行中的容器
                    .exec();

            for (Container container : containers) {
                String[] names = container.getNames();
                for (String name : names) {
                    String cleanName = name.startsWith("/") ? name.substring(1) : name;
                    if (cleanName.equals(containerName)) {
                        return true;
                    }
                }
            }
            return false;

        } catch (Exception e) {
            log.warn("检查容器 {} 运行状态失败: {}", containerName, e.getMessage());
            return false;
        }
    }

    /**
     * 安全地获取容器信息 - 避免 JSON 解析错误
     */
    public Map<String, Object> getContainerInfoSafe(String containerName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", containerName);

        if (!dockerConnected) {
            info.put("error", "Docker 未连接");
            return info;
        }

        try {
            // 首先通过 listContainersCmd 获取基本信息
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            boolean containerFound = false;
            for (Container container : containers) {
                String[] names = container.getNames();
                for (String name : names) {
                    String cleanName = name.startsWith("/") ? name.substring(1) : name;
                    if (cleanName.equals(containerName)) {
                        containerFound = true;
                        info.put("id", container.getId().substring(0, 12));
                        info.put("running", container.getState().equals("running"));
                        info.put("status", container.getStatus());
                        info.put("image", container.getImage());
                        info.put("created", container.getCreated());
                        info.put("state", container.getState());
                        break;
                    }
                }
                if (containerFound) break;
            }

            if (!containerFound) {
                info.put("error", "容器不存在");
                info.put("exists", false);
                return info;
            }

            // 如果容器正在运行，尝试获取更多信息（但忽略解析错误）
            if (Boolean.TRUE.equals(info.get("running"))) {
                try {
                    InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerName).exec();

                    // 安全地尝试获取更多信息
                    try {
                        info.put("created", inspect.getCreated());
                    } catch (Exception e) {
                        // 忽略解析错误
                    }

                    try {
                        if (inspect.getState().getHealth() != null) {
                            info.put("healthStatus", inspect.getState().getHealth().getStatus());
                        }
                    } catch (Exception e) {
                        // 忽略解析错误
                    }

                } catch (Exception e) {
                    // 即使 inspect 失败，也不影响基本功能
                    info.put("inspectWarning", "无法获取完整信息: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            info.put("error", e.getMessage());
        }

        return info;
    }

    /**
     * 获取 Docker 连接状态
     */
    public boolean isDockerConnected() {
        return dockerConnected;
    }

    /**
     * 获取容器健康状态报告 - 修复版本，避免 JSON 解析错误
     */
    public Map<String, Object> getDockerHealthReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dockerConnected", dockerConnected);
        report.put("timestamp", new Date());

        if (dockerConnected) {
            try {
                // 使用安全的方式获取容器信息
                Map<String, Object> pythonInfo = getContainerInfoSafe(pythonContainerName);
                Map<String, Object> rInfo = getContainerInfoSafe(rContainerName);

                report.put("pythonContainer", pythonInfo);
                report.put("rContainer", rInfo);
                report.put("volumePath", volumePath);

                // 获取 Docker 信息
                Info info = dockerClient.infoCmd().exec();
                report.put("dockerVersion", info.getServerVersion());
                report.put("totalContainers", info.getContainers());
                report.put("runningContainers", info.getContainersRunning());

                // 计算健康状态
                boolean pythonHealthy = pythonInfo.containsKey("running") &&
                        Boolean.TRUE.equals(pythonInfo.get("running"));
                boolean rHealthy = rInfo.containsKey("running") &&
                        Boolean.TRUE.equals(rInfo.get("running"));

                report.put("allHealthy", pythonHealthy && rHealthy);
                report.put("status", pythonHealthy && rHealthy ? "HEALTHY" : "DEGRADED");

            } catch (Exception e) {
                log.error("获取健康报告失败", e);
                report.put("error", e.getMessage());
                report.put("status", "ERROR");
            }
        }

        return report;
    }

    /**
     * 获取所有容器列表
     */
    public List<Map<String, Object>> getAllContainers() {
        List<Map<String, Object>> result = new ArrayList<>();

        if (!dockerConnected) {
            return result;
        }

        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            for (Container container : containers) {
                Map<String, Object> containerInfo = new LinkedHashMap<>();
                containerInfo.put("id", container.getId().substring(0, 12));
                containerInfo.put("names", container.getNames());
                containerInfo.put("image", container.getImage());
                containerInfo.put("status", container.getStatus());
                containerInfo.put("state", container.getState());
                containerInfo.put("created", container.getCreated());

                result.add(containerInfo);
            }
        } catch (Exception e) {
            log.error("获取容器列表失败", e);
        }

        return result;
    }

    /**
     * 启动容器
     */
    public boolean startContainer(String containerName) {
        if (!dockerConnected) {
            log.error("Docker 未连接，无法启动容器");
            return false;
        }

        try {
            log.info("正在启动容器: {}", containerName);
            dockerClient.startContainerCmd(containerName).exec();

            // 等待容器启动
            Thread.sleep(2000);

            boolean isRunning = isContainerRunning(containerName);
            if (isRunning) {
                log.info("容器 {} 启动成功", containerName);
            } else {
                log.warn("容器 {} 启动命令已发送，但可能未成功运行", containerName);
            }

            return isRunning;
        } catch (Exception e) {
            log.error("启动容器 {} 失败: {}", containerName, e.getMessage());
            return false;
        }
    }

    /**
     * 停止容器
     */
    public boolean stopContainer(String containerName) {
        if (!dockerConnected) {
            log.error("Docker 未连接，无法停止容器");
            return false;
        }

        try {
            log.info("正在停止容器: {}", containerName);
            dockerClient.stopContainerCmd(containerName).exec();

            // 等待容器停止
            Thread.sleep(2000);

            boolean isRunning = isContainerRunning(containerName);
            if (!isRunning) {
                log.info("容器 {} 停止成功", containerName);
            } else {
                log.warn("容器 {} 停止命令已发送，但可能仍在运行", containerName);
            }

            return !isRunning;
        } catch (Exception e) {
            log.error("停止容器 {} 失败: {}", containerName, e.getMessage());
            return false;
        }
    }


    /**
     * 在 Python 容器中执行代码
     */
    public ExecutionResult executePython(String code, String taskId) {
        ExecutionResult result = new ExecutionResult();
        result.setTaskId(taskId);
        result.setLanguage("python");
        result.setCode(code);
        result.setEnvironment("python:3.9-slim (read-only, no network)");
        result.setStartTime(System.currentTimeMillis());
        result.setStatus("running");

        log.info("开始执行 Python 代码，任务ID: {}", taskId);

        try {
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
                    .withEnv(Collections.singletonList("TASK_ID=" + taskId))  // 新增这行：注入任务ID
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
            result.calculateExecutionTime(); // 自动计算执行时间

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
     * 在 R 容器中执行代码
     */
    public ExecutionResult executeR(String code, String taskId) {
        ExecutionResult result = new ExecutionResult();
        result.setTaskId(taskId);
        result.setLanguage("r");
        result.setCode(code);
        result.setEnvironment("rocker/r-base:latest (read-only, no network)");
        result.setStartTime(System.currentTimeMillis());
        result.setStatus("running");

        log.info("开始执行 R 代码，任务ID: {}", taskId);

        try {
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
                    .withEnv(Collections.singletonList("TASK_ID=" + taskId))  // 新增这行：注入任务ID
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
            result.calculateExecutionTime(); // 自动计算执行时间

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
     * 检查生成的图片文件
     */
    private void checkForGeneratedImages(ExecutionResult result, String taskId) {
        try {
            List<String> images = new ArrayList<>();
            Path sharedDir = Paths.get(volumePath);

            // 新增：如果配置了专门的绘图路径，也检查那里
            Path plotDir = Paths.get(plotPath);
            List<Path> searchDirs = Arrays.asList(sharedDir, plotDir);

            // 查找可能的图片文件：既按任务ID，也按常见固定名称
            String[] imageExtensions = {".png", ".jpg", ".jpeg", ".svg", ".pdf"};
            String[] possibleFileNames = {
                    taskId, // 按任务ID查找
            };

            for (Path searchDir : searchDirs) {
                if (!Files.exists(searchDir)) continue;

                for (String baseName : possibleFileNames) {
                    for (String ext : imageExtensions) {
                        Path imagePath = searchDir.resolve(baseName + ext);
                        log.info("生成的图片路径：{}", imagePath);
                        if (Files.exists(imagePath)) {
                            try {
                                log.info("✅ 找到图片文件: {}", imagePath);
                                byte[] imageBytes = Files.readAllBytes(imagePath);
                                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                                // 根据扩展名设置正确的MIME类型
                                String mimeType = ext.equals(".svg") ? "svg+xml" :
                                        ext.equals(".pdf") ? "pdf" : ext.substring(1);
                                images.add("data:image/" + mimeType + ";base64," + base64Image);

                                // 清理图片文件 (可选)
                                Files.deleteIfExists(imagePath);
                            } catch (Exception e) {
                                log.warn("处理图片文件 {} 失败: {}", imagePath, e.getMessage());
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
                log.info("ℹ️ 未找到任务生成的图片文件，任务ID: {}", taskId);
            }
        } catch (Exception e) {
            log.warn("检查生成图片时发生异常: {}", e.getMessage());
            result.setHasVisualization(false);
        }
    }
}