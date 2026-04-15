package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.AnalysisPipeline;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnalysisPipelineMapper extends BaseMapper<AnalysisPipeline> {
    // MyBatis-Plus 已经内置了所有的 CRUD 基础方法，直接继承即可
}