package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.Course;
import com.example.bio_platform.vo.CourseDetailVO;
import com.example.bio_platform.vo.CourseVO;
import com.example.bio_platform.vo.MyCourseVO;
import com.example.bio_platform.vo.RecentCourseVO;

import java.util.List;


public interface CourseService extends IService<Course> {

    /**
     * 分页查询课程中心列表
     * @param pageNum 当前页
     * @param pageSize 每页大小
     * @param title 课程名称模糊搜索
     * @param difficulty 难度筛选
     * @return 包含 CourseVO 的分页对象
     */
    Page<CourseVO> getCoursePage(Integer pageNum, Integer pageSize, String title, String difficulty);

    /**
     * 根据课程 ID 获取完整的课程详情及大纲
     *
     * @param id 课程 ID
     * @return 课程详情 VO
     */
    CourseDetailVO getCourseDetail(Long id);

    /**
     * 根据课程 ID 获取最近学习记录
     *
     * @param userId 课程 ID
     * @return 最近学习记录 VO
     */
    List<RecentCourseVO> getRecentLearnedCourses(Long userId, int limit);


    // 获取我的全部学习课程
    List<MyCourseVO> getMyCourses(Long userId);

    //  根据关键词检索相关课程
    List<Course> searchCoursesByKeywords(List<String> keywords);
}