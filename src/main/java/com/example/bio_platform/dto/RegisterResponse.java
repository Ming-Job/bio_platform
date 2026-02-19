package com.example.bio_platform.dto;

import com.example.bio_platform.entity.User;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "注册响应", description = "用户注册响应数据")
public class RegisterResponse {

    @ApiModelProperty(value = "是否成功")
    private Boolean success;

    @ApiModelProperty(value = "消息")
    private String message;

    @ApiModelProperty(value = "用户信息")
    private User user;

    @ApiModelProperty(value = "访问令牌")
    private String token;

    @ApiModelProperty(value = "令牌类型")
    private String tokenType;

    @ApiModelProperty(value = "过期时间（秒）")
    private Long expiresIn;

    // 成功响应
    public static RegisterResponse success(User user, String token) {
        return RegisterResponse.builder()
                .success(true)
                .message("注册成功")
                .user(user)
                .token(token)
                .tokenType("Bearer")
                .expiresIn(7200L) // 2小时
                .build();
    }

    // 失败响应
    public static RegisterResponse error(String message) {
        return RegisterResponse.builder()
                .success(false)
                .message(message)
                .build();
    }


}