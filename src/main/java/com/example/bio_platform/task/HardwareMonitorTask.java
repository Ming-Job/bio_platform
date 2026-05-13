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
            // 代码在类初始化时记录了一次 prevTicks，然后在每次执行时，对比当前 Ticks 和 3 秒前的 prevTicks 的差值。
            // 算出了过去 3 秒内 CPU 使用率
            double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
            prevTicks = processor.getSystemCpuLoadTicks(); // 记录本次滴答数，留给下次用
            stats.put("cpu", formatDouble(cpuLoad));

            // 2. 获取内存使用率
            GlobalMemory memory = hal.getMemory();
            long totalMemory = memory.getTotal();
            long availableMemory = memory.getAvailable();
            // 用（总的内存-可用的内存）/ 总的内存
            double memoryUsage = totalMemory == 0 ? 0 : ((double) (totalMemory - availableMemory) / totalMemory) * 100;
            stats.put("memory", formatDouble(memoryUsage));

            // 3. 获取特定的磁盘存储使用率 (仅针对 D 盘)
            FileSystem fileSystem = os.getFileSystem();
            long dTotal = 0;
            long dUsable = 0;

            for (OSFileStore fs : fileSystem.getFileStores()) {
                // 在 Windows 下，getMount() 通常返回 "D:\"
                if (fs.getMount().startsWith("D:")) {
                    dTotal = fs.getTotalSpace();
                    dUsable = fs.getUsableSpace();
                    break; // 找到 D 盘后直接跳出循环
                }
            }

            // 计算 D 盘使用率
            double storageUsage = dTotal == 0 ? 0 : ((double) (dTotal - dUsable) / dTotal) * 100;
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