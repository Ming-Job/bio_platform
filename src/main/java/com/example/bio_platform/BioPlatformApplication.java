package com.example.bio_platform;

import com.example.bio_platform.config.FileUploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileUploadProperties.class)  // 添加这行
public class BioPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioPlatformApplication.class, args);
    }

}
