package com.example.bio_platform.task;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
public class HardwareMonitorTask {

    // 实例化 OSHI 的核心对象
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hal = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();

    // 核心原理：用于保存上一次采集的 CPU 滴答数
    private long[] prevTicks = new long[CentralProcessor.TickType.values().length];

    // 本地缓存：前端 HTTP 接口直接读这个 Map，永远不会造成线程阻塞
    private Map<String, Object> currentHardwareStats = new HashMap<>();

    public HardwareMonitorTask() {
        // 项目启动时，初始化一次 CPU 滴答数
        prevTicks = hal.getProcessor().getSystemCpuLoadTicks();

        // 给缓存赋一个初始值
        currentHardwareStats.put("cpu", 0.0);
        currentHardwareStats.put("memory", 0.0);
        currentHardwareStats.put("storage", 0.0);
    }

    /**
     * @Scheduled(fixedRate = 3000) 意味着每 3 秒在后台静默执行一次
     */
    @Scheduled(fixedRate = 3000)
    public void collectHardwareInfo() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 1. 获取 CPU 使用率 (基于前后两次滴答数的差值精准计算)
            CentralProcessor processor = hal.getProcessor();
            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
            prevTicks = processor.getSystemCpuLoadTicks(); // 记录本次滴答数，留给下次用
            stats.put("cpu", formatDouble(cpuLoad));

            // 2. 获取内存使用率
            GlobalMemory memory = hal.getMemory();
            long totalMemory = memory.getTotal();
            long availableMemory = memory.getAvailable();
            double memoryUsage = totalMemory == 0 ? 0 : ((double) (totalMemory - availableMemory) / totalMemory) * 100;
            stats.put("memory", formatDouble(memoryUsage));

            // 3. 获取磁盘/OSS 存储使用率 (遍历所有本地磁盘累加计算)
            FileSystem fileSystem = os.getFileSystem();
            long totalStorage = 0;
            long usableStorage = 0;
            for (OSFileStore fs : fileSystem.getFileStores()) {
                totalStorage += fs.getTotalSpace();
                usableStorage += fs.getUsableSpace();
            }
            double storageUsage = totalStorage == 0 ? 0 : ((double) (totalStorage - usableStorage) / totalStorage) * 100;
            stats.put("storage", formatDouble(storageUsage));

            // 4. 将最新数据无缝切换到缓存中
            currentHardwareStats = stats;

        } catch (Exception e) {
            // 采集失败时不破坏原缓存，只打印日志
            e.printStackTrace();
        }
    }

    /**
     * 暴露给 Controller 的方法：O(1) 复杂度，毫秒级响应
     */
    public Map<String, Object> getCurrentHardwareStats() {
        return currentHardwareStats;
    }

    // 小助手：保留 1 位小数，防止前端显示长串浮点数
    private double formatDouble(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}