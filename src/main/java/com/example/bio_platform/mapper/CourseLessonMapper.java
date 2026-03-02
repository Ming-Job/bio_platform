package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.CourseLesson;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseLessonMapper extends BaseMapper<CourseLesson> {
    // 基础的 CRUD 已经由 MyBatis-Plus 提供
    // 如果有非常复杂的多表联查，可以在这里定义并在 xml 中实现
}
