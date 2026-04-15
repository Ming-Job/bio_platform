package com.example.bio_platform.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.info("开始插入填充...");
        LocalDateTime now = LocalDateTime.now();

        // 1. 兼容创建时间：有 createTime 就填 createTime，有 createdAt 就填 createdAt
        if (metaObject.hasSetter("createTime")) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("createdAt")) {
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        }

        // 2. 兼容更新时间
        if (metaObject.hasSetter("updateTime")) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("updatedAt")) {
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }

        // 3. status 的默认值逻辑保持不变，但也加个安全判断
        if (metaObject.hasSetter("status")) {
            Object status = getFieldValByName("status", metaObject);
            if (status == null) {
                this.strictInsertFill(metaObject, "status", Integer.class, 1);
            }
        }

        log.info("插入填充完成");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充...");
        LocalDateTime now = LocalDateTime.now();

        // 兼容两种更新时间命名，哪个存在就填哪个
        if (metaObject.hasSetter("updateTime")) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("updatedAt")) {
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }

        log.info("更新填充完成");
    }
}