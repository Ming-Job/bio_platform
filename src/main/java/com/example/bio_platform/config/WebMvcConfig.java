package com.example.bio_platform.config;

import com.example.bio_platform.utils.AvatarUploadUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AvatarUploadUtil avatarUploadUtil;

    /**
     * 静态资源映射：/avatar/** → 项目根目录/uploads/avatar/
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String realUploadPath = avatarUploadUtil.getRealUploadPath();
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:" + realUploadPath);
    }
}