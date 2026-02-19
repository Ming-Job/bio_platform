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

        // 使用 LocalDateTime，与实体类类型匹配
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        // 如果有 status 字段，可以这样设置默认值（根据你的需求）
        // 注意：这个会覆盖实体类中已经设置的值
        Object status = getFieldValByName("status", metaObject);
        if (status == null) {
            this.strictInsertFill(metaObject, "status", Integer.class, 1);
        }

        log.info("插入填充完成");
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("开始更新填充...");

        // 只更新 updateTime
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());

        log.info("更新填充完成");
    }
}