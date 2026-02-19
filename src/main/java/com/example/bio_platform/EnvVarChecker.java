package com.example.bio_platform;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EnvVarChecker implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("=== 环境变量检查 ===");
        log.info("DOCKER_HOST = {}", System.getenv("DOCKER_HOST"));
        log.info("DOCKER_TLS_VERIFY = {}", System.getenv("DOCKER_TLS_VERIFY"));
        log.info("=== 环境变量检查结束 ===");
    }
}