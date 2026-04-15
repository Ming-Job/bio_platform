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
    private TaskGeneExpressionMapper expressionMapper;
    @Autowired
    private TaskDiffExpressionMapper taskDiffExpressionMapper;
    @Autowired
    private TaskGwasResultMapper taskGwasResultMapper;

    private static final String STORAGE_ROOT = "D:/bio_uploads/files/";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitTask(TaskSubmitDTO dto, Long userId) {
        AnalysisPipeline pipeline = pipelineMapper.selectOne(
                new LambdaQueryWrapper<AnalysisPipeline>().eq(AnalysisPipeline::getId, dto.getPipelineId()).eq(AnalysisPipeline::getIsActive, 1)
        );
        if (pipeline == null) throw new RuntimeException("流程不存在");

        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setProjectId(dto.getProjectId());
        task.setPipelineId(pipeline.getId());

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

    private void parseAndSaveCounts(Long taskId, java.io.File countsFile) {
        try (BufferedReader br = new BufferedReader(new java.io.FileReader(countsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("Geneid")) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    TaskGeneExpression exp = new TaskGeneExpression();
                    exp.setTaskId(taskId);
                    exp.setGeneId(parts[0].trim());
                    exp.setReadCount(Integer.parseInt(parts[6].trim()));
                    exp.setCreatedAt(LocalDateTime.now());
                    expressionMapper.insert(exp);
                }
            }
        } catch (Exception e) {
            log.error("任务 [{}] 解析 counts.txt 数据异常", taskId, e);
        }
    }

    private void updateTaskStatus(Long taskId, String status, Integer progress, String msg, boolean isStart) {
        AnalysisTask t = new AnalysisTask();
        t.setId(taskId);
        t.setStatus(status);
        t.setProgress(progress);
        t.setProgressMsg(msg);
        if (isStart) t.setStartedAt(LocalDateTime.now());
        if ("COMPLETED".equals(status)) t.setCompletedAt(LocalDateTime.now());
        this.updateById(t);
    }

    @Override
    public Map<String, Object> getDashboardStats(Long userId, Long projectId) {
        LambdaQueryWrapper<AnalysisTask> qw = new LambdaQueryWrapper<AnalysisTask>().eq(AnalysisTask::getUserId, userId);
        if (projectId != null) {
            qw.eq(AnalysisTask::getProjectId, projectId);
        }
        long totalTasks = this.count(qw);

        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getUserId, userId);
        fileWrapper.ne(File::getStatus, "deleted");
        fileWrapper.ne(File::getFileSource, "generate");
        if (projectId != null) {
            fileWrapper.eq(File::getProjectId, projectId);
        }
        long totalFiles = fileMapper.selectCount(fileWrapper);

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
                    if ("input".equals(tf.getFileRole())) ins.add(f);
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

    @Override
    public Map<Long, Integer> getProjectTaskCountMap(Long userId) {
        List<Map<String, Object>> list = baseMapper.countTasksGroupByProject(userId);
        Map<Long, Integer> m = new HashMap<>();
        for (Map<String, Object> item : list) {
            m.put(((Number) item.get("project_id")).longValue(), ((Number) item.get("task_count")).intValue());
        }
        return m;
    }

    @Override
    public List<TaskGeneExpression> getGeneExpressionData(Long taskId) {
        return expressionMapper.selectList(
                new LambdaQueryWrapper<TaskGeneExpression>()
                        .eq(TaskGeneExpression::getTaskId, taskId)
                        .orderByDesc(TaskGeneExpression::getReadCount)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void performMultiSampleDiffAnalysis(List<Long> controlTaskIds, List<Long> treatTaskIds) {
        try {
            String runId = "diff_" + System.currentTimeMillis();
            Path sandboxDir = Paths.get(STORAGE_ROOT, "diff_workspace", runId);
            Files.createDirectories(sandboxDir);
            log.info("创建多样本差异分析沙盒: {}", sandboxDir.toAbsolutePath());

            Path countsCsvPath = sandboxDir.resolve("counts.csv");
            Path metaCsvPath = sandboxDir.resolve("metadata.csv");
            Path scriptPath = sandboxDir.resolve("run_deseq2.py");
            Path resultCsvPath = sandboxDir.resolve("results.csv");

            writePythonScriptToSandbox(scriptPath);
            buildMatrixAndMetadata(controlTaskIds, treatTaskIds, countsCsvPath, metaCsvPath);

            log.info("正在唤醒 Python DESeq2 沙盒容器进行矩阵运算...");
            boolean success = runDockerSandbox(sandboxDir.toAbsolutePath().toString());

            if (!success || !Files.exists(resultCsvPath)) {
                throw new RuntimeException("Python 容器执行失败或未产出结果文件，请检查控制台日志。");
            }

            parseAndSaveDeseq2Results(resultCsvPath, controlTaskIds, treatTaskIds);
            log.info("🎉 多样本 DESeq2 分析圆满结束，数据已入库！");

        } catch (Exception e) {
            log.error("多样本分析引擎执行崩溃", e);
            throw new RuntimeException("差异计算引擎故障: " + e.getMessage());
        }
    }
    @Override
    public List<TaskDiffExpression> getDiffAnalysisResult(Long controlTaskId, Long treatTaskId) {
        return taskDiffExpressionMapper.selectList(
                new LambdaQueryWrapper<TaskDiffExpression>()
                        .eq(TaskDiffExpression::getControlTaskId, controlTaskId)
                        .eq(TaskDiffExpression::getTreatTaskId, treatTaskId)
        );
    }

    private Map<String, Integer> parseCountsFile(Path countPath) throws IOException {
        Map<String, Integer> countMap = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(countPath);
        for (String line : lines) {
            if (line.startsWith("#") || line.startsWith("Geneid")) continue;
            String[] parts = line.split("\t");
            if (parts.length >= 7) {
                countMap.put(parts[0].trim(), Integer.parseInt(parts[6].trim()));
            }
        }
        return countMap;
    }

    private void buildMatrixAndMetadata(List<Long> controlTaskIds, List<Long> treatTaskIds, Path countsCsv, Path metaCsv) throws IOException {
        Set<String> allGenes = new HashSet<>();
        Map<String, Map<String, Integer>> sampleDataMap = new LinkedHashMap<>();
        List<String> allSampleNames = new ArrayList<>();

        class MatrixBuilder {
            void process(List<Long> taskIds, String condition) throws IOException {
                for (Long taskId : taskIds) {
                    String sampleName = "task_" + taskId;
                    allSampleNames.add(sampleName);
                    Path taskDir = Paths.get(STORAGE_ROOT, "task_results", sampleName);

                    Path countPath = null;
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(taskDir, "*_counts.txt")) {
                        for (Path entry : stream) {
                            countPath = entry;
                            break;
                        }
                    }

                    if (countPath == null || !Files.exists(countPath)) {
                        throw new RuntimeException(sampleName + " 缺乏物理 counts 矩阵文件，请检查上游任务是否完整");
                    }

                    Map<String, Integer> counts = parseCountsFile(countPath);
                    allGenes.addAll(counts.keySet());
                    sampleDataMap.put(sampleName, counts);

                    Files.writeString(metaCsv, sampleName + "," + condition + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
        }

        Files.writeString(metaCsv, "Sample,Condition\n");
        MatrixBuilder builder = new MatrixBuilder();
        builder.process(controlTaskIds, "Control");
        builder.process(treatTaskIds, "Treat");

        StringBuilder countHeader = new StringBuilder("Gene");
        for (String sample : allSampleNames) countHeader.append(",").append(sample);
        Files.writeString(countsCsv, countHeader.append("\n").toString());

        for (String gene : allGenes) {
            StringBuilder row = new StringBuilder(gene);
            for (String sample : allSampleNames) {
                row.append(",").append(sampleDataMap.get(sample).getOrDefault(gene, 0));
            }
            Files.writeString(countsCsv, row.append("\n").toString(), StandardOpenOption.APPEND);
        }
    }

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

    private boolean runDockerSandbox(String workspaceAbsolute) throws Exception {
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            command.add("cmd");
            command.add("/c");
        }
        command.add("docker");
        command.add("run");
        command.add("--rm");
        String safePath = workspaceAbsolute.replace("\\", "/");
        command.add("-v");
        command.add(safePath + ":/workspace");

        command.add("python-sandbox:latest");

        command.add("python");
        command.add("/workspace/run_deseq2.py");
        command.add("/workspace/counts.csv");
        command.add("/workspace/metadata.csv");
        command.add("/workspace/results.csv");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Map<String, String> env = pb.environment();
        env.remove("DOCKER_HOST");
        env.remove("DOCKER_TLS_VERIFY");
        env.remove("DOCKER_CERT_PATH");

        Process process = pb.start();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[DESeq2-Sandbox] {}", line);
            }
        }
        return process.waitFor() == 0;
    }

    private void parseAndSaveDeseq2Results(Path resultCsvPath, List<Long> controlTaskIds, List<Long> treatTaskIds) throws IOException {
        Long representControlId = controlTaskIds.get(0);
        Long representTreatId = treatTaskIds.get(0);

        taskDiffExpressionMapper.delete(new LambdaQueryWrapper<TaskDiffExpression>()
                .eq(TaskDiffExpression::getControlTaskId, representControlId)
                .eq(TaskDiffExpression::getTreatTaskId, representTreatId));

        List<String> lines = Files.readAllLines(resultCsvPath);
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",");
            if (parts.length < 7) continue;

            String geneId = parts[0];
            String log2fcStr = parts[2];
            String padjStr = parts[6];

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

    // =========================================================================
    // 🌟 GWAS 引擎流
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

            List<String> command = new ArrayList<>();
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

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.remove("DOCKER_HOST");
            env.remove("DOCKER_TLS_VERIFY");
            env.remove("DOCKER_CERT_PATH");

            try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath))) {
                Process process = pb.start();
                updateTaskStatus(taskId, "RUNNING", 30, "全流程调度主脚本执行中(包含 Joint Genotyping)...", false);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logWriter.write(line + "\n");
                        logWriter.flush();
                        log.info("[GWAS Sandbox] {}", line);
                    }
                }

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
        Files.createDirectories(Paths.get(taskDir, "raw_data"));
        Files.createDirectories(Paths.get(taskDir, "output"));
        Files.createDirectories(Paths.get(taskDir, "cohort_output"));

        List<AnalysisTaskFile> tFiles = taskFileMapper.selectList(
                new LambdaQueryWrapper<AnalysisTaskFile>().eq(AnalysisTaskFile::getTaskId, taskId).eq(AnalysisTaskFile::getFileRole, "input")
        );

        boolean hasRef = false, hasPheno = false;
        int fqCount = 0;

        for (AnalysisTaskFile tf : tFiles) {
            File f = fileMapper.selectById(tf.getFileId());
            if (f == null) continue;
            String name = f.getOriginalName().toLowerCase();

            if (name.endsWith(".fa") || name.endsWith(".fasta")) {
                Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(taskDir, "reference.fasta"), StandardCopyOption.REPLACE_EXISTING);
                hasRef = true;
            } else if (name.endsWith(".csv")) {
                Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(taskDir, "phenotype.csv"), StandardCopyOption.REPLACE_EXISTING);
                hasPheno = true;
            } else if (name.endsWith(".fastq") || name.endsWith(".fq") || name.endsWith(".gz")) {
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
                "REF_GENOME=\"/workspace/reference.fasta\"\n" +
                "RAW_R1=\"/workspace/raw_data/${SAMPLE_ID}_R1.fastq\"\n" +
                "RAW_R2=\"/workspace/raw_data/${SAMPLE_ID}_R2.fastq\"\n" +
                "OUT_DIR=\"/workspace/output/${SAMPLE_ID}\"\n" +
                "TMP_DIR=\"${OUT_DIR}/tmp\"\n" +
                "mkdir -p ${OUT_DIR} ${TMP_DIR}\n" +    // 这上面的一大段都是在保存上传的文件 和 建文件夹


                "echo \"========== 开始处理样本: ${SAMPLE_ID} ==========\"\n" +
                // 数据清洗
                // 测序仪下机的数据（Raw FastQ）里通常带着机器的接头序列（Adapter），而且有些序列片段测得很模糊（低质量）
                // fastp 就像一个高速洗衣机，把 R1 和 R2 双端数据丢进去，洗掉接头和烂数据，输出干干净净的 clean_R1 和 clean_R2
                "fastp -i ${RAW_R1} -I ${RAW_R2} -o ${OUT_DIR}/${SAMPLE_ID}_clean_R1.fq.gz -O ${OUT_DIR}/${SAMPLE_ID}_clean_R2.fq.gz -w ${THREADS} 2> /dev/null\n" +

                // 序列比对与排序
                // bwa mem：测序出来的数据极短的 DNA 碎片（只有 150bp 左右）。这一步拿着这些短碎片，去参考基因组做比对，找到每一片碎片在染色体上的精确位置。
                // -R (表头注入)：GATK 软件极其严格，它要求数据里必须刻上“防伪钢印”（这个样本叫啥名、用的什么测序仪）。这个 -R 就是强制打钢印的
                // samtools sort： | “管道符”。它让 bwa 比对完的数据直接流进 samtools 进行坐标排序，并打包成高压缩比的 BAM 格式。
                "bwa mem -t ${THREADS} -M -R \"@RG\\tID:${SAMPLE_ID}\\tSM:${SAMPLE_ID}\\tPL:ILLUMINA\\tLB:lib1\" ${REF_GENOME} ${OUT_DIR}/${SAMPLE_ID}_clean_R1.fq.gz ${OUT_DIR}/${SAMPLE_ID}_clean_R2.fq.gz 2> /dev/null | samtools sort -@ ${THREADS} -O bam -o ${OUT_DIR}/${SAMPLE_ID}.sorted.bam -\n" +

                // 建立索引
                // 给刚才生成的 BAM 文件建一个 .bai 目录文件。就像字典的拼音检索表一样，以后软件想查“第 5 号染色体第 100 万个位置”
                "samtools index -@ ${THREADS} ${OUT_DIR}/${SAMPLE_ID}.sorted.bam\n" +

                // 标记 PCR 重复
                // 在测序前，为了让 DNA 浓度达标，机器会做 PCR 扩增（也就是克隆）。这会导致原本只有 1 个的突变，被人工放大了 100 倍
                // 这一步 GATK 会火眼金睛地识别出这些物理位置完全重合的“影分身序列”，给它们打上标签（标记为 Duplicate）。在后面的计算中，这些数据就不会被重复计算了。
                "gatk MarkDuplicates -I ${OUT_DIR}/${SAMPLE_ID}.sorted.bam -O ${OUT_DIR}/${SAMPLE_ID}.dedup.bam -M ${OUT_DIR}/${SAMPLE_ID}.metrics.txt --TMP_DIR ${TMP_DIR} --QUIET true\n" +

                // 建立索引
                "samtools index -@ ${THREADS} ${OUT_DIR}/${SAMPLE_ID}.dedup.bam\n" +

                // 变异检测
                // GATK 拿着去重后的序列（dedup.bam），一个碱基一个碱基地和参考基因组对比。发现不一样的地方（比如标准答案是 A，这哥们身上是 T），就把它记录下来。
                "gatk HaplotypeCaller -R ${REF_GENOME} -I ${OUT_DIR}/${SAMPLE_ID}.dedup.bam -O ${OUT_DIR}/${SAMPLE_ID}.g.vcf.gz -ERC GVCF --native-pair-hmm-threads ${THREADS} --QUIET true\n";

        // 当主调度程序把群体里所有的样本都用这个脚本跑一遍后，就会拿到 100 个 GVCF 文件，接着就可以送进下一个脚本 jointCallingSh，
        Files.write(Paths.get(taskDir, "process_single_sample.sh"), processSingleSh.getBytes());

        // 把前面单人单管算出来的所有结果，融合成一张全群体的超级大表，并进行最终格式清洗。
        String jointCallingSh = "#!/bin/bash\n" +
                "set -e\n" +
                "REF_GENOME=\"/workspace/reference.fasta\"\n" +
                "GVCF_DIR=\"/workspace/output\"\n" +
                "COHORT_OUT=\"/workspace/cohort_output\"\n" +
                "mkdir -p ${COHORT_OUT}\n" +

                // 所有样本的 GVCF 文件路径全找出来，然后写进一个叫 cohort.sample_map.list 的文本文件里
                "find ${GVCF_DIR} -name \"*.g.vcf.gz\" > ${COHORT_OUT}/cohort.sample_map.list\n" +
                // GATK 拿着刚才cohort.sample_map.list文件，把所有样本的单独文件，按染色体位置对齐，硬生生地揉进一个巨大无比的文件 merged.g.vcf.gz 里。
                // 这个时候，数据还比较原始，有些样本在某些位置可能还是“待定”状态。
                "gatk CombineGVCFs -R ${REF_GENOME} -V ${COHORT_OUT}/cohort.sample_map.list -O ${COHORT_OUT}/merged.g.vcf.gz\n" +

                // （Joint Calling 联合变异检测）
                //把所有样本的模糊地带全部强力洗牌，输出一张最终定稿的多样本标准变异文件（VCF） raw_cohort.vcf.gz。
                // 这张表里，每一行是一个突变位点，后面跟着所有样本在这个位点上的基因型。
                "gatk GenotypeGVCFs -R ${REF_GENOME} -V ${COHORT_OUT}/merged.g.vcf.gz -O ${COHORT_OUT}/raw_cohort.vcf.gz\n" +
                // 因为 GATK 吐出来的 VCF 虽然权威，但格式有点“洁癖”。
                // 比如如果是动植物基因组，染色体名字可能叫 Chr1A、Scaffold_2，一般的 GWAS 软件看到这种非人类的命名就会当场崩溃。
                // --allow-extra-chr：极其关键的“宽容补丁”！告诉 PLINK：“别管染色体名字多奇怪，都给我放行，别报错！
                "plink --vcf ${COHORT_OUT}/raw_cohort.vcf.gz --allow-extra-chr --keep-allele-order --recode vcf-iid bgz --out ${COHORT_OUT}/final_gwas_ready\n";
        Files.write(Paths.get(taskDir, "joint_calling_and_filter.sh"), jointCallingSh.getBytes());

        String runSh = "#!/bin/bash\n" +
                "set -e\n" +
                "cd /workspace\n" +

                // 这三行命令，分别是用三种不同的软件，给同一个参考基因组建立了三种不同格式的“索引文件”（就像书的目录）。
                // 做完这一步，后面的软件想查基因组的任何一个位置，都能毫秒级空降。
                "bwa index reference.fasta\n" +
                "samtools faidx reference.fasta\n" +
                "gatk CreateSequenceDictionary -R reference.fasta\n" +

                // 循环处理单样本 跑完一个再跑下一个，直到全群体的测序文件都被处理成 GVCF 格式
                "awk -F ',' 'NR>1 {print $1}' phenotype.csv | while read sample; do\n" +
                "    sample=$(echo $sample | tr -d '\\r')\n" +
                "    echo \"➡️ 正在提交任务: $sample\"\n" +
                "    bash process_single_sample.sh $sample\n" +
                "done\n" +


                // 等样本跑完，即第一个脚本运行完成，就调用第二个脚本进行联合
                // 将产生的所有 GVCF 文件合二为一，并经过 PLINK 格式转换，最终输出一张高质量的全群体变异矩阵表。
                "bash joint_calling_and_filter.sh\n" +

                // 算出谁是导致表型的“罪魁祸首”
                "echo \"========== 开始执行 vcf2gwas LMM 模型 ==========\"\n" +
                // -lmm：核心亮点！ 代表使用 Linear Mixed Model（线性混合模型）。
                // 这是目前 GWAS 领域最顶级的模型，它能自动算亲缘关系矩阵（Kinship）和群体结构（PCA）
                "conda run -n myenv vcf2gwas -v cohort_output/raw_cohort.vcf.gz -pf phenotype.csv -p yield -lmm\n" +
                "echo \"[SUCCESS] 全部流程已打通！\"\n";

        java.io.File sFile = new java.io.File(taskDir, "run.sh");
        Files.write(sFile.toPath(), runSh.getBytes());
        sFile.setExecutable(true, false);
    }

    private void registerGwasOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        if (name.endsWith("phenotype.csv") || name.endsWith("metadata.csv")) {
                            return false;
                        }
                        return name.contains("vcf2gwas") || name.endsWith(".csv") || name.endsWith(".assoc") || name.endsWith(".assoc.txt");
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

            if (fileName.endsWith(".csv") || fileName.endsWith(".assoc.txt")) {
                parseAndSaveGwasResults(taskId, f);
            }
        }
    }

    private void parseAndSaveGwasResults(Long taskId, java.io.File resultFile) {
        log.info("====== 开始解析并入库 GWAS (LMM) 结果文件: {} ======", resultFile.getName());

        List<TaskGwasResult> batchList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new java.io.FileReader(resultFile))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] parts = line.split("\\s+");

                if (parts.length >= 12) {
                    TaskGwasResult res = new TaskGwasResult();
                    res.setTaskId(taskId);
                    res.setChr(parts[0]);
                    res.setSnp(parts[1]);
                    res.setBp(Long.parseLong(parts[2]));

                    res.setAltAllele(parts[4]);
                    res.setRefAllele(parts[5]);
                    res.setMaf(Double.parseDouble(parts[6]));
                    res.setBeta(Double.parseDouble(parts[7]));

                    res.setPValue(Double.parseDouble(parts[11]));
                    res.setCreatedAt(LocalDateTime.now());

                    batchList.add(res);

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

            for (TaskGwasResult r : chrData) {
                double p = (r.getPValue() == null || r.getPValue() <= 0) ? 1e-300 : r.getPValue();
                double negLogP = -Math.log10(p);

                if (negLogP > maxLogP) {
                    maxLogP = negLogP;
                }

                dataPoints.add(new Object[]{
                        r.getBp(),
                        negLogP,
                        r.getSnp()
                });
            }

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
// 🌟 16S 引擎流 (VSEARCH 降维防弹版)
// =========================================================================

    @Override
    @Async
    public void simulateMicrobiomeExecution(Long taskId) {
        log.info("====== 接管 16S 扩增子 (QIIME2 VSEARCH) 任务 [{}] ======", taskId);
        updateTaskStatus(taskId, "RUNNING", 5, "正在初始化微生物组运算沙盒...", true);

        String relativeDir = "task_results/task_" + taskId;
        String hostDataDir = STORAGE_ROOT + relativeDir;
        String containerDataDir = "/workspace";
        String logFilePath = hostDataDir + "/qiime2_process.log";

        try {
            Files.createDirectories(Paths.get(hostDataDir));

            // 调用重写后的环境准备方法
            prepareMicrobiomeEnvironment(taskId, hostDataDir);

            List<String> command = new ArrayList<>();
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
            pb.redirectErrorStream(true);

            Map<String, String> env = pb.environment();
            env.remove("DOCKER_HOST");
            env.remove("DOCKER_TLS_VERIFY");
            env.remove("DOCKER_CERT_PATH");

            try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath))) {
                Process process = pb.start();
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

        // 🌟 核心修改 1：将单一的 classifier 拆分为序列库和层级库
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

            // 🌟 核心修改 2：靠文件名特征自动识别这两个库文件
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

        // 🌟 核心修改 3：严谨的校验逻辑
        if (refSeqsName.isEmpty() || refTaxName.isEmpty() || metadataName.isEmpty() || sampleMap.isEmpty()) {
            throw new RuntimeException("文件校验失败：必须挂载参考序列库(含seq)、参考物种库(含tax)、元数据(metadata)与FastQ测序文件");
        }

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
        // 🌟 最终完美版：修复了导出参数兼容性问题的 16S 工作流
        String runSh = "#!/bin/bash\n" +
                "set -e\n" +          // 是一个保护机制 遇到报错立即停止运行
                "cd /workspace\n" +   // 进入docker容器里挂载了数据的工作目录

                // 打包装箱。
                // QIIME2不直接认散落的.fastq.gz 文件。
                // 这一步把原始测序数据，根据 manifest.tsv（清单文件，告诉它哪个文件是哪个样本），打包成一个 QIIME2 专属的压缩包文件 .qza。
                "echo '====== [1/6] 导入双端数据 ======'\n" +
                "qiime tools import --type 'SampleData[PairedEndSequencesWithQuality]' --input-path manifest.tsv --output-path demux.qza --input-format PairedEndFastqManifestPhred33V2\n" +

                // 拼图游戏。
                // 双端测序（Paired-End）是从 DNA 片段的两头往中间测。
                // 这一步就是把左边测出的序列（Forward）和右边测出的序列（Reverse），根据中间重叠的部分，“咔哒”一下拼接成一条完整的序列。
                "echo '====== [2/6] VSEARCH 双端合并 ======'\n" +
                "qiime vsearch merge-pairs --i-demultiplexed-seqs demux.qza --o-merged-sequences joined.qza\n" +


                //挑拣与合并同类项。
                //质量过滤：把那些测序仪没看清、质量极差的“模糊序列”直接扔掉。
                //去冗余 (Dereplicate)：如果样本里有 1000 条一模一样的序列，计算 1000 次太浪费时间了。这一步会把它们合并成 1 条，并贴个标签写上“数量：1000”，极大减轻后面的计算压力。
                "echo '====== [3/6] 质量过滤与去冗余 ======'\n" +
                "qiime quality-filter q-score --i-demux joined.qza --o-filtered-sequences filtered.qza --o-filter-stats filter-stats.qza\n" +
                "qiime vsearch dereplicate-sequences --i-sequences filtered.qza --o-dereplicated-table table-derep.qza --o-dereplicated-sequences seqs-derep.qza\n" +

                // 划分帮派与抓内鬼。
                //聚类 (Cluster)：把长得像的序列放在一起。用户选了 97%（percIdentity），意思就是只要两条序列的相似度达到 97%，我们就认为它们属于同一个“帮派”（学术上叫 OTU，操作分类单元，可以粗略理解为“种”）。
                //去嵌合体 (Chimera)：在 PCR 扩增的时候，机器偶尔会抽风，把 A 菌的头和 B 菌的尾巴缝合在一起，造出一个自然界根本不存在的“科学怪人”（嵌合体）。这一步就是把这些假数据揪出来扔掉。
                "echo '====== [4/6] OTU 聚类 (" + (percIdentity * 100) + "%) & 去嵌合体 ======'\n" +
                "qiime vsearch cluster-features-de-novo --i-table table-derep.qza --i-sequences seqs-derep.qza --p-perc-identity " + percIdentity + " --p-threads " + threads + " --o-clustered-table table-otu.qza --o-clustered-sequences seqs-otu.qza\n" +
                "qiime vsearch uchime-denovo --i-table table-otu.qza --i-sequences seqs-otu.qza --o-chimeras chimeras.qza --o-nonchimeras seqs-no-chimera.qza --o-stats uchime-stats.qza\n" +

                // 查字典起名字。
                // 现在我们有一堆“帮派”（OTU）了，但只知道编号，不知道它们到底是什么细菌。这一步就是拿着这些 OTU 的序列，去和模板上传的参考数据库（Silva 库）做对比。
                "echo '====== [5/6] 物种注释 (VSEARCH 算法极速版) ======'\n" +
                "qiime feature-classifier classify-consensus-vsearch --i-query seqs-no-chimera.qza --i-reference-reads " + refSeqsName + " --i-reference-taxonomy " + refTaxName + " --p-threads " + threads + " --p-maxaccepts 1 --p-maxrejects 100 --o-classification taxonomy.qza --o-search-results search-results.qza\n" +

                // 将不知名字的分类筛选出来
                "echo '====== [5.5/6] 数据一致性检查 (过滤无注释序列) ======'\n" +
                "qiime feature-table filter-features --i-table table-otu.qza --m-metadata-file taxonomy.qza --o-filtered-table table-otu-filtered.qza\n" +

                // 拿着干净的特征表、物种分类字典和用户的分组信息（Metadata），让 QIIME2 官方生成一个可视化的压缩包 .qzv
                "echo '====== [6/6] 生成物种组成柱状图 ======'\n" +
                "qiime taxa barplot --i-table table-otu-filtered.qza --i-taxonomy taxonomy.qza --m-metadata-file " + metadataName + " --o-visualization taxa-bar-plots.qzv\n" +

                "echo '====== [附加步] 导出 CSV 数据矩阵以支持 Echarts 渲染 ======'\n" +
                // 🌟 核心修复点：将 --output-dir 更改为 --output-path
                "qiime tools export --input-path taxa-bar-plots.qzv --output-path taxa_csv_export\n" +

                "echo '[SUCCESS] 16S 分析流程全部完成！'\n";

        java.io.File sFile = new java.io.File(taskDir, "run.sh");
        Files.write(sFile.toPath(), runSh.getBytes());
        sFile.setExecutable(true, false);
    }

    private void registerMicrobiomeOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles;
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith("taxa-bar-plots.qzv") || name.endsWith("table-otu.qza") || name.endsWith("taxonomy.qza");
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

    // =========================================================================
    // 🌟 终极版 RNA-Seq 多样本端到端引擎
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
        String containerDataDir = "/workspace";
        String logFilePath = hostDataDir + "/process.log";

        try {
            Files.createDirectories(Paths.get(hostDataDir));
            Files.createDirectories(Paths.get(hostDataDir, "raw_data"));

            JSONObject params = JSON.parseObject(paramsJson);
            String threads = params.getString("threads") != null ? params.getString("threads") : "4";
            List<Long> controlIds = params.getJSONArray("controlGroup").toJavaList(Long.class);
            List<Long> treatIds = params.getJSONArray("treatGroup").toJavaList(Long.class);

            AnalysisPipeline pipeline = pipelineMapper.selectById(task.getPipelineId());
            if (pipeline.getRefFaFileId() != null) {
                File faFile = fileMapper.selectById(pipeline.getRefFaFileId());
                Files.copy(Paths.get(STORAGE_ROOT + faFile.getStoragePath()), Paths.get(hostDataDir, "ref.fa"), StandardCopyOption.REPLACE_EXISTING);
            }
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
                    sampleConditionMap.put(baseName, "Control");
                }
            }
            for (Long fid : treatIds) {
                File f = fileMapper.selectById(fid);
                if (f != null) {
                    Files.copy(Paths.get(STORAGE_ROOT + f.getStoragePath()), Paths.get(hostDataDir, "raw_data", f.getOriginalName()), StandardCopyOption.REPLACE_EXISTING);
                    String baseName = f.getOriginalName().replaceAll("\\.(fastq|fq)(\\.gz)?$", "");
                    allSampleNames.add(baseName);
                    sampleConditionMap.put(baseName, "Treat");
                }
            }

            updateTaskStatus(taskId, "RUNNING", 15, "启动第一级引擎：串行比对与定量...", false);

            String sh = "#!/bin/bash\n" +
                    "set -e\n" +
                    "cd /workspace\n" +
                    "echo '[INFO] 开始建立基因组索引...'\n" +
                    // 给参考基因组建立索引
                    // hisat2-build 会对这个大文件进行数学压缩和预处理，生成一套名为 idx 的索引文件。
                    "hisat2-build ref.fa idx > /dev/null\n" +

                    // Linux for 循环，它会自动去找 raw_data 文件夹下所有带有 .fq 的文件（
                    "for fq in raw_data/*.fq*; do\n" +
                    "    base=$(basename $fq | sed -E 's/\\.(fastq|fq)(\\.gz)?//g')\n" +
                    "    echo \"[INFO] =======================================\"\n" +
                    "    echo \"[INFO] 正在全速处理样本: $base\"\n" +
                          // 原始数据里不仅有真正的 RNA 序列，还混杂着测序仪的“接头”（Adapter）以及一些质量极差的“烂尾”序列。
                         //  fastp 就像一个全自动的高速洗衣机，把这些杂质全部剪掉扔掉，输出干干净净的 ${base}_clean.fq。
                    "    fastp -i $fq -o ${base}_clean.fq -w " + threads + " 2>/dev/null\n" +
                         //  RNA-Seq 最耗算力的一步。hisat2 是目前最顶级的转录组比对软件之一。
                        //  它拿着刚才洗好的短序列，对着第一步建好的字典（idx）疯狂查找：“这条序列是从哪条染色体的哪个位置转录出来的？”
                    // 找到位置后，把所有的坐标信息记录下来，打包输出为一个叫 .sam 的对齐文件
                    "    hisat2 -x idx -U ${base}_clean.fq -S ${base}_aligned.sam -p " + threads + " 2>/dev/null\n" +
                    // 刚才的比对（SAM文件）只告诉了我们坐标（比如这条序列在 5 号染色体 100~200 的位置），
                    // 但我们不知道这个位置到底是哪个具体的基因。
                    // ref.gtf（基因注释文件）上面写清楚了哪个坐标段属于哪个基因
                    // featureCounts 的工作原理就是：拿着测序序列的坐标，去和 ref.gtf 做比对碰撞。碰上了一次，就给这个基因的得票数 +1。
                    "    featureCounts -T " + threads + " -a ref.gtf -o ${base}_counts.txt ${base}_aligned.sam 2>/dev/null\n" +
                    "done\n" +
                    "echo '[SUCCESS] 所有样本比对与定量完成！'\n";

            java.io.File sFile = new java.io.File(hostDataDir, "run_bulk.sh");
            Files.write(sFile.toPath(), sh.getBytes());
            sFile.setExecutable(true, false);

            List<String> cmd1 = Arrays.asList("docker", "run", "--rm", "-v", hostDataDir.replace("\\", "/") + ":/workspace", "bio-os/rna-seq-sandbox:v1", "bash", "/workspace/run_bulk.sh");
            runProcessAndLog(cmd1, logFilePath);

            updateTaskStatus(taskId, "RUNNING", 60, "提取定量矩阵，准备 DESeq2 差异对撞...", false);

            Set<String> allGenes = new HashSet<>();
            Map<String, Map<String, Integer>> sampleDataMap = new LinkedHashMap<>();

            for (String sample : allSampleNames) {
                Path countPath = Paths.get(hostDataDir, sample + "_counts.txt");
                if (Files.exists(countPath)) {
                    Map<String, Integer> counts = parseCountsFile(countPath);
                    allGenes.addAll(counts.keySet());
                    sampleDataMap.put(sample, counts);
                    parseAndSaveCounts(taskId, countPath.toFile());
                }
            }

            Path countsCsv = Paths.get(hostDataDir, "counts.csv");
            Path metaCsv = Paths.get(hostDataDir, "metadata.csv");
            Files.writeString(metaCsv, "Sample,Condition\n");
            for (String sample : allSampleNames) {
                Files.writeString(metaCsv, sample + "," + sampleConditionMap.get(sample) + "\n", StandardOpenOption.APPEND);
            }

            StringBuilder countHeader = new StringBuilder("Gene");
            for (String sample : allSampleNames) countHeader.append(",").append(sample);
            Files.writeString(countsCsv, countHeader.append("\n").toString());

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

                // 🌟 新增：将 Python 沙盒生成的产出物注册回文件系统
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

    /**
     * 🌟 新增方法：将 RNA-Seq E2E 生成的关键物理文件（尤其是 results.csv）注册入库，供用户下载
     */
    private void registerRnaSeqE2EOutputs(Long taskId, String hostDataDir, String relDir) throws Exception {
        List<Path> resultFiles;
        try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(hostDataDir))) {
            resultFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        // 过滤出我们想要展示在“产出物”面板里供用户下载的文件
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

    /**
     * 提取公共的进程执行和日志打印方法
     */
    private void runProcessAndLog(List<String> command, String logFilePath) throws Exception {
        List<String> finalCmd = new ArrayList<>();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            finalCmd.add("cmd"); finalCmd.add("/c");
        }
        finalCmd.addAll(command);

        ProcessBuilder pb = new ProcessBuilder(finalCmd);
        pb.redirectErrorStream(true);
        Map<String, String> env = pb.environment();
        env.remove("DOCKER_HOST"); env.remove("DOCKER_TLS_VERIFY"); env.remove("DOCKER_CERT_PATH");

        try (FileWriter logWriter = new FileWriter(new java.io.File(logFilePath), true)) {
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logWriter.write(line + "\n");
                    logWriter.flush();
                    log.info("[E2E Sandbox] {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Docker 容器异常退出，退出码: " + exitCode);
            }
        }
    }
}