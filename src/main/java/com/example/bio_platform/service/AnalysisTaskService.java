package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.dto.TaskSubmitDTO;
import com.example.bio_platform.entity.AnalysisTask;
import com.example.bio_platform.entity.TaskDiffExpression;
import com.example.bio_platform.entity.TaskGeneExpression;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 云端生信分析任务引擎 - 核心调度接口
 * </p>
 */
public interface AnalysisTaskService extends IService<AnalysisTask> {

    // =========================================================================
    // 🚀 核心任务投递与路由
    // =========================================================================

    /**
     * 提交新的生信分析任务并分配初始资源
     */
    Long submitTask(TaskSubmitDTO dto, Long userId);


    // =========================================================================
    // 🧬 三大生信分析底层引擎 (Docker 容器化隔离沙盒)
    // =========================================================================

    /**
     * 🌟 引擎一：RNA-Seq 端到端多样本复合分析流程 (FastQ排队比对 -> Python DESeq2 对撞)
     */
    void simulateRnaSeqExecution(Long taskId);

    /**
     * 🌟 引擎二：16S 微生物组扩增子分析流程 (基于 QIIME2 与 VSEARCH 聚类)
     */
    void simulateMicrobiomeExecution(Long taskId);

    /**
     * 🌟 引擎三：GWAS 全基因组关联分析流程 (基于 GATK4 与 LMM 线性混合模型)
     */
    void simulateGwasExecution(Long taskId);


    // =========================================================================
    // 📊 控制台与任务明细查询
    // =========================================================================

    /**
     * 获取分析控制台的大盘统计数据 (任务数、文件数等)
     */
    Map<String, Object> getDashboardStats(Long userId, Long projectId);

    /**
     * 获取分析任务的完整明细 (含日志、输入、输出文件挂载列表)
     */
    Map<String, Object> getTaskDetails(Long taskId, Long userId);

    /**
     * 获取用户各个项目下的任务统计分布
     */
    Map<Long, Integer> getProjectTaskCountMap(Long userId);


    // =========================================================================
    // 📈 核心结果解析与图表数据下发 (对接 Echarts)
    // =========================================================================

    /**
     * [兼容性保留] 单独拉取 Python 沙盒执行多样本差异分析
     */
    void performMultiSampleDiffAnalysis(List<Long> controlTaskIds, List<Long> treatTaskIds);

    /**
     * 获取单样本的基因表达量矩阵数据
     */
    List<TaskGeneExpression> getGeneExpressionData(Long taskId);

    /**
     * 提取 RNA-Seq 差异分析结果 (用于前端真实火山图绘制)
     */
    List<TaskDiffExpression> getDiffAnalysisResult(Long controlTaskId, Long treatTaskId);

    /**
     * 提取 GWAS 变异关联结果 (用于前端真实曼哈顿图与 QQ 图绘制)
     */
    Map<String, Object> getGwasManhattanData(Long taskId);

}