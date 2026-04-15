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
     * 异步执行代码 (🌟 升级：加入 fileName 参数)
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
                // 🌟 核心：将 fileName 传递给下层沙箱
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
                .handle((result, ex) -> { // 使用 handle 同时处理正常结果和异常
                    if (ex != null) {
                        // 处理异常情况
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
                            // 其他执行异常
                            log.error("任务执行异常: {}", finalTaskId, ex);
                            ExecutionResult errorResult = new ExecutionResult();
                            errorResult.setTaskId(finalTaskId);
                            errorResult.setStatus("error");
                            ex.getCause();
                            errorResult.setMessage(ex.getCause().getMessage());
                            errorResult.setLanguage(language);
                            errorResult.setCode(code);
                            errorResult.setEndTime(System.currentTimeMillis());
                            errorResult.calculateExecutionTime();
                            return errorResult;
                        }
                    } else {
                        // 正常完成，直接返回 DockerSandboxService 产生的正确结果
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
     * 在沙箱中执行代码 (🌟 升级：接收并传递 fileName 参数)
     */
    private ExecutionResult executeInSandbox(String taskId, String code, String language, String fileName) {
        log.info("在沙箱中执行代码: taskId={}, language={}, fileName={}", taskId, language, fileName);

        // 检查Docker连接
        if (!dockerSandboxService.isDockerConnected()) {
            ExecutionResult errorResult = new ExecutionResult(taskId, "error", "Docker 连接不可用");
            errorResult.setLanguage(language);
            errorResult.setCode(code);
            errorResult.setEndTime(System.currentTimeMillis());
            errorResult.calculateExecutionTime();
            return errorResult;
        }

        // 根据语言选择执行器
        ExecutionResult result;
        switch (language.toLowerCase()) {
            case "python":
                log.info("执行 Python 代码，长度: {} 字符", code.length());
                // 🌟 将 fileName 传入底层的 executePython
                result = dockerSandboxService.executePython(code, taskId, fileName);
                break;
            case "r":
                log.info("执行 R 代码，长度: {} 字符", code.length());
                // 🌟 将 fileName 传入底层的 executeR
                result = dockerSandboxService.executeR(code, taskId, fileName);
                break;
            default:
                throw new IllegalArgumentException("不支持的编程语言: " + language);
        }

        // 保存结果
        saveResult(taskId, result);

        return result;
    }

    /**
     * 保存执行结果到Redis（24小时过期）
     */
    private void saveResult(String taskId, ExecutionResult result) {
        try {
            String redisKey = "execution:" + taskId;
            redisTemplate.opsForValue().set(
                    redisKey,
                    result,
                    RESULT_EXPIRATION_HOURS,
                    TimeUnit.HOURS
            );
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
            log.info("尝试从Redis读取，Key: {}", redisKey);

            // 先获取原始数据，检查是否存在
            Boolean exists = redisTemplate.hasKey(redisKey);
            log.info("Redis Key 是否存在: {}", exists);

            if (Boolean.TRUE.equals(exists)) {
                // 获取并记录原始值
                Object rawValue = redisTemplate.opsForValue().get(redisKey);
                log.info("从Redis获取的原始数据类型: {}", rawValue != null ? rawValue.getClass().getName() : "null");

                if (rawValue instanceof ExecutionResult) {
                    ExecutionResult savedResult = (ExecutionResult) rawValue;
                    log.info("成功反序列化为ExecutionResult: 状态={}, 消息={}, 输出长度={}",
                            savedResult.getStatus(),
                            savedResult.getMessage(),
                            savedResult.getOutput() != null ? savedResult.getOutput().length() : 0);
                    return savedResult;
                } else {
                    log.warn("Redis中的值不是ExecutionResult类型: {}", rawValue);
                }
            } else {
                log.warn("Redis中未找到任务: {}", taskId);
            }
        } catch (Exception e) {
            log.error("从Redis获取结果失败，任务ID: {}", taskId, e);
            // 这里不要返回错误结果，继续执行下面的逻辑
        }

        // ============ 其次，检查活跃任务（仅用于返回“运行中”状态）============
        CompletableFuture<ExecutionResult> future = activeTasks.get(taskId);
        if (future != null) {
            if (future.isDone()) {
                // 任务已完成，但Redis中可能还没有（短暂窗口期），此时从future获取
                try {
                    ExecutionResult futureResult = future.get();
                    log.info("从已完成的Future获取结果: {}, 状态: {}", taskId, futureResult.getStatus());
                    return futureResult;
                } catch (Exception e) {
                    log.error("从Future获取结果失败: {}", taskId, e);
                }
            } else {
                // 任务仍在运行中
                ExecutionResult pendingResult = new ExecutionResult(taskId, "running", "任务正在执行中");
                log.info("任务仍在执行中: {}", taskId);
                return pendingResult;
            }
        }

        // ============ 最后，返回“不存在”状态 ============
        log.warn("任务不存在或已过期: {}", taskId);
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
                log.info("任务取消成功: {}", taskId);

                // 保存取消状态
                ExecutionResult cancelledResult = new ExecutionResult(taskId, "cancelled", "任务已被取消");
                cancelledResult.setEndTime(System.currentTimeMillis());
                cancelledResult.calculateExecutionTime();
                saveResult(taskId, cancelledResult);
            }
            return cancelled;
        }

        log.warn("任务无法取消（可能已完成或不存在）: {}", taskId);
        return false;
    }

    /**
     * 同步执行代码（阻塞）
     * 🌟 升级：加入 fileName 参数
     */
    public ExecutionResult executeSync(String taskId, String code, String language, String fileName) {
        log.info("同步执行代码: taskId={}, language={}, fileName={}", taskId, language, fileName);

        try {
            // 🌟 将 fileName 传递给下层沙箱
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

    /**
     * 优雅关闭服务
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭代码执行服务...");

        // 取消所有活跃任务
        synchronized (activeTasks) {
            for (String taskId : activeTasks.keySet()) {
                cancelTask(taskId);
            }
        }

        // 关闭线程池
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
}