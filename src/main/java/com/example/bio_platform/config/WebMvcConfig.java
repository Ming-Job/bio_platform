package com.example.bio_platform.config;

import com.example.bio_platform.utils.AvatarUploadUtil;
import com.example.bio_platform.utils.CourseCoverUploadUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.io.File;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AvatarUploadUtil avatarUploadUtil;

    @Resource
    private CourseCoverUploadUtil courseCoverUploadUtil;

    // 🌟 1. 读取我们在 application.properties 里配置的视频本地存放路径
    @Value("${course.video.upload-relative-path:uploads/video/}")
    private String videoUploadRelativePath;

    // 🌟 读取文档存放路径
    @Value("${course.document.upload-relative-path:uploads/document/}")
    private String documentUploadRelativePath;

    /**
     * 静态资源映射：/avatar/** → 项目根目录/uploads/avatar/  用户头像
     * 静态资源映射：/course/** → 项目根目录/uploads/course/  课程图片
     * 静态资源映射：/video/** → 项目根目录/uploads/video/   课程视频
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1. 用户头像映射
        String avatarUploadPath = avatarUploadUtil.getRealUploadPath();
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:" + avatarUploadPath);

        // 2. 课程图片映射
        String courseUploadPath = courseCoverUploadUtil.getRealUploadPath();
        registry.addResourceHandler("/course/**")
                .addResourceLocations("file:" + courseUploadPath);

        // 3. 🌟 新增：课程视频映射
        // 获取项目根目录，并拼上视频文件夹的路径
        String videoAbsolutePath = System.getProperty("user.dir") + File.separator + videoUploadRelativePath;
        // 确切保证路径末尾有斜杠
        if (!videoAbsolutePath.endsWith(File.separator)) {
            videoAbsolutePath += File.separator;
        }

        // 只要前端请求 /video/ 开头的链接，就去本地的 uploads/video/ 文件夹下找
        registry.addResourceHandler("/video/**")
                .addResourceLocations("file:" + videoAbsolutePath);

        // 🌟 新增：课程文档(PPT/PDF)映射
        String docAbsolutePath = System.getProperty("user.dir") + File.separator + documentUploadRelativePath;
        if (!docAbsolutePath.endsWith(File.separator)) {
            docAbsolutePath += File.separator;
        }

        // 只要前端请求 /document/ 开头的链接，就去本地的 uploads/document/ 找
        registry.addResourceHandler("/document/**")
                .addResourceLocations("file:" + docAbsolutePath);
    }
}