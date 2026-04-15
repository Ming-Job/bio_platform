package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.AnalysisPipeline;

public interface AnalysisPipelineService extends IService<AnalysisPipeline> {

    // 自定义一个带防呆校验的新增方法
    boolean createPipeline(AnalysisPipeline pipeline);

}