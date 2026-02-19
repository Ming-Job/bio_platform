package com.example.bio_platform.common;

import lombok.Data;

/**
 * 统一响应实体
 */
@Data
public class Result<T> {
    // 响应码：200成功，500失败，400参数错误，401未授权
    private Integer code;
    // 响应消息
    private String msg;
    // 响应数据
    private T data;

    // 修复后的构造方法：public修饰 + 正确参数名 + 泛型T类型 + 赋值逻辑
    public Result(Integer code, String msg, T data) {
        this.code = code;   // 给响应码赋值
        this.msg = msg;     // 给响应消息赋值
        this.data = data;   // 给响应数据赋值
    }

    // 成功（无数据）
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    // 成功（有数据）
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    // 成功（自定义消息+数据）
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    // 失败（自定义消息）
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    // 失败（自定义码+消息）
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}