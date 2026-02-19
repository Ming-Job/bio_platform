package com.example.bio_platform.config;// config/WebConfig.java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {


        // 映射生物信息学文件存储目录
        registry.addResourceHandler("/bio_files/**")
                .addResourceLocations("file:" + System.getProperty("user.dir") + "/bio_uploads/files/")
                .setCachePeriod(3600);

        // 映射普通上传文件
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + System.getProperty("user.dir") + "/uploads/")
                .setCachePeriod(3600);

        // 映射 /avatar/** 到项目的 uploads/avatar/ 目录
        registry.addResourceHandler("/avatar/**")
                .addResourceLocations("file:./uploads/avatar/");

        // 将本地文件路径映射为可访问的URL
        // 例如：将 ./uploads 映射为 /uploads/**
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + uploadPath + "/")
                .setCachePeriod(3600); // 设置缓存时间（秒）

        // 如果需要，可以添加更多静态资源路径
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }
}