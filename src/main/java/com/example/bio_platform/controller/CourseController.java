package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.common.Result; // 请替换为你实际的 Result 路径
import com.example.bio_platform.entity.UserCourseEnrollment;
import com.example.bio_platform.service.CourseService;
import com.example.bio_platform.service.UserCourseEnrollmentService;
import com.example.bio_platform.utils.CourseCoverUploadUtil;
import com.example.bio_platform.vo.CourseDetailVO;
import com.example.bio_platform.vo.CourseVO;
import com.example.bio_platform.vo.MyCourseVO;
import com.example.bio_platform.vo.RecentCourseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

@Api(tags = "课程中心接口")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Resource
    private CourseService courseService;

    @Resource
    private UserCourseEnrollmentService enrollmentService;

    @Resource
    private CourseCoverUploadUtil courseCoverUploadUtil;

    // 🌟 1. 注入视频存放配置
    @org.springframework.beans.factory.annotation.Value("${course.video.upload-relative-path:uploads/video/}")
    private String videoUploadPath;

    @org.springframework.beans.factory.annotation.Value("${course.video.access-prefix:/video/}")
    private String videoAccessPrefix;

    // 🌟 @PostMapping，路径为 /video/upload
    @ApiOperation(value = "上传课程视频")
    @PostMapping("/video/upload")
    public Result<String> uploadCourseVideo(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 校验文件后缀
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || (!originalFilename.toLowerCase().endsWith(".mp4") && !originalFilename.toLowerCase().endsWith(".avi"))) {
                return Result.error("仅支持 MP4 / AVI 格式的视频");
            }

            // 🌟 核心修改：利用 user.dir 获取项目绝对路径，彻底杜绝 Tomcat 瞎拼接
            String absolutePath = System.getProperty("user.dir") + java.io.File.separator + videoUploadPath;
            java.io.File dir = new java.io.File(absolutePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 3. 生成唯一文件名
            String newFileName = java.util.UUID.randomUUID().toString().replace("-", "") + ".mp4";
            java.io.File targetFile = new java.io.File(dir, newFileName);

            // 4. 保存文件到本地 (此时传入的是绝对路径，安全通过！)
            file.transferTo(targetFile);

            // 5. 返回相对路径给前端
            return Result.success("视频上传成功", videoAccessPrefix + newFileName);

        } catch (java.io.IOException e) {
            e.printStackTrace();
            return Result.error("视频存储失败，服务器异常");
        }
    }

    // 🌟 1. 注入文档存放配置
    @org.springframework.beans.factory.annotation.Value("${course.document.upload-relative-path:uploads/document/}")
    private String documentUploadPath;

    @org.springframework.beans.factory.annotation.Value("${course.document.access-prefix:/document/}")
    private String documentAccessPrefix;

    // 🌟 2. 文档上传接口
    @ApiOperation(value = "上传课程文档(PPT/PDF)")
    @PostMapping("/document/upload")
    public Result<String> uploadCourseDocument(@RequestParam("file") MultipartFile file) {
        try {
            // 校验文件后缀
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return Result.error("文件名不能为空");
            }
            String lowerName = originalFilename.toLowerCase();
            if (!lowerName.endsWith(".ppt") && !lowerName.endsWith(".pptx") && !lowerName.endsWith(".pdf")) {
                return Result.error("仅支持 PPT, PPTX 或 PDF 格式的文档");
            }

            // 利用 user.dir 获取项目绝对路径，防止路径拼接报错
            String absolutePath = System.getProperty("user.dir") + java.io.File.separator + documentUploadPath;
            java.io.File dir = new java.io.File(absolutePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 生成唯一文件名，并保留原始后缀
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFileName = java.util.UUID.randomUUID().toString().replace("-", "") + suffix;
            java.io.File targetFile = new java.io.File(dir, newFileName);

            // 保存文件到本地
            file.transferTo(targetFile);

            // 返回相对路径给前端
            return Result.success("文档上传成功", documentAccessPrefix + newFileName);

        } catch (java.io.IOException e) {
            e.printStackTrace();
            return Result.error("文档存储失败，服务器异常");
        }
    }
    /**
     * 课程封面上传接口
     */
    @PostMapping("/cover/upload")
    public Result<String> uploadCourseCover(@RequestParam("cover") MultipartFile file) {
        try {
            String coverPath = courseCoverUploadUtil.upload(file);
            return Result.success("上传成功", coverPath);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("服务器文件读写异常");
        }
    }

    @ApiOperation(value = "分页查询前端课程列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "当前页码", defaultValue = "1", paramType = "query", dataType = "Integer"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", defaultValue = "6", paramType = "query", dataType = "Integer"),
            @ApiImplicitParam(name = "title", value = "课程名称(模糊)", paramType = "query", dataType = "String"),
            @ApiImplicitParam(name = "difficulty", value = "难度(BEGINNER/INTERMEDIATE/ADVANCED)", paramType = "query", dataType = "String")
    })
    @GetMapping("/page")
    public Result<Page<CourseVO>> getCoursePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "6") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String difficulty) {

        Page<CourseVO> pageData = courseService.getCoursePage(pageNum, pageSize, title, difficulty);
        return Result.success(pageData); // 根据你项目的统一返回类进行修改
    }

    @ApiOperation(value = "获取单门课程详情及大纲")
    @GetMapping("/{id}")
    public Result<CourseDetailVO> getCourseDetail(@PathVariable Long id) {
        CourseDetailVO detail = courseService.getCourseDetail(id);
        if (detail == null) {
            return Result.error("该课程不存在或已下架");
        }
        return Result.success(detail);
    }

    @ApiOperation(value = "获取用户对某门课的报名状态")
    @GetMapping("/{courseId}/enrollment-status")
    public Result<UserCourseEnrollment> getEnrollmentStatus(@PathVariable Long courseId,
                                                            @RequestParam Long userId) {
        // 注意：实际企业开发中，userId 通常从 Token 中解析（如 request.getHeader("Authorization")）
        // 毕设阶段为了方便联调，可以先作为参数传过来
        UserCourseEnrollment record = enrollmentService.getEnrollmentRecord(userId, courseId);
        return Result.success(record); // 如果未报名会返回 null，前端根据 null 判断
    }

    @ApiOperation(value = "用户加入课程学习")
    @PostMapping("/{courseId}/enroll")
    public Result<UserCourseEnrollment> enrollCourse(@PathVariable Long courseId,
                                                     @RequestParam Long userId) {
        UserCourseEnrollment record = enrollmentService.enrollCourse(userId, courseId);
        return Result.success(record);
    }

    @ApiOperation(value = "获取用户最近学习的课程")
    @GetMapping("/recent")
    public Result<List<RecentCourseVO>> getRecentCourses(@RequestParam Long userId) {
        // 默认取最近学的 3 门课展示在顶部
        List<RecentCourseVO> list = courseService.getRecentLearnedCourses(userId, 3);
        return Result.success(list);
    }

    @ApiOperation(value = "获取当前用户的所有学习课程")
    @GetMapping("/user/my")
    public Result<List<MyCourseVO>> getMyCourses(@RequestParam Long userId) {
        List<MyCourseVO> list = courseService.getMyCourses(userId);
        return Result.success(list);
    }
}