package com.example.bio_platform.service;

import com.example.bio_platform.common.ExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import java.util.UUID;

@Service
@Slf4j
public class CodeExecutionService {

    @Autowired
    private DockerSandboxService dockerSandboxService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final Map<String, CompletableFuture<ExecutionResult>> activeTasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    // 任务执行超时时间（秒）
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;
    // Redis 中任务结果的过期时间（小时）
    private static final long RESULT_EXPIRATION_HOURS = 24;

    /**
     * 异步执行代码
     */
    public CompletableFuture<ExecutionResult> executeAsync(String taskId, String code, String language, String fileName) {
        log.info("创建异步执行任务: taskId={}, language={}, fileName={}", taskId, language, fileName);

        // 如果未提供 taskId，则生成一个
        if (taskId == null || taskId.trim().isEmpty()) {
            taskId = generateTaskId(language);
        }

        final String finalTaskId = taskId;
        // 检查任务是否已存在（并发安全）
        synchronized (activeTasks) {
            CompletableFuture<ExecutionResult> existingFuture = activeTasks.get(finalTaskId);
            if (existingFuture != null) {
                log.warn("任务已存在: {}", finalTaskId);
                return existingFuture;
            }
        }
        // 创建异步任务
        CompletableFuture<ExecutionResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始执行代码任务: {}", finalTaskId);
                return executeInSandbox(finalTaskId, code, language, fileName);
            } catch (Exception e) {
                log.error("执行代码任务失败: {}", finalTaskId, e);
                ExecutionResult result = new ExecutionResult(finalTaskId, "error", "执行过程异常: " + e.getMessage());
                result.setLanguage(language);
                result.setCode(code);
                result.setEndTime(System.currentTimeMillis());
                result.calculateExecutionTime();
                saveResult(finalTaskId, result);
                return result;
            }
        }, executorService);

        CompletableFuture<ExecutionResult> timeoutFuture = future
                .orTimeout(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((result, ex) -> {
                    if (ex != null) {
                        if (ex instanceof TimeoutException) {
                            log.warn("任务执行超时: {}", finalTaskId);
                            ExecutionResult timeoutResult = new ExecutionResult();
                            timeoutResult.setTaskId(finalTaskId);
                            timeoutResult.setStatus("timeout");
                            timeoutResult.setMessage("执行超时（超过 " + EXECUTION_TIMEOUT_SECONDS + " 秒）");
                            timeoutResult.setLanguage(language);
                            timeoutResult.setCode(code);
                            timeoutResult.setEndTime(System.currentTimeMillis());
                            timeoutResult.calculateExecutionTime();
                            return timeoutResult;
                        } else {
                            log.error("任务执行异常: {}", finalTaskId, ex);
                            ExecutionResult errorResult = new ExecutionResult();
                            errorResult.setTaskId(finalTaskId);
                            errorResult.setStatus("error");
                            errorResult.setMessage(ex.getCause() != null ? ex.getCause().getMessage() : "未知异常");
                            errorResult.setLanguage(language);
                            errorResult.setCode(code);
                            errorResult.setEndTime(System.currentTimeMillis());
                            errorResult.calculateExecutionTime();
                            return errorResult;
                        }
                    } else {
                        log.info("任务正常完成: {}, 状态: {}", finalTaskId, result != null ? result.getStatus() : "null");
                        return result;
                    }
                });

        // 存储任务引用（并发安全）
        synchronized (activeTasks) {
            CompletableFuture<ExecutionResult> existingFuture = activeTasks.putIfAbsent(finalTaskId, timeoutFuture);
            if (existingFuture != null) {
                log.warn("任务已存在（并发冲突）: {}", finalTaskId);
                return existingFuture;
            }
        }

        // 任务完成后统一清理
        timeoutFuture.whenComplete((result, ex) -> {
            synchronized (activeTasks) {
                activeTasks.remove(finalTaskId);
            }
            if (ex != null) {
                log.error("任务异常完成: {}, 异常: {}", finalTaskId, ex.getMessage());
            } else {
                log.info("任务完成: {}, 状态: {}", finalTaskId, result.getStatus());
            }
        });
        return timeoutFuture;
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId(String language) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return language + "_" + timestamp + "_" + uuid;
    }

    /**
     * 在沙箱中执行代码
     */
    private ExecutionResult executeInSandbox(String taskId, String code, String language, String fileName) {
        log.info("在沙箱中执行代码: taskId={}, language={}, fileName={}", taskId, language, fileName);

        if (!dockerSandboxService.isDockerConnected()) {
            ExecutionResult errorResult = new ExecutionResult(taskId, "error", "Docker 连接不可用");
            errorResult.setLanguage(language);
            errorResult.setCode(code);
            errorResult.setEndTime(System.currentTimeMillis());
            errorResult.calculateExecutionTime();
            return errorResult;
        }

        ExecutionResult result;
        if ("python".equalsIgnoreCase(language)) {
            log.info("执行 Python 代码，长度: {} 字符", code.length());
            result = dockerSandboxService.executePython(code, taskId, fileName);
        } else {
            throw new IllegalArgumentException("当前系统仅支持 Python 语言，不支持: " + language);
        }
        saveResult(taskId, result);
        return result;
    }

    /**
     * 保存执行结果到Redis
     */
    private void saveResult(String taskId, ExecutionResult result) {
        try {
            String redisKey = "execution:" + taskId;
            redisTemplate.opsForValue().set(redisKey, result, RESULT_EXPIRATION_HOURS, TimeUnit.HOURS);
            log.info("保存结果到Redis: {}, 状态: {}", redisKey, result.getStatus());
        } catch (Exception e) {
            log.error("保存结果到Redis失败: {}", taskId, e);
        }
    }

    /**
     * 获取执行结果
     */
    public ExecutionResult getExecutionResult(String taskId) {
        log.info("获取执行结果: {}", taskId);
        try {
            String redisKey = "execution:" + taskId;
            Boolean exists = redisTemplate.hasKey(redisKey);
            if (Boolean.TRUE.equals(exists)) {
                Object rawValue = redisTemplate.opsForValue().get(redisKey);
                if (rawValue instanceof ExecutionResult) {
                    return (ExecutionResult) rawValue;
                }
            }
        } catch (Exception e) {
            log.error("从Redis获取结果失败，任务ID: {}", taskId, e);
        }
        CompletableFuture<ExecutionResult> future = activeTasks.get(taskId);
        if (future != null) {
            if (future.isDone()) {
                try {
                    return future.get();
                } catch (Exception e) {
                    log.error("从Future获取结果失败: {}", taskId, e);
                }
            } else {
                return new ExecutionResult(taskId, "running", "任务正在执行中");
            }
        }
        return new ExecutionResult(taskId, "expired", "任务不存在或已过期");
    }

    /**
     * 取消正在执行的任务
     */
    public boolean cancelTask(String taskId) {
        log.info("尝试取消任务: {}", taskId);
        CompletableFuture<ExecutionResult> future = activeTasks.get(taskId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                synchronized (activeTasks) {
                    activeTasks.remove(taskId);
                }
                ExecutionResult cancelledResult = new ExecutionResult(taskId, "cancelled", "任务已被取消");
                cancelledResult.setEndTime(System.currentTimeMillis());
                cancelledResult.calculateExecutionTime();
                saveResult(taskId, cancelledResult);
            }
            return cancelled;
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        log.info("开始关闭代码执行服务...");
        synchronized (activeTasks) {
            for (String taskId : activeTasks.keySet()) {
                cancelTask(taskId);
            }
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("代码执行服务已关闭");
    }


    /**
     * 同步执行代码
     */
    public ExecutionResult executeSync(String taskId, String code, String language, String fileName) {
        try {
            return executeInSandbox(taskId, code, language, fileName);
        } catch (Exception e) {
            log.error("同步执行代码失败", e);
            ExecutionResult errorResult = new ExecutionResult(taskId, "error", "执行失败: " + e.getMessage());
            errorResult.setLanguage(language);
            errorResult.setCode(code);
            errorResult.setEndTime(System.currentTimeMillis());
            errorResult.calculateExecutionTime();
            return errorResult;
        }
    }
}