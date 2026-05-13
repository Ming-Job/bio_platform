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
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

@Service
@Slf4j
public class DockerSandboxService {

    private DockerClient dockerClient;
    private final String workDir = "/tmp/sandbox";

    @Value("${sandbox.volume.path:D:/docker_share}")
    private String volumePath;  // 宿主机和docker容器共享的文件夹

    @Value("${sandbox.python.container:python-sandbox}")
    private String pythonContainerName;

    @Value("${sandbox.plot.path:D:/docker_plots}")
    private String plotPath;

    @Value("${bio.file.upload.base-dir:D:/bio_uploads/files}")
    private String userUploadDir;

    @Value("${bio.file.system-cases.dir:D:/bio_uploads/system_cases}")
    private String systemCaseDir;

    private boolean dockerConnected = false;

    public DockerSandboxService() {
        log.info("DockerSandboxService 构造函数调用");
    }

    /**
     * 负责建立Docker客户端连接并测试连通性
     */
    @PostConstruct
    public void init() {
        log.info("🔍 配置注入检查:");
        log.info("   Python容器名: {}", pythonContainerName);
        log.info("   共享目录路径: {}", volumePath);
        log.info("初始化 DockerSandboxService...");
        this.dockerClient = createDockerClient();
        testDockerConnection();
        if (dockerConnected) {
            testContainerStatus();
        }
        try {
            Files.createDirectories(Paths.get(systemCaseDir));
            Files.createDirectories(Paths.get(userUploadDir));
            Files.createDirectories(Paths.get(volumePath));
        } catch (Exception e) {
            log.warn("⚠️ 无法创建基础数据目录: {}", e.getMessage());
        }
    }

    /**
     *  确保Python代码在Docker内部执行时，能读取到正确的输入文件。
     * */
    private void prepareSandboxData(String datasetFileName) throws Exception {
        if (datasetFileName == null || datasetFileName.trim().isEmpty() || "null".equals(datasetFileName)) {
            return;
        }

        Path targetPath = Paths.get(volumePath, datasetFileName);
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
            log.error("❌ 数据异常：未在用户目录或系统目录中找到数据集 {}", datasetFileName);
            throw new FileNotFoundException("算力节点未能定位到依赖的数据集：" + datasetFileName);
        }
    }

    /**
     *  Java操控Docker进程
     * */
    private DockerClient createDockerClient() {
        try {
            // Windows 下通常通过暴露出 2375 端口供外部程序调用 API
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
     *  验证Docker是否存活，并获取OS和运行中的容器信息
     * */
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
            log.info("   Docker 信息:");
            log.info("   OS: {}", info.getOperatingSystem());
            log.info("   运行中容器: {}", info.getContainersRunning());
            dockerConnected = true;
        } catch (Exception e) {
            log.error("❌ Docker 连接测试失败: {}", e.getMessage());
            dockerConnected = false;
        }
    }

    /**
     *  检查docker容器状态
     * */
    private void testContainerStatus() {
        log.info("📦 检查沙箱容器状态...");
        checkContainerStatusSafe(pythonContainerName, "Python");
    }
    private void checkContainerStatusSafe(String containerName, String containerType) {
        log.info("   --- 检查 {} 容器: {} ---", containerType, containerName);
        try {
            boolean foundByList = false;
            boolean isRunningByList = false;

            // 查看所有容器，包括死了的
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
            // 拿到的列表里存活的容器
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

    /**
     *  去查询某个具体容器的详细信息，保证不会引发系统报错或崩溃。
     * */
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
                // 去除Docker底层API接口在返回容器名字（/python-sandbox）时带的斜杠
                    if ((name.startsWith("/") ? name.substring(1) : name).equals(containerName)) {
                        // Docker生成的容器ID是一个64位哈希字符串，截取前 12 位
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

    /**
     *  收敛docker底层的十几种状态，将其简化为HEALTHY、DEGRADED、ERROR
     * */
    public Map<String, Object> getDockerHealthReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dockerConnected", dockerConnected);
        if (dockerConnected) {
            try {
                Map<String, Object> pythonInfo = getContainerInfoSafe(pythonContainerName);
                boolean pythonHealthy = Boolean.TRUE.equals(pythonInfo.get("running"));
                report.put("status", pythonHealthy ? "HEALTHY" : "DEGRADED");
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

    /**
     *  启动容器
     * */
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

    /**
     *  停止容器
     * */
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
     * 在 Python 容器中执行代码
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
            prepareSandboxData(datasetFileName);
            String fileName = taskId + ".py";
            Path codePath = Paths.get(volumePath, fileName);

            log.info("将代码写入文件: {}", codePath);
            Files.write(codePath, code.getBytes(StandardCharsets.UTF_8));

            String[] command = {"python", "/tmp/sandbox/" + fileName};

            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(pythonContainerName)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command)
                    .withEnv(Collections.singletonList("TASK_ID=" + taskId))
                    .exec();

            String execId = execCreate.getId();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream(); // 接受容器的正常输出
            ByteArrayOutputStream stderr = new ByteArrayOutputStream(); // 接受容器的报错输出

            dockerClient.execStartCmd(execId)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            // 将STDOUT和STDERR分拣到不同的缓冲区
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
                    .awaitCompletion(30, TimeUnit.SECONDS);  // 30 秒的强制超时熔断

            InspectExecResponse inspect = dockerClient.inspectExecCmd(execId).exec();
            Integer exitCode = inspect.getExitCodeLong() != null ?
                    inspect.getExitCodeLong().intValue() : null;

            String output = stdout.toString(StandardCharsets.UTF_8);
            String error = stderr.toString(StandardCharsets.UTF_8);

            result.setOutput(output);
            result.setError(error);
            result.setEndTime(System.currentTimeMillis());
            result.calculateExecutionTime();

            //  在linux和docker里面，exitCode == 0 为成功
            //  如果是非 0（例如 1 表示报错，137 表示被强杀），系统会精确捕获并将任务标记为error
            if (exitCode == null || exitCode != 0) {
                result.setStatus("error");
                result.setMessage("代码执行失败，退出码: " + exitCode);
            } else {
                result.setStatus("completed");
                result.setMessage("代码执行成功");
                checkForGeneratedImages(result, taskId);
            }

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
     *  获取代码执行完成之后的图表
     * */
    private void checkForGeneratedImages(ExecutionResult result, String taskId) {
        try {
            List<String> images = new ArrayList<>(); // 用于存放最终转换成Base64格式的前端可用字符串
            Path sharedDir = Paths.get(volumePath);
            Path plotDir = Paths.get(plotPath);
            List<Path> searchDirs = Arrays.asList(sharedDir, plotDir);

            for (Path searchDir : searchDirs) {
                if (!Files.exists(searchDir)) continue;

                try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(searchDir)) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString().toLowerCase();

                        if (fileName.contains(taskId.toLowerCase()) &&
                                (fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
                                        fileName.endsWith(".jpeg") || fileName.endsWith(".svg") ||
                                        fileName.endsWith(".pdf"))) {

                            log.info(" 成功捕捉到图片: {}", file);
                            try {
                                byte[] imageBytes = Files.readAllBytes(file);
                                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                                String mimeType = fileName.endsWith(".svg") ? "svg+xml" :
                                        fileName.endsWith(".pdf") ? "pdf" :
                                                fileName.substring(fileName.lastIndexOf(".") + 1);

                                // data: （协议头）
                                // image/png （MIME类型）
                                // base64 （编码方式）
                                images.add("data:image/" + mimeType + ";base64," + base64Image);
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
                log.info(" 成功添加 {} 张图片到任务结果", images.size());
            } else {
                result.setHasVisualization(false);
                log.info(" 未找到包含任务ID {} 的图片文件", taskId);
            }
        } catch (Exception e) {
            log.warn("检查生成图片时发生异常: {}", e.getMessage());
            result.setHasVisualization(false);
        }
    }
}