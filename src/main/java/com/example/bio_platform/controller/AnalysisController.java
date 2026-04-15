package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bio_platform.common.Result;
import com.example.bio_platform.dto.TaskSubmitDTO;
import com.example.bio_platform.entity.*;
import com.example.bio_platform.mapper.AnalysisPipelineMapper;
import com.example.bio_platform.mapper.AnalysisTaskMapper;
import com.example.bio_platform.mapper.FileMapper;
import com.example.bio_platform.mapper.ProjectMapper;
import com.example.bio_platform.service.AnalysisTaskService;
import com.example.bio_platform.service.impl.AiServiceImpl;
import com.example.bio_platform.task.HardwareMonitorTask;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "生信分析中心 - 任务调度引擎接口")
@RestController
@RequestMapping("/api/analysis")
@Slf4j
public class AnalysisController {

    @Autowired
    private AnalysisTaskService analysisTaskService;

    @Autowired
    private AnalysisPipelineMapper pipelineMapper;

    @Autowired
    private AnalysisTaskMapper taskMapper;

    @Autowired
    private FileMapper fileMapper;

    @Autowired
    private HardwareMonitorTask hardwareMonitorTask;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AiServiceImpl aiService;


    @ApiOperation("获取所有已启用的分析流程模板")
    @GetMapping("/pipelines/list")
    public Map<String, Object> getPipelines() {
        List<AnalysisPipeline> list = pipelineMapper.selectList(
                new LambdaQueryWrapper<AnalysisPipeline>()
                        .eq(AnalysisPipeline::getIsActive, 1)
                        .orderByAsc(AnalysisPipeline::getSortOrder)
        );
        return success(list);
    }

    @ApiOperation("投递(发射)生信分析任务")
    @PostMapping("/tasks/submit")
    public Map<String, Object> submitTask(@RequestBody TaskSubmitDTO dto,
                                          @RequestHeader(value = "userId", defaultValue = "6") Long userId) {

        // 1. 先把任务存进数据库，拿到任务 ID
        Long taskId = analysisTaskService.submitTask(dto, userId);

        // 2. 🌟 核心智能路由：查出当前跑的是什么流程
        AnalysisPipeline pipeline = pipelineMapper.selectById(dto.getPipelineId());
        if (pipeline != null) {
            String category = pipeline.getCategory();
            String name = pipeline.getName() != null ? pipeline.getName().toLowerCase() : "";
            String paramsJson = dto.getParams(); // 获取前端传来的动态参数

            // 🌟 核心修改：纯净版三路分发
            if ("microbiome".equalsIgnoreCase(category) || name.contains("16s") || name.contains("qiime")) {
                log.info("🟢 路由匹配: 16S 扩增子分析引擎");
                analysisTaskService.simulateMicrobiomeExecution(taskId);
            }
            else if ("genomics".equalsIgnoreCase(category) || name.contains("gwas")) {
                log.info("🔵 路由匹配: GWAS 分析引擎");
                analysisTaskService.simulateGwasExecution(taskId);
            }
            else {
                // 🌟 直接霸气接管！只要是转录组，全部走端到端多样本流水线！
                log.info("🟣 路由匹配: RNA-Seq 端到端超级复合引擎 (排队比对 + DESeq2)");
                analysisTaskService.simulateRnaSeqExecution(taskId);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        return success(data);
    }
    @ApiOperation("获取分析控制台大盘统计与任务队列")
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(
            @RequestHeader(value = "userId", defaultValue = "1") Long userId,
            @RequestParam(required = false) Long projectId) {

        Map<String, Object> result = new HashMap<>();
        result.put("stats", analysisTaskService.getDashboardStats(userId, projectId));

        LambdaQueryWrapper<AnalysisTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(AnalysisTask::getUserId, userId);

        if (projectId != null) {
            taskWrapper.eq(AnalysisTask::getProjectId, projectId);
        }

        taskWrapper.orderByDesc(AnalysisTask::getCreatedAt).last("LIMIT 5");
        List<AnalysisTask> recentTasks = taskMapper.selectList(taskWrapper);

        for (AnalysisTask t : recentTasks) {
            if (t.getProjectId() != null) {
                Project project = projectMapper.selectById(t.getProjectId());
                if (project != null) {
                    t.setProjectName(project.getName());
                }
            } else {
                t.setProjectName("未绑定课题");
            }
        }
        result.put("recentTasks", recentTasks);
        result.put("hardware", hardwareMonitorTask.getCurrentHardwareStats());

        return success(result);
    }

    @ApiOperation("获取任务执行明细、日志与结果产出物")
    @GetMapping("/tasks/{taskId}/details")
    public Map<String, Object> getTaskDetails(@PathVariable Long taskId,
                                              @RequestHeader(value = "userId", defaultValue = "1") Long userId) {
        Map<String, Object> detailData = analysisTaskService.getTaskDetails(taskId, userId);
        return success(detailData);
    }

    @ApiOperation("分页获取任务调度中心列表")
    @GetMapping("/tasks/page")
    public Map<String, Object> getTaskPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "userId", defaultValue = "6") Long userId) {

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnalysisTask> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);

        LambdaQueryWrapper<AnalysisTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisTask::getUserId, userId);

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(AnalysisTask::getTaskName, keyword.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(AnalysisTask::getStatus, status);
        }
        wrapper.orderByDesc(AnalysisTask::getCreatedAt);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AnalysisTask> resultPage =
                analysisTaskService.page(pageParam, wrapper);

        for (AnalysisTask task : resultPage.getRecords()) {
            if (task.getProjectId() != null) {
                Project project = projectMapper.selectById(task.getProjectId());
                if (project != null) {
                    task.setProjectName(project.getName());
                }
            } else {
                task.setProjectName("未绑定课题");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", resultPage.getRecords());
        result.put("total", resultPage.getTotal());

        return success(result);
    }

    @ApiOperation("触发多样本差异表达分析 (集成 Python DESeq2沙盒)")
    @PostMapping("/tasks/diff")
    public Map<String, Object> performDiffAnalysis(@RequestBody Map<String, List<Long>> params) {
        List<Long> controlTaskIds = params.get("controlTaskIds");
        List<Long> treatTaskIds = params.get("treatTaskIds");
        Map<String, Object> errorRes = new HashMap<>();

        if (controlTaskIds == null || treatTaskIds == null) {
            errorRes.put("code", 400);
            errorRes.put("message", "参数缺失：必须同时提供对照组和处理组数组");
            return errorRes;
        }
        if (controlTaskIds.size() < 3 || treatTaskIds.size() < 3) {
            errorRes.put("code", 400);
            errorRes.put("message", "学术规范拦截：真实的 DESeq2 计算要求至少 3 个生物学重复样本");
            return errorRes;
        }

        try {
            log.info("前端发起多样本分析请求 | Control组: {}, Treat组: {}", controlTaskIds, treatTaskIds);
            analysisTaskService.performMultiSampleDiffAnalysis(controlTaskIds, treatTaskIds);
            return success("Python DESeq2 矩阵运算完毕，折叠倍数与 P-value 已物理落盘！");
        } catch (RuntimeException e) {
            log.warn("多样本差异分析业务阻断: {}", e.getMessage());
            errorRes.put("code", 500);
            errorRes.put("message", e.getMessage());
            return errorRes;
        } catch (Exception e) {
            log.error("多样本差异分析发生系统级异常", e);
            errorRes.put("code", 500);
            errorRes.put("message", "系统繁忙，Python 容器调度失败，请联系管理员");
            return errorRes;
        }
    }

    @ApiOperation("获取差异表达分析结果 (用于绘制真实火山图)")
    @GetMapping("/tasks/diff/result")
    public Map<String, Object> getDiffResult(
            @RequestParam Long controlId,
            @RequestParam Long treatId) {

        Map<String, Object> errorRes = new HashMap<>();
        if (controlId == null || treatId == null) {
            errorRes.put("code", 400);
            errorRes.put("message", "参数缺失");
            return errorRes;
        }

        try {
            log.info("提取真实多样本火山图数据 | 代表 Control ID: {}, 代表 Treat ID: {}", controlId, treatId);
            List<TaskDiffExpression> list = analysisTaskService.getDiffAnalysisResult(controlId, treatId);
            if (list == null) list = new ArrayList<>();
            return success(list);
        } catch (Exception e) {
            log.error("提取差异分析结果失败", e);
            errorRes.put("code", 500);
            errorRes.put("message", "获取图表数据失败，请联系管理员");
            return errorRes;
        }
    }



    private Map<String, Object> success(Object data) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("message", "操作成功");
        res.put("data", data);
        return res;
    }

    @ApiOperation("智能文件探针：根据挂载文件生成动态分析提示词")
    @GetMapping("/smart-prompts")
    public Map<String, Object> getSmartPrompts(@RequestParam String fileName) {
        Map<String, Object> errorRes = new HashMap<>();
        if (fileName == null || fileName.trim().isEmpty()) {
            errorRes.put("code", 400);
            errorRes.put("message", "文件名不能为空");
            return errorRes;
        }

        String filePath = "D:/docker_share/" + fileName;
        StringBuilder fileHeader = new StringBuilder();

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(filePath))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null && count < 5) {
                fileHeader.append(line).append("\n");
                count++;
            }
        } catch (Exception e) {
            log.warn("探针读取物理文件失败: {}", e.getMessage());
            fileHeader.append("[无法读取文件内容，请仅根据文件名推测业务场景]");
        }

        log.info("触发智能探针，扫描文件: {}, 提取特征:\n{}", fileName, fileHeader.toString());
        List<String> prompts = aiService.generateSmartPrompts(fileName, fileHeader.toString());

        if (prompts == null || prompts.isEmpty()) {
            prompts = new ArrayList<>();
            prompts.add("读取挂载的数据文件，打印前5行并展示基础信息 (全英文)");
            prompts.add("检查数据中是否存在缺失值，如果有请进行均值插补 (全英文)");
            prompts.add("提取数据中的数值列，进行PCA主成分分析并绘制散点图 (全英文)");
        }

        return success(prompts);
    }

    @ApiOperation("获取 16S 物种丰度表 (供前端 Echarts 渲染)")
    @GetMapping("/tasks/taxa/result")
    public Map<String, Object> getTaxaResult(@RequestParam Long taskId) {
        Map<String, Object> errorRes = new HashMap<>();

        String storageRoot = "D:/bio_uploads/files/";
        String csvPath = storageRoot + "task_results/task_" + taskId + "/taxa_csv_export/level-6.csv";

        java.io.File file = new java.io.File(csvPath);
        if (!file.exists()) {
            errorRes.put("code", 404);
            errorRes.put("message", "丰度数据不存在，请确认 QIIME2 第6步导出是否完成");
            return errorRes;
        }

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
            // 1. 读取表头
            String headerLine = br.readLine();
            String[] headers = headerLine.split(",");

            // 2. 将所有数据行先读入内存
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                rows.add(line.split(","));
            }

            if (rows.isEmpty()) {
                throw new RuntimeException("CSV 数据行为空");
            }

            // 3. 智能探测：看第一行数据，判断到底有几列是真实的“数字(丰度)”
            int validTaxaCols = 0;
            String[] firstRow = rows.get(0);
            for (int i = 1; i < firstRow.length; i++) {
                try {
                    Double.parseDouble(firstRow[i]);
                    validTaxaCols++;
                } catch (NumberFormatException e) {
                    break;
                }
            }

            // 🌟🌟🌟 核心新增：给样本排队！按照分组名字的字母顺序排序 (CK 会自动排在 T 前面) 🌟🌟🌟
            final int groupColIndex = validTaxaCols + 1;
            rows.sort((r1, r2) -> {
                String group1 = r1.length > groupColIndex ? r1[groupColIndex] : "";
                String group2 = r2.length > groupColIndex ? r2[groupColIndex] : "";
                // 按字母表顺序对比，如果想强制倒序可以改成 group2.compareTo(group1)
                return group1.compareTo(group2);
            });

            // 4. 提取真正的样本名，并计算总丰度
            List<String> samples = new ArrayList<>();
            double[] sampleSums = new double[rows.size()];
            for (int r = 0; r < rows.size(); r++) {
                String[] row = rows.get(r);
                String sampleId = row[0];

                // 抓取分组信息
                String groupName = "";
                if (row.length > validTaxaCols + 1) {
                    groupName = row[validTaxaCols + 1];
                }

                // 拼接样本名和分组
                if (groupName != null && !groupName.trim().isEmpty()) {
                    samples.add(sampleId + "\n(" + groupName + ")");
                } else {
                    samples.add(sampleId);
                }

                for (int c = 1; c <= validTaxaCols; c++) {
                    sampleSums[r] += Double.parseDouble(row[c]);
                }
            }

            // 5. 行列反转，生成画图的 Series
            List<Map<String, Object>> series = new ArrayList<>();
            for (int c = 1; c <= validTaxaCols; c++) {
                String fullTaxaName = headers[c];

                String shortName = fullTaxaName;
                String[] taxParts = fullTaxaName.split(";");
                if (taxParts.length > 0) {
                    shortName = taxParts[taxParts.length - 1].trim();
                    if (shortName.contains("__")) {
                        shortName = shortName.substring(shortName.indexOf("__") + 2);
                    }
                }

                List<Double> dataArr = new ArrayList<>();
                for (int r = 0; r < rows.size(); r++) {
                    double rawValue = Double.parseDouble(rows.get(r)[c]);
                    double relativeAbundance = sampleSums[r] > 0 ? (rawValue / sampleSums[r]) : 0.0;
                    dataArr.add(relativeAbundance);
                }

                double totalAbundance = dataArr.stream().mapToDouble(Double::doubleValue).sum();
                if (totalAbundance > 0.01) {
                    Map<String, Object> seriesItem = new HashMap<>();
                    seriesItem.put("name", shortName);
                    seriesItem.put("data", dataArr);
                    series.add(seriesItem);
                }
            }

            // 6. 打包返回
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("samples", samples);
            responseData.put("series", series);

            return success(responseData);

        } catch (Exception e) {
            log.error("解析 16S 物种丰度 CSV 失败", e);
            errorRes.put("code", 500);
            errorRes.put("message", "解析物种丰度文件发生系统异常");
            return errorRes;
        }
    }



}