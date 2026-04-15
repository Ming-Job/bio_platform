package com.example.bio_platform;

import com.example.bio_platform.config.FileUploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(FileUploadProperties.class)  // 添加这行
@EnableScheduling
@EnableAsync
public class BioPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioPlatformApplication.class, args);
    }

}
