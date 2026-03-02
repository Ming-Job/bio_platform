package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.dto.CourseChapterDTO;
import com.example.bio_platform.entity.CourseLesson;
import com.example.bio_platform.mapper.CourseLessonMapper;
import com.example.bio_platform.service.CourseLessonService; // 确保导入了父接口
import com.example.bio_platform.vo.CourseLessonVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap; // 必须导入
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseLessonServiceImpl extends ServiceImpl<CourseLessonMapper, CourseLesson> implements CourseLessonService {

    @Override
    public List<CourseChapterDTO> getCourseOutline(Long courseId) {
        // 1. 参数校验
        if (courseId == null) {
            return new ArrayList<>();
        }

        // 2. 查询该课程下所有课时
        // 建议：如果数据库有章节序号字段，应先按章节序号排序，再按 sort_order 排序
        // 目前按 sort_order 排序，前提是你的 sort_order 在全课程范围内是连续递增的
        List<CourseLesson> lessons = this.list(new LambdaQueryWrapper<CourseLesson>()
                .eq(CourseLesson::getCourseId, courseId)
                .orderByAsc(CourseLesson::getSortOrder));

        if (lessons == null || lessons.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 按照章节名称分组，并保持原始查询的先后顺序
        Map<String, List<CourseLesson>> groupedMap = lessons.stream()
                .collect(Collectors.groupingBy(
                        lesson -> (lesson.getChapterName() == null || lesson.getChapterName().isEmpty())
                                ? "未分类章节" : lesson.getChapterName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 4. 组装 DTO 结构
        return groupedMap.entrySet().stream().map(entry -> {
            CourseChapterDTO chapter = new CourseChapterDTO();
            chapter.setChapterName(entry.getKey());

            // 实体转 VO (手动设置或使用 BeanUtils)
            List<CourseLessonVO> lessonVOs = entry.getValue().stream().map(l -> {
                CourseLessonVO vo = new CourseLessonVO();
                BeanUtils.copyProperties(l, vo);
                return vo;
            }).collect(Collectors.toList());

            chapter.setLessons(lessonVOs);
            return chapter;
        }).collect(Collectors.toList());
    }
}