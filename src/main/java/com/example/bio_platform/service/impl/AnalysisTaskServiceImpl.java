package com.example.bio_platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.dto.TaskSubmitDTO;
import com.example.bio_platform.entity.*;
import com.example.bio_platform.entity.File;
import com.example.bio_platform.mapper.*;
import com.example.bio_platform.service.AnalysisTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalysisTaskServiceImpl extends ServiceImpl<AnalysisTaskMapper, AnalysisTask> implements AnalysisTaskService {

    @Autowired
    private AnalysisPipelineMapper pipelineMapper;
    @Autowired
    private AnalysisTaskFileMapper taskFileMapper;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private AnalysisTaskMapper taskMapper;

    @Autowired
    private TaskDiffExpressionMapper taskDiffExpressionMapper;
    @Autowired
    private TaskGwasResultMapper taskGwasResultMapper;

    private static final String STORAGE_ROOT = "D:/bio_uploads/files/";

    // 分析任务提交
    @Override
    @Transactional(rollbackFor = Exception.class) // 事务回滚
    public Long submitTask(TaskSubmitDTO dto, Long userId) {
        AnalysisPipeline pipeline = pipelineMapper.selectOne(
                new LambdaQueryWrapper<AnalysisPipeline>().eq(AnalysisPipeline::getId, dto.getPipelineId()).eq(AnalysisPipeline::getIsActive, 1)
        );
        if (pipeline == null) throw new RuntimeException("流程不存在");

        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setProjectId(dto.getProjectId());
        task.setPipelineId(pipeline.getId());

        // 给分析任务命名
        String taskName = pipeline.getName() + " - " + System.currentTimeMillis();

        if (dto.getFileIds() != null && !dto.getFileIds().isEmpty()) {
            Long firstFileId = dto.getFileIds().get(0);
            File inputFile = fileMapper.selectById(firstFileId);
            if (inputFile != null) {
                String origName = inputFile.getOriginalName();
                String baseName = origName.replaceAll("\\.(fastq|fq|gz|fasta|fa|txt|csv).*$", "");
                String dateStr = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                taskName = String.format("[%s] %s (%s)", baseName, pipeline.getName(), dateStr);
            }
        }
        task.setTaskName(taskName);
        task.setStatus("PENDING");
        task.setParameters(dto.getParams());
        task.setProgress(0);
        task.setProgressMsg("任务排队中...");
        this.save(task);

        if (dto.getFileIds() != null) {
            for (Long fid : dto.getFileIds()) {
                AnalysisTaskFile tf = new AnalysisTaskFile();
                tf.setTaskId(task.getId());
                tf.setFileId(fid);
                tf.setFileRole("input");
                taskFileMapper.insert(tf);
            }
        }
        return task.getId();
    }

    // MyBatis-Plus 的局部更新
    private void updateTaskStatus(Long taskId, String status, Integer progress, String msg, boolean isStart) {
        AnalysisTask t = new AnalysisTask();
        t.setId(taskId);
        t.setStatus(status);
        t.setProgress(progress);
        t.setProgressMsg(msg);
        if (isStart) t.setStartedAt(LocalDateTime.now());
        if ("COMPLETED".equals(status)) t.setCompletedAt(LocalDateTime.now());
        this.updateById(t);  //UPDATE 非空的字段
    }

    // 前端界面右上角的数据统计
    @Override
    public Map<String, Object> getDashboardStats(Long userId, Long projectId) {
        // 如果前端传来项目id，则查询当前项目下的统计数据
        LambdaQueryWrapper<AnalysisTask> qw = new LambdaQueryWrapper<AnalysisTask>().eq(AnalysisTask::getUserId, userId);
        if (projectId != null) {
            qw.eq(AnalysisTask::getProjectId, projectId);
        }
        long totalTasks = this.count(qw);

        // 统计用户上传的文件
        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getUserId, userId);
        fileWrapper.ne(File::getStatus, "deleted");
        fileWrapper.ne(File::getFileSource, "generate");
        if (projectId != null) {
            fileWrapper.eq(File::getProjectId, projectId);
        }
        long totalFiles = fileMapper.selectCount(fileWrapper);

        // 统计任务产出的文件
        LambdaQueryWrapper<File> outputWrapper = new LambdaQueryWrapper<>();
        outputWrapper.eq(File::getUserId, userId);
        outputWrapper.ne(File::getStatus, "deleted");
        outputWrapper.eq(File::getFileSource, "generate");
        if (projectId != null) {
            outputWrapper.eq(File::getProjectId, projectId);
        }
        long totalOutputs = fileMapper.selectCount(outputWrapper);

        Map<String, Object> s = new HashMap<>();
        s.put("totalTasks", totalTasks);
        s.put("totalFiles", totalFiles);
        s.put("totalOutputs", totalOutputs);

        return s;
    }

    // 任务详情页
    @Override
    public Map<String, Object> getTaskDetails(Long taskId, Long userId) {
        AnalysisTask task = taskMapper.selectById(taskId);
        Map<String, Object> res = new HashMap<>();
        res.put("task", task);

        List<AnalysisTaskFile> tfs = taskFileMapper.selectList(new LambdaQueryWrapper<AnalysisTaskFile>().eq(AnalysisTaskFile::getTaskId, taskId));
        List<File> ins = new ArrayList<>(), outs = new ArrayList<>();
        if (!tfs.isEmpty()) {
            List<File> details = fileMapper.selectBatchIds(tfs.stream().map(AnalysisTaskFile::getFileId).collect(Collectors.toList()));
            for (AnalysisTaskFile tf : tfs) {
                details.stream().filter(f -> f.getId().equals(tf.getFileId())).findFirst().ifPresent(f -> {
                    if ("input".equals(tf.getFileRole())) ins.add(f); // 根据文件的来源 精确将文件分发到输入池和输出池
                    else outs.add(f);
                });
            }
        }
        res.put("inputFiles", ins);
        res.put("resultFiles", outs);

        List<Map<String, String>> logs = new ArrayList<>();

        java.io.File lFile = new java.io.File(STORAGE_ROOT + "task_results/task_" + taskId + "/process.log"); // RNA-Seq
        if (!lFile.exists()) {
            lFile = new java.io.File(STORAGE_ROOT + "task_results/task_" + taskId + "/gwas_process.log"); // GWAS
        }
        if (!lFile.exists()) {
            lFile = new java.io.File(STORAGE_ROOT + "task_results/task_" + taskId + "/qiime2_process.log"); // 16S
        }

        if (lFile.exists()) {
            try (BufferedReader br = new BufferedReader(new java.io.FileReader(lFile))) {
                String line;
                // 后端驱动前端 UI 状态
                while ((line = br.readLine()) != null) {
                    Map<String, String> m = new HashMap<>();
                    m.put("time", LocalDateTime.now().toString().substring(11, 19));
                    if (line.contains("SUCCESS") || line.contains("🎉")) {
                        m.put("level", "SUCCESS");
                    } else if (line.contains("ERROR") || line.contains("Failed")) {
                        m.put("level", "ERROR");
                    } else {
                        m.put("level", "INFO");
                    }
                    m.put("msg", line);
                    logs.add(m);
                }
            } catch (Exception e) {
                log.error("读取任务日志失败", e);
            }
        }
        res.put("logs", logs);
        return res;
    }

    // 一次性算出该用户所有项目下的任务数量
    @Override
    public Map<Long, Integer> getProjectTaskCountMap(Long userId) {
        List<Map<String, Object>> list = baseMapper.countTasksGroupByProject(userId);
        Map<Long, Integer> m = new HashMap<>();
        for (Map<String, Object> item : list) {
            m.put(((Number) item.get("project_id")).longValue(), ((Number) item.get("task_count")).intValue());
        }
        return m;
    }


// =========================================================================
// RNA-Seq 多样本端到端分析流程
// =========================================================================

    @Override
    @Async
    public void simulateRnaSeqExecution(Long taskId) {
        log.info("====== 接管 RNA-Seq 端到端多样本任务 [{}] ======", taskId);
        updateTaskStatus(taskId, "RUNNING", 5, "正在初始化多样本比对队列与基建...", true);

        AnalysisTask task = this.getById(taskId);
        String paramsJson = task.getParameters();

        String relativeDir = "task_results/task_" + taskId;
        String hostDataDir = STORAGE_ROOT + relativeDir;
        String logFilePath = hostDataDir + "/process.log";

        try {
            Files.createDirectories(Paths.get(hostDataDir));
            Files.createDirectories(Paths.get(hostDataDir, "raw_data"));

            JSONObject params = JSON.parseObject(paramsJson);
            String threads = params.getString("threads") != null ? params.getString("threads") : "4";
            List<Long> controlIds = params.getJSONArray("controlGroup").toJavaList(Long.class); // 提取前端传来的对照组文件 ID 列表
            List<Long> treatIds = params.getJSONArray("treatGroup").toJavaList(Long.class); // 提取前端传来的处理组文件 ID 列表

            AnalysisPipeline pipeline = pipelineMapper.selectById(task.getPipelineId());
            // 参考基因组
            if (pipeline.getRefFaFileId() != null) {
                File faFile = fileMapper.selectById(pipeline.getRefFaFileId());
                Files.copy(Paths.get(STORAGE_ROOT + faFile.getStoragePath()), Paths.get(hostDataDir, "ref.fa"), StandardCopyOption.REPLACE_EXISTING);
            }
            // 基因注释文件
            if (pipeline.getRefGtfFileId() != null) {
                File gtfFile = fileMapper.selectById(pipeline.getRefGtfFileId());
                Files.copy(Paths.get(STORAGE_ROOT + gtfFile.getStoragePath()), Paths.get(hostDataDir, "ref.gtf"), StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> allSampleNames = new ArrayList<>();
            Map<String, String> sampleConditionMap = new LinkedHashMap<>();

            for (Long fid : controlIds) {
                File f = fileMapper.selectById(fid);
                if (f != null) {
                    Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(hostDataDir, "raw_data", f.getOriginalName()), StandardCopyOption.REPLACE_EXISTING);
                    String baseName = f.getOriginalName().replaceAll("\\.(fastq|fq)(\\.gz)?$", "");
                    allSampleNames.add(baseName);
                    // 只要是 controlIds 里的文件，全部在字典里打上 Control
                    sampleConditionMap.put(baseName, "Control");
                }
            }

            for (Long fid : treatIds) {
                File f = fileMapper.selectById(fid);
                if (f != null) {
                    Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(hostDataDir, "raw_data", f.getOriginalName()), StandardCopyOption.REPLACE_EXISTING);
                    String baseName = f.getOriginalName().replaceAll("\\.(fastq|fq)(\\.gz)?$", "");
                    allSampleNames.add(baseName);
                    // 只要是 treatIds 里的文件，全部在字典里打上 Treat
                    sampleConditionMap.put(baseName, "Treat");
                }
            }

            updateTaskStatus(taskId, "RUNNING", 15, "启动第一级引擎：串行比对与定量...", false);

            String sh = "#!/bin/bash\n" +
                    "set -e\n" +  // 只要出现错误就停止
                    "cd /workspace\n" +
                    "echo '[INFO] 开始建立基因组索引...'\n" +
                    // 给参考基因组建立索引
                    "hisat2-build ref.fa idx > /dev/null\n" +

                    // Linux for 循环，寻找raw_data文件夹下所有带有 .fq 的文件
                    "for fq in raw_data/*.fq*; do\n" +
                    "    base=$(basename $fq | sed -E 's/\\.(fastq|fq)(\\.gz)?//g')\n" +
                    "    echo \"[INFO] =======================================\"\n" +
                    "    echo \"[INFO] 正在全速处理样本: $base\"\n" +
                    "    fastp -i $fq -o ${base}_clean.fq -w " + threads + " 2>/dev/null\n" +
                    "    hisat2 -x idx -U ${base}_clean.fq -S ${base}_aligned.sam -p " + threads + " 2>/dev/null\n" +
                    "    featureCounts -T " + threads + " -a ref.gtf -o ${base}_counts.txt ${base}_aligned.sam 2>/dev/null\n" +
                    "done\n" +
                    "echo '[SUCCESS] 所有样本比对与定量完成！'\n";

            java.io.File sFile = new java.io.File(hostDataDir, "run_bulk.sh");
            Files.write(sFile.toPath(), sh.getBytes());
            sFile.setExecutable(true, false);

            List<String> cmd1 = Arrays.asList("docker", "run", "--rm", "-v", hostDataDir.replace("\\", "/") + ":/workspace", "bio-os/rna-seq-sandbox:v1", "bash", "/workspace/run_bulk.sh");
            runProcessAndLog(cmd1, logFilePath);

            updateTaskStatus(taskId, "RUNNING", 60, "提取定量矩阵，准备 DESeq2 差异对撞...", false);


            Set<String> allGenes = new HashSet<>(); // 把所有样本里出现过的基因求了一个数学上的并集
            Map<String, Map<String, Integer>> sampleDataMap = new LinkedHashMap<>();

            for (String sample : allSampleNames) {
                Path countPath = Paths.get(hostDataDir, sample + "_counts.txt");
                if (Files.exists(countPath)) {
                    Map<String, Integer> counts = parseCountsFile(countPath);
                    allGenes.addAll(counts.keySet());
                    sampleDataMap.put(sample, counts);

                }
            }

            Path countsCsv = Paths.get(hostDataDir, "counts.csv");

            Path metaCsv = Paths.get(hostDataDir, "metadata.csv");
            Files.writeString(metaCsv, "Sample,Condition\n");
            // 直接去 allSampleNames 里面分类好的样本名称和实验分组写入metadata.csv
            for (String sample : allSampleNames) {
                Files.writeString(metaCsv, sample + "," + sampleConditionMap.get(sample) + "\n", StandardOpenOption.APPEND);
            }

            StringBuilder countHeader = new StringBuilder("Gene");
            for (String sample : allSampleNames) countHeader.append(",").append(sample);
            Files.writeString(countsCsv, countHeader.append("\n").toString());

            // 确保哪怕某个基因在某个特定样本中发生表达量丢失，CSV 矩阵的维度也绝对不会发生断层错位。
            for (String gene : allGenes) {
                StringBuilder row = new StringBuilder(gene);
                for (String sample : allSampleNames) {
                    row.append(",").append(sampleDataMap.containsKey(sample) ? sampleDataMap.get(sample).getOrDefault(gene, 0) : 0);
                }
                Files.writeString(countsCsv, row.append("\n").toString(), StandardOpenOption.APPEND);
            }

            updateTaskStatus(taskId, "RUNNING", 80, "启动第二级引擎：DESeq2 统计学计算...", false);

            writePythonScriptToSandbox(Paths.get(hostDataDir, "run_deseq2.py"));

            List<String> cmd2 = Arrays.asList("docker", "run", "--rm", "-v", hostDataDir.replace("\\", "/") + ":/workspace", "python-sandbox:latest", "python", "/workspace/run_deseq2.py", "/workspace/counts.csv", "/workspace/metadata.csv", "/workspace/results.csv");
            runProcessAndLog(cmd2, logFilePath);

            Path resultCsv = Paths.get(hostDataDir, "results.csv");
            if (Files.exists(resultCsv)) {
                parseAndSaveDeseq2Results(resultCsv, Collections.singletonList(taskId), Collections.singletonList(taskId));

                registerRnaSeqE2EOutputs(taskId, hostDataDir, relativeDir);

                updateTaskStatus(taskId, "COMPLETED", 100, "分析完毕，火山图差异数据已封存！", false);
            } else {
                updateTaskStatus(taskId, "FAILED", 0, "第二级引擎未产出 results.csv", false);
            }

        } catch (Exception e) {
            log.error("E2E RNA-Seq 任务调度失败", e);
            updateTaskStatus(taskId, "FAILED", 0, "运行异常: " + e.getMessage(), false);
        }
    }

    // 将Python脚本写入沙盒
    private void writePythonScriptToSandbox(Path scriptPath) throws IOException {
        String scriptContent = "import sys\n" +
                "import pandas as pd\n" +
                "from pydeseq2.dds import DeseqDataSet\n" +
                "from pydeseq2.ds import DeseqStats\n" +

                "counts_df = pd.read_csv(sys.argv[1], index_col=0).T\n" +
                "meta_df = pd.read_csv(sys.argv[2], index_col=0)\n" +
                "counts_df = counts_df.loc[meta_df.index]\n" +

                "dds = DeseqDataSet(counts=counts_df, metadata=meta_df, design_factors='Condition')\n" +

                "dds.deseq2()\n" +
                "stat_res = DeseqStats(dds, contrast=('Condition', 'Treat', 'Control'))\n" +

                "stat_res.summary()\n" +
                "stat_res.results_df.to_csv(sys.argv[3])\n";
        Files.writeString(scriptPath, scriptContent);
    }

    // docker进程调度与实时监测输出
    private void runProcessAndLog(List<String> command, String logFilePath) throws Exception {
        List<String> finalCmd = new ArrayList<>();
        // 兼容Windows系统，Docker命令前加上 cmd /c
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            finalCmd.add("cmd"); finalCmd.add("/c");
        }
        finalCmd.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(finalCmd);
        pb.redirectErrorStream(true); // 合并linux操作系统里面的标准输出流和标准错误流

        // 排除一些干扰性的环境变量
        Map<String, String> env = pb.environment();
        env.remove("DOCKER_HOST"); env.remove("DOCKER_TLS_VERIFY"); env.remove("DOCKER_CERT_PATH");

        // 把容器里面打印出来的日志写入java内存，然后同步写入日志文件中
        try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath), true)) {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logWriter.write(line + "\n");
                    logWriter.flush(); // 刷盘
                    log.info("[E2E Sandbox] {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Docker 容器异常退出，退出码: " + exitCode);
            }
        }
    }

    // 用来解析上游featureCounts算出来的counts.txt
    private Map<String, Integer> parseCountsFile(Path countPath) throws IOException {
        Map<String, Integer> countMap = new LinkedHashMap<>(); //保证插入的顺序
        List<String> lines = Files.readAllLines(countPath);
        for (String line : lines) {
            if (line.startsWith("#") || line.startsWith("Geneid")) continue; // 拦截第一行的简介和第二行的表头
            String[] parts = line.split("\t");  // 上游 C++ 写文件用的是 \t（制表符，Tab 键）
            if (parts.length >= 7) {
                countMap.put(parts[0].trim(), Integer.parseInt(parts[6].trim()));  // 只获取第一列基因名和第六列的表达量计数
            }
        }
        return countMap;
    }

    // 数据持久化  放分析结果的数据到分析结果表
    private void parseAndSaveDeseq2Results(Path resultCsvPath, List<Long> controlTaskIds, List<Long> treatTaskIds) throws IOException {
        // 获取对照组和处理组的第一个文件任务 ID
        Long representControlId = controlTaskIds.get(0);
        Long representTreatId = treatTaskIds.get(0);

        // 删除一些没必要的脏数据
        taskDiffExpressionMapper.delete(new LambdaQueryWrapper<TaskDiffExpression>()
                .eq(TaskDiffExpression::getControlTaskId, representControlId)
                .eq(TaskDiffExpression::getTreatTaskId, representTreatId));

        List<String> lines = Files.readAllLines(resultCsvPath);
        for (int i = 1; i < lines.size(); i++) { // 去除表头，按行读取到数据库
            String[] parts = lines.get(i).split(",");
            if (parts.length < 7) continue;  //DESeq2 的标准输出包含基因名、均值、Log2FC、P值等，固定为 7 列

            // 绘制火山图的三个重要指标
            String geneId = parts[0];  // 基因名称
            String log2fcStr = parts[2]; // 表达差异倍数
            String padjStr = parts[6];  // 多重假设检验校正后的显著性 P 值

            // 生物学测序存在大量的无表达基因（全为 0）
            // 这会导致底层 Python 计算差异倍数时触发“除以零”异常，从而输出 NaN，需要将其拦截
            double log2fc = (log2fcStr.equalsIgnoreCase("NaN") || log2fcStr.equalsIgnoreCase("NA")) ? 0.0 : Double.parseDouble(log2fcStr);
            double padj = (padjStr.equalsIgnoreCase("NaN") || padjStr.equalsIgnoreCase("NA")) ? 1.0 : Double.parseDouble(padjStr);

            TaskDiffExpression diffRecord = new TaskDiffExpression();
            diffRecord.setControlTaskId(representControlId);
            diffRecord.setTreatTaskId(representTreatId);
            diffRecord.setGeneId(geneId);
            diffRecord.setControlCount(0);
            diffRecord.setTreatCount(0);
            diffRecord.setLog2FoldChange(log2fc);
            diffRecord.setPValue(padj);

            taskDiffExpressionMapper.insert(diffRecord);
        }
    }

    // 将产出的结果文件放入bio_files 和 analysis_task_file表
    private void registerRnaSeqE2EOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles;
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith("counts.csv") || name.endsWith("results.csv") || name.endsWith("metadata.csv");
                    })
                    .collect(Collectors.toList());
        }

        for (Path p : resultFiles) {
            java.io.File f = p.toFile();
            String relativeToRoot = Paths.get(hostDataDir).relativize(p).toString().replace("\\", "/");
            String fileName = f.getName();

            File df = new File();
            df.setOriginalName(fileName);
            df.setStoredName("task_" + taskId + "_" + fileName);
            int dotIdx = fileName.lastIndexOf('.');
            df.setFileExt(dotIdx > -1 ? fileName.substring(dotIdx) : ".csv");
            df.setFileType("rna_seq_result");
            df.setSizeBytes(f.length());
            df.setStoragePath(relDir + "/" + relativeToRoot);
            df.setUserId(this.getById(taskId).getUserId());
            df.setProjectId(this.getById(taskId).getProjectId());
            df.setStatus("ready");
            df.setFileSource("generate");
            df.setUploadTime(LocalDateTime.now());
            df.setMd5Hash(UUID.randomUUID().toString().replace("-", ""));
            df.setUpdateTime(LocalDateTime.now());
            fileMapper.insert(df);

            AnalysisTaskFile tf = new AnalysisTaskFile();
            tf.setTaskId(taskId);
            tf.setFileId(df.getId());
            tf.setFileRole("output");
            taskFileMapper.insert(tf);
        }
    }

    // 前端对可视化数据的获取（绘制火山图）
    @Override
    public List<TaskDiffExpression> getDiffAnalysisResult(Long controlTaskId, Long treatTaskId) {
        return taskDiffExpressionMapper.selectList(
                new LambdaQueryWrapper<TaskDiffExpression>()
                        .eq(TaskDiffExpression::getControlTaskId, controlTaskId)
                        .eq(TaskDiffExpression::getTreatTaskId, treatTaskId)
        );
    }



// =========================================================================
// GWAS 引擎流
// =========================================================================

    @Override
    @Async
    public void simulateGwasExecution(Long taskId) {
        log.info("====== 接管端到端全基因组 GWAS 任务 [{}] ======", taskId);
        updateTaskStatus(taskId, "RUNNING", 5, "正在初始化底层算法环境与文件调度...", true);

        String relativeDir = "task_results/task_" + taskId;
        String hostDataDir = STORAGE_ROOT + relativeDir;
        String containerDataDir = "/workspace";
        String logFilePath = hostDataDir + "/gwas_process.log";

        try {
            Files.createDirectories(Paths.get(hostDataDir));

            prepareGwasExecutionEnvironment(taskId, hostDataDir);

            // 组装docker执行命令
            List<String> command = new ArrayList<>();
            // windows系统适配
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                command.add("cmd");
                command.add("/c");
            }

            command.add("docker");
            command.add("run");
            command.add("--rm");
            command.add("-v");
            command.add(hostDataDir.replace("\\", "/") + ":" + containerDataDir);
            command.add("bio-os/gatk4-gwas-e2e:v2");
            command.add("bash");
            command.add(containerDataDir + "/run.sh");
            // docker run --rm -v D:/bio_data/task_results/task_123:/workspace bio-os/gatk4-gwas-e2e:v2 bash /workspace/run.sh

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.remove("DOCKER_HOST");
            env.remove("DOCKER_TLS_VERIFY");
            env.remove("DOCKER_CERT_PATH");

            try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath))) {
                Process process = pb.start(); // 拉起 docker 容器
                updateTaskStatus(taskId, "RUNNING", 30, "全流程调度主脚本执行中(包含 Joint Genotyping)...", false);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    // 抓到的每一行日志，既被写入到了宿主机的 gwas_process.log 中，也被打印到控制台上
                    while ((line = reader.readLine()) != null) {
                        logWriter.write(line + "\n");
                        logWriter.flush();
                        log.info("[GWAS Sandbox] {}", line);
                    }
                }
                // 在 Linux 系统中，程序正常结束会返回 0。任何非 0 的数字都代表某种报错
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    registerGwasOutputs(taskId, hostDataDir, relativeDir);
                    updateTaskStatus(taskId, "COMPLETED", 100, "多样本联合 GWAS 分析完毕，已成功入库！", false);
                } else {
                    updateTaskStatus(taskId, "FAILED", 0, "算法运行崩溃，退出码: " + exitCode, false);
                }
            }
        } catch (Exception e) {
            log.error("GWAS 任务调度失败", e);
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.length() > 100) errMsg = errMsg.substring(0, 100) + "...";
            updateTaskStatus(taskId, "FAILED", 0, "运行异常: " + errMsg, false);
        }
    }

    private void prepareGwasExecutionEnvironment(Long taskId, String taskDir) throws Exception {

        Files.createDirectories(Paths.get(taskDir, "raw_data")); // 存放原始数据
        Files.createDirectories(Paths.get(taskDir, "output"));   // 存放单样本结果
        Files.createDirectories(Paths.get(taskDir, "cohort_output")); // 存放群体结果

        List<AnalysisTaskFile> tFiles = taskFileMapper.selectList(
                new LambdaQueryWrapper<AnalysisTaskFile>().eq(AnalysisTaskFile::getTaskId, taskId).eq(AnalysisTaskFile::getFileRole, "input")
        );

        // 定义标志位 校验用户文件上传是否齐全
        boolean hasRef = false, hasPheno = false;
        int fqCount = 0;

        for (AnalysisTaskFile tf : tFiles) {
            File f = fileMapper.selectById(tf.getFileId());
            if (f == null) continue;
            String name = f.getOriginalName().toLowerCase();

            if (name.endsWith(".fa") || name.endsWith(".fasta")) {
                // 如果是参考基因组，拷贝到根目录并重命名为统一的 "reference.fasta"
                Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(taskDir, "reference.fasta"), StandardCopyOption.REPLACE_EXISTING);
                hasRef = true;
            } else if (name.endsWith(".csv")) {
                // 如果是表型文件，拷贝到根目录重命名为 "phenotype.csv"
                Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(taskDir, "phenotype.csv"), StandardCopyOption.REPLACE_EXISTING);
                hasPheno = true;
            } else if (name.endsWith(".fastq") || name.endsWith(".fq") || name.endsWith(".gz")) {
                // 如果是测序数据，原封不动地丢进 raw_data 文件夹，并统计数量
                Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(taskDir, "raw_data", f.getOriginalName()), StandardCopyOption.REPLACE_EXISTING);
                fqCount++;
            }
        }

        if (!hasRef || !hasPheno || fqCount < 2) {
            throw new RuntimeException("文件校验失败：缺少 reference.fasta、phenotype.csv 或 FastQ 测序数据不完整");
        }

        String processSingleSh = "#!/bin/bash\n" +
                "set -e\n" +

                "if [ -z \"$1\" ]; then exit 1; fi\n" +
                "SAMPLE_ID=$1\n" +

                "THREADS=4\n" +   // 设置线程数 4
                // 定义各种文件的路径
                "REF_GENOME=\"/workspace/reference.fasta\"\n" +
                "RAW_R1=\"/workspace/raw_data/${SAMPLE_ID}_R1.fastq\"\n" +
                "RAW_R2=\"/workspace/raw_data/${SAMPLE_ID}_R2.fastq\"\n" +
                "OUT_DIR=\"/workspace/output/${SAMPLE_ID}\"\n" +
                "TMP_DIR=\"${OUT_DIR}/tmp\"\n" +

                "mkdir -p ${OUT_DIR} ${TMP_DIR}\n" +    //  建文件夹


                "echo \"========== 开始处理样本: ${SAMPLE_ID} ==========\"\n" +
                "fastp -i ${RAW_R1} -I ${RAW_R2} -o ${OUT_DIR}/${SAMPLE_ID}_clean_R1.fq.gz -O ${OUT_DIR}/${SAMPLE_ID}_clean_R2.fq.gz -w ${THREADS} 2> /dev/null\n" +
                "bwa mem -t ${THREADS} -M -R \"@RG\\tID:${SAMPLE_ID}\\tSM:${SAMPLE_ID}\\tPL:ILLUMINA\\tLB:lib1\" ${REF_GENOME} ${OUT_DIR}/${SAMPLE_ID}_clean_R1.fq.gz ${OUT_DIR}/${SAMPLE_ID}_clean_R2.fq.gz 2> /dev/null | samtools sort -@ ${THREADS} -O bam -o ${OUT_DIR}/${SAMPLE_ID}.sorted.bam -\n" +
                "samtools index -@ ${THREADS} ${OUT_DIR}/${SAMPLE_ID}.sorted.bam\n" +
                "gatk MarkDuplicates -I ${OUT_DIR}/${SAMPLE_ID}.sorted.bam -O ${OUT_DIR}/${SAMPLE_ID}.dedup.bam -M ${OUT_DIR}/${SAMPLE_ID}.metrics.txt --TMP_DIR ${TMP_DIR} --QUIET true\n" +
                "samtools index -@ ${THREADS} ${OUT_DIR}/${SAMPLE_ID}.dedup.bam\n" +
                "gatk HaplotypeCaller -R ${REF_GENOME} -I ${OUT_DIR}/${SAMPLE_ID}.dedup.bam -O ${OUT_DIR}/${SAMPLE_ID}.g.vcf.gz -ERC GVCF --native-pair-hmm-threads ${THREADS} --QUIET true\n";

        Files.write(Paths.get(taskDir, "process_single_sample.sh"), processSingleSh.getBytes());

        String jointCallingSh = "#!/bin/bash\n" +
                "set -e\n" +
                "REF_GENOME=\"/workspace/reference.fasta\"\n" +
                "GVCF_DIR=\"/workspace/output\"\n" +
                "COHORT_OUT=\"/workspace/cohort_output\"\n" +
                "mkdir -p ${COHORT_OUT}\n" +

                "find ${GVCF_DIR} -name \"*.g.vcf.gz\" > ${COHORT_OUT}/cohort.sample_map.list\n" +
                "gatk CombineGVCFs -R ${REF_GENOME} -V ${COHORT_OUT}/cohort.sample_map.list -O ${COHORT_OUT}/merged.g.vcf.gz\n" +
                "gatk GenotypeGVCFs -R ${REF_GENOME} -V ${COHORT_OUT}/merged.g.vcf.gz -O ${COHORT_OUT}/raw_cohort.vcf.gz\n" +
                "plink --vcf ${COHORT_OUT}/raw_cohort.vcf.gz --allow-extra-chr --keep-allele-order --recode vcf-iid bgz --out ${COHORT_OUT}/final_gwas_ready\n";
        Files.write(Paths.get(taskDir, "joint_calling_and_filter.sh"), jointCallingSh.getBytes());

        String runSh = "#!/bin/bash\n" +
                "set -e\n" +
                "cd /workspace\n" +

                // 给参考基因组文件建索引
                "bwa index reference.fasta\n" +
                "samtools faidx reference.fasta\n" +
                "gatk CreateSequenceDictionary -R reference.fasta\n" +

                "awk -F ',' 'NR>1 {print $1}' phenotype.csv | while read sample; do\n" +
                "    sample=$(echo $sample | tr -d '\\r')\n" +
                "    echo \"➡️ 正在提交任务: $sample\"\n" +
                "    bash process_single_sample.sh $sample\n" +
                "done\n" +

                "bash joint_calling_and_filter.sh\n" +

                "echo \"========== 开始执行 vcf2gwas LMM 模型 ==========\"\n" +
                "conda run -n myenv vcf2gwas -v cohort_output/raw_cohort.vcf.gz -pf phenotype.csv -p yield -lmm\n" +
                "echo \"[SUCCESS] 全部流程已打通！\"\n";

        java.io.File sFile = new java.io.File(taskDir, "run.sh");
        Files.write(sFile.toPath(), runSh.getBytes());
        sFile.setExecutable(true, false);
    }

    // 负责结果文件的持久化记录
    private void registerGwasOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        if (name.endsWith("phenotype.csv") || name.endsWith("metadata.csv")) {
                            return false;
                        }
                        // 正则捕获底层的 LMM 产出文件或相关日志
                        return name.contains("vcf2gwas") || name.endsWith(".csv") || name.endsWith(".assoc") || name.endsWith(".assoc.txt");
                    })
                    .collect(Collectors.toList());
        }

        for (Path p : resultFiles) {
            java.io.File f = p.toFile();
            // 将 Windows 的反斜杠统一替换为 Web 友好的正斜杠，生成相对路径
            String relativeToRoot = Paths.get(hostDataDir).relativize(p).toString().replace("\\", "/");
            String fileName = f.getName();

            File df = new File();
            df.setOriginalName(fileName);
            df.setStoredName("task_" + taskId + "_" + fileName);
            int dotIdx = fileName.lastIndexOf('.');
            df.setFileExt(dotIdx > -1 ? fileName.substring(dotIdx) : ".txt");
            df.setFileType("gwas_result");
            df.setSizeBytes(f.length());
            df.setStoragePath(relDir + "/" + relativeToRoot);
            df.setUserId(this.getById(taskId).getUserId());
            df.setProjectId(this.getById(taskId).getProjectId());
            df.setStatus("ready");
            df.setFileSource("generate");
            df.setUploadTime(LocalDateTime.now());
            df.setMd5Hash(UUID.randomUUID().toString().replace("-", ""));
            df.setUpdateTime(LocalDateTime.now());
            fileMapper.insert(df);

            AnalysisTaskFile tf = new AnalysisTaskFile();
            tf.setTaskId(taskId);
            tf.setFileId(df.getId());
            tf.setFileRole("output");
            taskFileMapper.insert(tf);

            // 如果系统识别到这是包含 P-value 的核心报表，则调用parseAndSaveGwasResults方法
            if (fileName.endsWith(".csv") || fileName.endsWith(".assoc.txt")) {
                parseAndSaveGwasResults(taskId, f);
            }
        }
    }

    // 读取产出的结果文件存入数据库
    private void parseAndSaveGwasResults(Long taskId, java.io.File resultFile) {
        log.info("====== 开始解析并入库 GWAS (LMM) 结果文件: {} ======", resultFile.getName());

        // 建立一个 List 作为内存缓冲区，用于批量插入
        List<TaskGwasResult> batchList = new ArrayList<>();

        // 使用 BufferedReader 逐行读取，将内存占用维持在极低水平
        try (BufferedReader br = new BufferedReader(new java.io.FileReader(resultFile))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 丢弃表头行（列名不需要入库）
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // \s+ 能够无视多个空格或制表符（Tab）的混用，精准拆分出数据列
                String[] parts = line.split("\\s+");

                if (parts.length >= 12) {
                    TaskGwasResult res = new TaskGwasResult();
                    res.setTaskId(taskId);
                    res.setChr(parts[0]); // 提取染色体编号
                    res.setSnp(parts[1]); // 提取 SNP 标识符
                    res.setBp(Long.parseLong(parts[2]));  // 提取物理坐标 (Base Pair)

                    res.setAltAllele(parts[4]); // 变异碱基
                    res.setRefAllele(parts[5]); // 参考碱基
                    res.setMaf(Double.parseDouble(parts[6])); // 提取突变频率
                    res.setBeta(Double.parseDouble(parts[7])); // 提取效应值 (Beta)

                    res.setPValue(Double.parseDouble(parts[11])); // 提取极其核心的 P-value
                    res.setCreatedAt(LocalDateTime.now());

                    batchList.add(res); // 将解析好的对象放入内存缓冲池

                    // 当缓冲池攒够 1000 条记录时，执行一次数据库提交，然后清空缓冲池。
                    if (batchList.size() >= 1000) {
                        for (TaskGwasResult item : batchList) {
                            taskGwasResultMapper.insert(item);
                        }
                        batchList.clear();
                    }
                }
            }

            if (!batchList.isEmpty()) {
                for (TaskGwasResult item : batchList) {
                    taskGwasResultMapper.insert(item);
                }
            }

            log.info("🎉 任务 [{}] GWAS 变异关联结果已全部解析入库完毕！", taskId);
        } catch (Exception e) {
            log.error("任务 [{}] 解析 LMM 结果文件失败", taskId, e);
        }
    }

    @Override
    public Map<String, Object> getGwasManhattanData(Long taskId) {
        log.info("====== 开始组装任务 [{}] 的 Echarts 曼哈顿图数据 ======", taskId);

        // 直接按“染色体 (Chr)”和“物理坐标 (Bp)”取出有序序列
        List<TaskGwasResult> results = taskGwasResultMapper.selectList(
                new LambdaQueryWrapper<TaskGwasResult>()
                        .eq(TaskGwasResult::getTaskId, taskId)
                        .orderByAsc(TaskGwasResult::getChr, TaskGwasResult::getBp)
        );

        Map<String, List<TaskGwasResult>> groupedByChr = results.stream()
                .collect(Collectors.groupingBy(TaskGwasResult::getChr, LinkedHashMap::new, Collectors.toList()));

        List<String> chromosomes = new ArrayList<>(groupedByChr.keySet());
        List<Map<String, Object>> seriesList = new ArrayList<>();
        double maxLogP = 0;

        for (String chr : chromosomes) {
            List<TaskGwasResult> chrData = groupedByChr.get(chr);
            List<Object[]> dataPoints = new ArrayList<>();

            // 在绘制曼哈顿图时，必须对 P 值取负对数，防止P值可能会因为硬件精度极限被截断为0，给个兜底数
            // 直接对 0 取对数，Java 会抛出 Infinity 或 NaN 异常，导致前端图表崩溃。
            for (TaskGwasResult r : chrData) {
                double p = (r.getPValue() == null || r.getPValue() <= 0) ? 1e-300 : r.getPValue();
                double negLogP = -Math.log10(p);

                // 自适应坐标系的高度计算
                if (negLogP > maxLogP) {
                    maxLogP = negLogP;
                }

                dataPoints.add(new Object[]{
                        r.getBp(),
                        negLogP,
                        r.getSnp()
                });
            }

            // 直接在后端拼接出了 Echarts 所需的 scatter（散点图）的 series 结构。
            // 前端拿到这段 JSON 后，直接 setOption(data) 即可瞬间完成高并发渲染。
            Map<String, Object> series = new HashMap<>();
            series.put("name", chr);
            series.put("type", "scatter");
            series.put("symbolSize", 4);
            series.put("data", dataPoints);
            seriesList.add(series);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("chromosomes", chromosomes);
        responseData.put("series", seriesList);
        responseData.put("maxLogP", Math.ceil(maxLogP + 1));

        return responseData;
    }


// =========================================================================
// 16S 扩增子分析流
// =========================================================================

    @Override
    @Async  // 异步执行
    public void simulateMicrobiomeExecution(Long taskId) {
        log.info("====== 接管 16S 扩增子 (QIIME2 VSEARCH) 任务 [{}] ======", taskId);
        updateTaskStatus(taskId, "RUNNING", 5, "正在初始化微生物组运算沙盒...", true);

        String relativeDir = "task_results/task_" + taskId;
        String hostDataDir = STORAGE_ROOT + relativeDir;
        String containerDataDir = "/workspace";
        String logFilePath = hostDataDir + "/qiime2_process.log";

        try {
            Files.createDirectories(Paths.get(hostDataDir));

            prepareMicrobiomeEnvironment(taskId, hostDataDir);

            List<String> command = new ArrayList<>();
            // Docker 在 Windows 的命令行执行需要借助 cmd /c 作为前置宿主
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                command.add("cmd");
                command.add("/c");
            }
            command.add("docker");
            command.add("run");
            command.add("--rm");
            command.add("-v");
            command.add(hostDataDir.replace("\\", "/") + ":" + containerDataDir);
            command.add("bio-qiime2-vsearch:v1");
            command.add("bash");
            command.add(containerDataDir + "/run.sh");

            ProcessBuilder pb = new ProcessBuilder(command);

            pb.redirectErrorStream(true); // 合并标准输出和标准错误管道，

            // 强行清除这三个变量，确保 docker run 命令 100% 作用于当前宿主机的原生 Docker 环境上
            Map<String, String> env = pb.environment();
            env.remove("DOCKER_HOST");
            env.remove("DOCKER_TLS_VERIFY");
            env.remove("DOCKER_CERT_PATH");

            try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath))) {

                Process process = pb.start();  // 拉起docker容器进程

                updateTaskStatus(taskId, "RUNNING", 20, "QIIME2 引擎启动，正在进行 OTU 聚类与物种注释...", false);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logWriter.write(line + "\n");
                        logWriter.flush();
                        log.info("[QIIME2 Sandbox] {}", line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    registerMicrobiomeOutputs(taskId, hostDataDir, relativeDir);
                    updateTaskStatus(taskId, "COMPLETED", 100, "16S 物种多样性分析完毕，结果已封存！", false);
                } else {
                    updateTaskStatus(taskId, "FAILED", 0, "QIIME2 引擎崩溃，退出码: " + exitCode, false);
                }
            }
        } catch (Exception e) {
            log.error("16S 任务调度失败", e);
            String errMsg = e.getMessage();
            if (errMsg != null && errMsg.length() > 100) errMsg = errMsg.substring(0, 100) + "...";
            updateTaskStatus(taskId, "FAILED", 0, "运行异常: " + errMsg, false);
        }
    }

    private void prepareMicrobiomeEnvironment(Long taskId, String taskDir) throws Exception {
        AnalysisTask task = this.getById(taskId);
        String paramsJson = task.getParameters();
        double percIdentity = 0.97;
        int threads = 4;

        if (paramsJson != null && !paramsJson.isEmpty()) {
            JSONObject params = JSON.parseObject(paramsJson);
            if (params.getDouble("percIdentity") != null) percIdentity = params.getDouble("percIdentity");
            if (params.getInteger("threads") != null) threads = params.getInteger("threads");
        }

        List<AnalysisTaskFile> tFiles = taskFileMapper.selectList(
                new LambdaQueryWrapper<AnalysisTaskFile>().eq(AnalysisTaskFile::getTaskId, taskId).eq(AnalysisTaskFile::getFileRole, "input")
        );

        String refSeqsName = "";
        String refTaxName = "";
        String metadataName = "";
        Map<String, String[]> sampleMap = new TreeMap<>();

        for (AnalysisTaskFile tf : tFiles) {
            File f = fileMapper.selectById(tf.getFileId());
            if (f == null) continue;

            String name = f.getOriginalName();
            String lowerName = name.toLowerCase();
            Path sourcePath = Paths.get(STORAGE_ROOT + f.getStoragePath());
            Path targetPath = Paths.get(taskDir, name);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 靠文件名特征自动识别这两个库文件
            if (lowerName.endsWith(".qza")) {
                if (lowerName.contains("seq") || lowerName.contains("rep_set")) {
                    refSeqsName = name; // 匹配到参考序列库
                } else if (lowerName.contains("tax")) {
                    refTaxName = name;  // 匹配到物种分类库
                }
            } else if (lowerName.endsWith(".tsv") || lowerName.endsWith(".csv") || lowerName.endsWith(".txt")) {
                metadataName = name;
            } else if (lowerName.endsWith(".fastq.gz") || lowerName.endsWith(".fq.gz")) {
                String sampleId = name.replaceAll("(_1|_2|_R1|_R2)\\.(fastq|fq)\\.gz$", "");
                String[] paths = sampleMap.computeIfAbsent(sampleId, k -> new String[2]);
                String containerPath = "/workspace/" + name;
                if (lowerName.contains("_1") || lowerName.contains("_r1")) paths[0] = containerPath;
                else if (lowerName.contains("_2") || lowerName.contains("_r2")) paths[1] = containerPath;
            }
        }

        // 校验文件
        if (refSeqsName.isEmpty() || refTaxName.isEmpty() || metadataName.isEmpty() || sampleMap.isEmpty()) {
            throw new RuntimeException("文件校验失败：必须挂载参考序列库(含seq)、参考物种库(含tax)、元数据(metadata)与FastQ测序文件");
        }

        // 在生信数据流转中，QIIME2 引擎要求严格的 TSV 格式数据挂载清单
        try (PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(new java.io.FileOutputStream(new java.io.File(taskDir, "manifest.tsv")), "UTF-8"))) {
            pw.print("sample-id\tforward-absolute-filepath\treverse-absolute-filepath\n");
            for (Map.Entry<String, String[]> entry : sampleMap.entrySet()) {
                String[] p = entry.getValue();
                if (p[0] != null && p[1] != null) {
                    pw.print(entry.getKey() + "\t" + p[0] + "\t" + p[1] + "\n");
                }
            }
        } catch (Exception e) {
            log.error("生成 manifest.tsv 失败", e);
        }


        String runSh = "#!/bin/bash\n" +
                "set -e\n" +          // 是一个保护机制 遇到报错立即停止运行
                "cd /workspace\n" +   // 进入docker容器里挂载了数据的工作目录

                "echo '====== [1/6] 导入双端数据 ======'\n" +
                "qiime tools import --type 'SampleData[PairedEndSequencesWithQuality]' --input-path manifest.tsv --output-path demux.qza --input-format PairedEndFastqManifestPhred33V2\n" +

                "echo '====== [2/6] VSEARCH 双端合并 ======'\n" +
                "qiime vsearch merge-pairs --i-demultiplexed-seqs demux.qza --o-merged-sequences joined.qza\n" +

                "echo '====== [3/6] 质量过滤与去冗余 ======'\n" +
                "qiime quality-filter q-score --i-demux joined.qza --o-filtered-sequences filtered.qza --o-filter-stats filter-stats.qza\n" +
                "qiime vsearch dereplicate-sequences --i-sequences filtered.qza --o-dereplicated-table table-derep.qza --o-dereplicated-sequences seqs-derep.qza\n" +

                "echo '====== [4/6] OTU 聚类 (" + (percIdentity * 100) + "%) & 去嵌合体 ======'\n" +
                "qiime vsearch cluster-features-de-novo --i-table table-derep.qza --i-sequences seqs-derep.qza --p-perc-identity " + percIdentity + " --p-threads " + threads + " --o-clustered-table table-otu.qza --o-clustered-sequences seqs-otu.qza\n" +
                "qiime vsearch uchime-denovo --i-table table-otu.qza --i-sequences seqs-otu.qza --o-chimeras chimeras.qza --o-nonchimeras seqs-no-chimera.qza --o-stats uchime-stats.qza\n" +

                "echo '====== [5/6] 物种注释 (VSEARCH 算法极速版) ======'\n" +
                "qiime feature-classifier classify-consensus-vsearch --i-query seqs-no-chimera.qza --i-reference-reads " + refSeqsName + " --i-reference-taxonomy " + refTaxName + " --p-threads " + threads + " --p-maxaccepts 1 --p-maxrejects 100 --o-classification taxonomy.qza --o-search-results search-results.qza\n" +

                "echo '====== [5.5/6] 数据一致性检查 (过滤无注释序列) ======'\n" +
                "qiime feature-table filter-features --i-table table-otu.qza --m-metadata-file taxonomy.qza --o-filtered-table table-otu-filtered.qza\n" +

                "echo '====== [6/6] 生成物种组成柱状图 ======'\n" +
                "qiime taxa barplot --i-table table-otu-filtered.qza --i-taxonomy taxonomy.qza --m-metadata-file " + metadataName + " --o-visualization taxa-bar-plots.qzv\n" +

                "echo '====== [附加步] 导出 CSV 数据矩阵以支持 Echarts 渲染 ======'\n" +
                "qiime tools export --input-path taxa-bar-plots.qzv --output-path taxa_csv_export\n" +

                "echo '[SUCCESS] 16S 分析流程全部完成！'\n";

        java.io.File sFile = new java.io.File(taskDir, "run.sh");
        Files.write(sFile.toPath(), runSh.getBytes());
        sFile.setExecutable(true, false);
    }

    //  把生成的核心结果文件提取出来，加入数据库中，以便前端页面可以展示和下载。
    private void registerMicrobiomeOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles;

        // 获取核心结果文件
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith("taxa-bar-plots.qzv") || name.endsWith("table-otu.qza") || name.endsWith("taxonomy.qza");
                    })  // 给前端可视化图表压缩包         // 特征丰度矩阵    // 物种字典
                    .collect(Collectors.toList());
        }

        for (Path p : resultFiles) {
            java.io.File f = p.toFile();
            String relativeToRoot = Paths.get(hostDataDir).relativize(p).toString().replace("\\", "/");
            String fileName = f.getName();

            File df = new File();
            df.setOriginalName(fileName);
            df.setStoredName("task_" + taskId + "_" + fileName);
            int dotIdx = fileName.lastIndexOf('.');
            df.setFileExt(dotIdx > -1 ? fileName.substring(dotIdx) : ".qza");
            df.setFileType("microbiome_result");
            df.setSizeBytes(f.length());
            df.setStoragePath(relDir + "/" + relativeToRoot);
            df.setUserId(this.getById(taskId).getUserId());
            df.setProjectId(this.getById(taskId).getProjectId());
            df.setStatus("ready");
            df.setFileSource("generate");
            df.setUploadTime(LocalDateTime.now());
            df.setMd5Hash(UUID.randomUUID().toString().replace("-", ""));
            df.setUpdateTime(LocalDateTime.now());
            fileMapper.insert(df);

            AnalysisTaskFile tf = new AnalysisTaskFile();
            tf.setTaskId(taskId);
            tf.setFileId(df.getId());
            tf.setFileRole("output");
            taskFileMapper.insert(tf);
        }
    }
}


