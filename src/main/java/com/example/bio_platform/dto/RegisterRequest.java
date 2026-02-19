package com.example.bio_platform.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Data
@ApiModel(value = "注册请求", description = "用户注册请求参数")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @ApiModelProperty(value = "用户名", required = true, example = "zhangsan")
    private String username;

    @NotBlank(message = "密码不能为空")
    @ApiModelProperty(value = "密码", required = true, example = "123456")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    @ApiModelProperty(value = "确认密码", required = true, example = "123456")
    private String confirmPassword;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "admin|teacher|student", message = "角色必须是admin、teacher或student")
    @ApiModelProperty(value = "角色", required = true, example = "student")
    private String role;

    @Email(message = "邮箱格式不正确")
    @ApiModelProperty(value = "邮箱", example = "user@example.com")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @ApiModelProperty(value = "手机号", example = "13800138000")
    private String phone;
}