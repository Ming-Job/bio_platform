package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
    // 基础 CRUD 由 MyBatis-Plus 提供，无需手写 SQL
}