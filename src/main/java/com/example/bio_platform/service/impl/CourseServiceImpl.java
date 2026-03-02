package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.dto.CourseChapterDTO;
import com.example.bio_platform.entity.Course;
import com.example.bio_platform.entity.User;
import com.example.bio_platform.mapper.CourseMapper;
import com.example.bio_platform.service.CourseLessonService;
import com.example.bio_platform.service.CourseService;
import com.example.bio_platform.service.UserCourseEnrollmentService;
import com.example.bio_platform.service.UserService; // 引入 UserService
import com.example.bio_platform.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.bio_platform.vo.RecentCourseVO;
import com.example.bio_platform.entity.UserCourseEnrollment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    // 注入 UserService
    @Autowired
    private UserService userService;

    // 注入 课时
    @Autowired
    private CourseLessonService courseLessonService;

    @Autowired
    private UserCourseEnrollmentService enrollmentService;

    @Override
    public Page<CourseVO> getCoursePage(Integer pageNum, Integer pageSize, String title, String difficulty) {
        Page<Course> pageParam = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getStatus, "PUBLISHED");

        if (StringUtils.hasText(title)) {
            wrapper.like(Course::getTitle, title);
        }
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(Course::getDifficulty, difficulty);
        }
        wrapper.orderByAsc(Course::getSortOrder).orderByDesc(Course::getCreatedAt);

        // 从数据库查出所有的课程
        Page<Course> coursePage = this.page(pageParam, wrapper);

        // 2. 实体转 VO 的过程中，调用 UserService 查名字
        List<CourseVO> voList = coursePage.getRecords().stream().map(course -> {
            CourseVO vo = new CourseVO();
            BeanUtils.copyProperties(course, vo);

            // 获取当前课程的讲师 ID
            Long instructorId = course.getInstructorId();

            // 默认兜底数据
            String realUsername = "平台讲师";
            String realAvatar = "";

            // 一次性拿取用户所有信息
            User instructor = userService.getById(instructorId);

            if (instructor != null){

                // 查到了讲师的名字
                if (StringUtils.hasText(instructor.getUsername())){
                    realUsername = instructor.getUsername();
                }

                // 提取头像
                if (StringUtils.hasText(instructor.getAvatar())){
                    realAvatar = instructor.getAvatar();
                }
            }

            // 组装 VO 数据
            vo.setInstructorName(realUsername)
                    .setInstructorAvatar(realAvatar) // 如果你有 getUserAvatar 方法也可以在这里调
                    .setLessonCount(15)      // 这些等后面建了课时表再查
                    .setEnrolledCount(886)
                    .setRating(4.9)
                    .setEstimatedHours(8)
                    .setBgColor("linear-gradient(135deg, #3498db, #2ecc71)");

            return vo;
        }).collect(Collectors.toList());

        Page<CourseVO> voPage = new Page<>(coursePage.getCurrent(), coursePage.getSize(), coursePage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public CourseDetailVO getCourseDetail(Long id) {
        // 1. 查询课程主表信息
        Course course = this.getById(id);
        if (course == null || "OFFLINE".equals(course.getStatus())){
            return null;  // 如果课程不存在或已下架，返回null
        }

        // 2. 初始化详情
        CourseDetailVO detailVO = new CourseDetailVO();
        BeanUtils.copyProperties(course, detailVO);

        // 3. 补充讲师信息 （复用 getById 优化查询）
        User instructor = userService.getById(course.getInstructorId());
        if (instructor != null){
            detailVO.setInstructorName(StringUtils.hasText(instructor.getUsername())? instructor.getUsername() : "平台讲师")
                    .setInstructorAvatar(StringUtils.hasText(instructor.getAvatar())? instructor.getAvatar() : "");
        }else {
            detailVO.setInstructorName("平台讲师").setInstructorAvatar("");
        }

        // 4. 获取课程大纲 （直接调用 CourseLessonService）
        List<CourseChapterDTO> chapters = courseLessonService.getCourseOutline(id);
        detailVO.setChapters(chapters);

        // 5. 补充聚合统计数据
        // 真实计算：遍历大纲，算出到底有多少个课时
        int realLessonCount = chapters.stream().mapToInt(chapter -> chapter.getLessons().size()).sum();

        detailVO.setLessonCount(realLessonCount)
                .setEnrolledCount(1250) // 报名人数 假数据
                .setRating(4.9) // 评分
                .setEstimatedHours(12) // 预计时长
                .setBgColor("linear-gradient(135deg, #3498db, #2ecc71)"); // 默认占位渐变色
        return detailVO;
    }

    @Override
    public List<RecentCourseVO> getRecentLearnedCourses(Long userId, int limit) {
        if (userId == null) return new ArrayList<>();

        // 1. 查询该用户最近报名的几门课（按最后学习时间倒序）
        List<UserCourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<UserCourseEnrollment>()
                        .eq(UserCourseEnrollment::getUserId, userId)
                        .orderByDesc(UserCourseEnrollment::getLastLearnedAt)
                        .last("LIMIT " + limit)
        );

        if (enrollments.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取课程ID，去 course 表查出封面和标题
        List<Long> courseIds = enrollments.stream().map(UserCourseEnrollment::getCourseId).collect(Collectors.toList());
        List<Course> courses = this.listByIds(courseIds);

        // 3. 转为 Map 方便匹配
        Map<Long, Course> courseMap = courses.stream().collect(Collectors.toMap(Course::getId, c -> c));

        // 4. 组装 VO 返回
        return enrollments.stream().map(e -> {
            RecentCourseVO vo = new RecentCourseVO();
            vo.setCourseId(e.getCourseId());
            vo.setProgress(e.getProgress());

            Course c = courseMap.get(e.getCourseId());
            if (c != null) {
                vo.setTitle(c.getTitle());
                vo.setCoverImage(c.getCoverImage());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<MyCourseVO> getMyCourses(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        // 1. 查询该用户所有的报名记录（按照最后学习时间倒序排列，最近学的排在前面）
        List<UserCourseEnrollment> enrollments = enrollmentService.list(
                new LambdaQueryWrapper<UserCourseEnrollment>()
                        .eq(UserCourseEnrollment::getUserId, userId)
                        .orderByDesc(UserCourseEnrollment::getLastLearnedAt)
        );

        if (enrollments.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 提取所有的课程 ID，去 course 表里批量查出课程详情（封面、标题）
        List<Long> courseIds = enrollments.stream()
                .map(UserCourseEnrollment::getCourseId)
                .collect(Collectors.toList());
        List<Course> courses = this.listByIds(courseIds);

        // 转为 Map 提高匹配效率 (ID -> Course对象)
        Map<Long, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        // 3. 将记录组装成 VO 返回给前端
        return enrollments.stream().map(e -> {
            MyCourseVO vo = new MyCourseVO()
                    .setCourseId(e.getCourseId())
                    .setProgress(e.getProgress())
                    .setLastLearnedAt(e.getLastLearnedAt()); // 注入最后学习时间

            Course c = courseMap.get(e.getCourseId());
            if (c != null) {
                vo.setTitle(c.getTitle());
                vo.setCoverImage(c.getCoverImage());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // 🌟 新增：根据关键词列表，搜索相关的课程
    public List<Course> searchCoursesByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>(); // 没关键词就返回空
        }

        QueryWrapper<Course> queryWrapper = new QueryWrapper<>();

        // 拼装 SQL: WHERE (title LIKE '%k1%' OR description LIKE '%k1%') OR (title LIKE '%k2%' OR ...)
        queryWrapper.and(wrapper -> {
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (i > 0) {
                    wrapper.or();
                }
                wrapper.like("title", kw)
                        .or()
                        .like("description", kw); // 假设你的表里有 title 和 description 字段
            }
        });

        // 还可以加上排序，比如按浏览量或上架时间排个序
        queryWrapper.orderByDesc("created_at");

        // 限制最多推荐 6 门课
        queryWrapper.last("LIMIT 6");

        return this.list(queryWrapper);
    }
}
