package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/*
*   用户实体类
* */

@Data  // Lombok自动生成get/set/toString等方法
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")  // 关联数据库表名
@ApiModel(value = "User对象", description = "用户表")
public class User implements Serializable {  // 继承该接口，允许被实例化

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "用户ID", example = "1")
    @TableId(type = IdType.AUTO)  // 主键自增
    private Long id;

    @ApiModelProperty(value = "用户名", required = true, example = "zhangsan")
    @TableField("username")
    private String username;

    @ApiModelProperty(value = "密码", required = true, example = "123456")
    @TableField("password")
    private String password;

    @ApiModelProperty(value = "头像地址", example = "/avatar/1.jpg")
    @TableField("avatar")
    private String avatar;

    @ApiModelProperty(value = "角色：user/admin", example = "user")
    @TableField("role")
    private String role;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)  // 插入时自动填充
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.UPDATE)  // 插入时自动更新
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "邮箱", example = "123@qq.com")
    @TableField("email")
    private String email;

    @ApiModelProperty(value = "手机号", example = "12565478545")
    @TableField("phone")
    private String phone;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    @TableField("status")
    private Integer status = 1;

    @ApiModelProperty(value = "最后登录时间")
    @TableField("last_login_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    // 以下为不映射到数据库的字段
    @TableField(exist = false)
    private String token;

    @TableField(exist = false)
    private String confirmPassword;
}