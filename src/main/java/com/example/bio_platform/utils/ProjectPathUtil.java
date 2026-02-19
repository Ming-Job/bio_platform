package com.example.bio_platform.utils;

import org.springframework.stereotype.Component;
import java.io.File;

/**
 * 动态获取项目根目录工具类
 */
@Component
public class ProjectPathUtil {

    /**
     * 获取项目根目录（bio_platform 目录）
     */
    public String getProjectRootPath() {
        // 通过当前类路径推导项目根目录
        String classPath = this.getClass().getResource("/").getPath();
        // 去掉 target/classes/ 层级，得到项目根目录
        File classesDir = new File(classPath);
        File targetDir = classesDir.getParentFile();
        File projectRootDir = targetDir.getParentFile();
        // 统一路径分隔符为 /
        return projectRootDir.getAbsolutePath().replace("\\", "/") + "/";
    }
}
