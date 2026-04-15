package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.entity.AnalysisPipeline;
import com.example.bio_platform.mapper.AnalysisPipelineMapper;
import com.example.bio_platform.service.AnalysisPipelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisPipelineServiceImpl extends ServiceImpl<AnalysisPipelineMapper, AnalysisPipeline> implements AnalysisPipelineService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createPipeline(AnalysisPipeline pipeline) {
        // 🌟 核心防御：查重。防止 pipelineCode 重复导致调度冲突
        LambdaQueryWrapper<AnalysisPipeline> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisPipeline::getPipelineCode, pipeline.getPipelineCode());

        long count = this.count(wrapper);
        if (count > 0) {
            throw new RuntimeException("该流程编码 (Code) 已存在，请使用唯一的英文标识！");
        }

        // 设置一些默认值兜底
        if (pipeline.getSortOrder() == null) {
            pipeline.setSortOrder(0);
        }
        if (pipeline.getIsActive() == null) {
            pipeline.setIsActive(1);
        }

        return this.save(pipeline);
    }
}
