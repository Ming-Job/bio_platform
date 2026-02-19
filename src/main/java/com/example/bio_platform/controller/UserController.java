package com.example.bio_platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.common.Result;
import com.example.bio_platform.dto.*;
import com.example.bio_platform.entity.User;
import com.example.bio_platform.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
@Api(tags = "用户管理接口")
@CrossOrigin
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    @ApiOperation("用户登录")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @ApiParam(value = "登录请求", required = true)
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = userService.login(request);
        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @ApiOperation("用户注册")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @ApiParam(value = "注册请求", required = true)
            @Valid @RequestBody RegisterRequest request) {

        RegisterResponse response = userService.register(request);
        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @ApiOperation("检查用户名是否存在")
    @GetMapping("/check-username/{username}")
    public ResponseEntity<Map<String, Object>> checkUsernameExists(
            @ApiParam(value = "用户名", required = true) @PathVariable String username) {

        boolean exists = userService.checkUsernameExists(username);
        Map<String, Object> result = new HashMap<>();
        result.put("exists", exists);
        result.put("message", exists ? "用户名已存在" : "用户名可用");
        return ResponseEntity.ok(result);
    }

    @ApiOperation("修改密码")
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long id,
            @ApiParam(value = "原密码", required = true) @RequestParam String oldPassword,
            @ApiParam(value = "新密码", required = true) @RequestParam String newPassword) {

        boolean success = userService.changePassword(id, oldPassword, newPassword);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "密码修改成功" : "密码修改失败");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @ApiOperation("更新用户状态")
    @PostMapping("/update-status")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long id,
            @ApiParam(value = "状态：0-禁用，1-启用", required = true) @RequestParam Integer status) {

        boolean success = userService.updateUserStatus(id, status);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "状态更新成功" : "状态更新失败");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @ApiOperation("分页查询用户")
    @GetMapping("/page")
    public ResponseEntity<IPage<User>> getUserPage(
            @ApiParam(value = "当前页", defaultValue = "1") @RequestParam(defaultValue = "1") Integer pageNum,
            @ApiParam(value = "每页大小", defaultValue = "10") @RequestParam(defaultValue = "10") Integer pageSize,
            @ApiParam(value = "用户名") @RequestParam(required = false) String username,
            @ApiParam(value = "角色") @RequestParam(required = false) String role,
            @ApiParam(value = "状态") @RequestParam(required = false) Integer status) {

        Page<User> page = new Page<>(pageNum, pageSize);
        Map<String, Object> params = new HashMap<>();

        if (username != null) params.put("username", username);
        if (role != null) params.put("role", role);
        if (status != null) params.put("status", status);

        IPage<User> userPage = userService.getUserPage(page, params);
        return ResponseEntity.ok(userPage);
    }

    @ApiOperation("用户统计")
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUserStatistics() {
        Map<String, Object> statistics = userService.getUserStatistics();
        return ResponseEntity.ok(statistics);
    }

    // 原有的接口保持不变，优化部分实现

    @ApiOperation("获取所有用户")
    @GetMapping("/list")
    public ResponseEntity<List<User>> getUserList() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        List<User> list = userService.list(wrapper);
        return ResponseEntity.ok(list);
    }

    @ApiOperation("根据ID查询用户")
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(
            @ApiParam(value = "用户ID", required = true, example = "1") @PathVariable Long id) {

        User user = userService.getById(id);
        return ResponseEntity.ok(user);
    }

    @ApiOperation("新增用户")
    @PostMapping("/save")
    public ResponseEntity<Boolean> saveUser(
            @ApiParam(value = "用户信息", required = true) @RequestBody User user) {

        // 对密码进行加密
        if (user.getPassword() != null) {
            // 实际加密逻辑应该在Service层，这里简单处理
            String encryptedPassword = org.springframework.util.DigestUtils.md5DigestAsHex(
                    (user.getPassword() + "bio_platform_salt_2024").getBytes()
            ).toUpperCase();
            user.setPassword(encryptedPassword);
        }

        boolean save = userService.save(user);
        return ResponseEntity.ok(save);
    }

    @ApiOperation("修改用户")
    @PutMapping("/update")
    public ResponseEntity<Boolean> updateUser(
            @ApiParam(value = "用户信息", required = true) @RequestBody User user) {

        boolean update = userService.updateById(user);
        return ResponseEntity.ok(update);
    }

    /**
     * 更新用户头像
     */
    @PostMapping("/avatar/update")
    public Result<String> updateUserAvatar(
            @RequestParam("userId") Long userId,
            @RequestParam("avatar") MultipartFile avatar
    ) {
        try {
            String newAvatar = userService.updateUserAvatar(userId, avatar);
            return Result.success(newAvatar);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }


    @ApiOperation("删除用户")
    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteUser(
            @ApiParam(value = "用户ID", required = true, example = "1") @PathVariable Long id) {

        boolean remove = userService.removeById(id);
        return ResponseEntity.ok(remove);
    }

    @ApiOperation("根据用户名查询用户")
    @GetMapping("/username/{username}")
    public ResponseEntity<User> getUserByUsername(
            @ApiParam(value = "用户名", required = true, example = "zhangsan") @PathVariable String username) {

        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @ApiOperation("根据用户ID查询用户名")
    @GetMapping("/get-username/{id}")
    public ResponseEntity<Result<String>> getUsername(
            @ApiParam(value = "用户ID", required = true, example = "1")
            @PathVariable Long id) {

        try {
            // 参数基础校验
            if (id == null || id <= 0) {
                return ResponseEntity.ok(Result.error("用户ID不合法"));
            }

            String username = userService.getUsername(id);

            // 判断用户是否存在
            if (username == null) {
                return ResponseEntity.ok(Result.error("用户不存在"));
            }

            return ResponseEntity.ok(Result.success(username));

        } catch (Exception e) {
            return ResponseEntity.ok(Result.error("查询用户名失败"));
        }
    }
    @ApiOperation("验证身份（忘记密码第一步）")
    @PostMapping("/verify-identity")
    public ResponseEntity<Map<String, Object>> verifyIdentity(
            @ApiParam(value = "身份验证请求", required = true)
            @Valid @RequestBody VerifyIdentityRequest request) {

        User user = userService.getUserByUsername(request.getUsername());
        Map<String, Object> result = new HashMap<>();

        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        if (!user.getEmail().equals(request.getEmail())) {
            result.put("success", false);
            result.put("message", "邮箱不匹配");
            return ResponseEntity.badRequest().body(result);
        }

        result.put("success", true);
        result.put("message", "身份验证成功");
        return ResponseEntity.ok(result);
    }

    @ApiOperation("通过用户id重置密码")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @ApiParam(value = "用户ID", required = true) @RequestParam Long id,
            @ApiParam(value = "新密码", required = true) @RequestParam String newPassword){

        boolean success = userService.resetPassword(id, newPassword);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "密码修改成功" : "密码修改失败");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);

    }

    @ApiOperation("通过用户名和邮箱重置密码")
    @PostMapping("/forget-password")
    public ResponseEntity<Map<String, Object>> forgetPassword(
            @ApiParam(value = "忘记密码请求", required = true)
            @Valid @RequestBody ForgetPasswordRequest request) {

        // 先验证身份
        User user = userService.getUserByUsername(request.getUsername());
        if (user == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(result);
        }

        if (!user.getEmail().equals(request.getEmail())) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "邮箱不匹配");
            return ResponseEntity.badRequest().body(result);
        }

        // 重置密码
        boolean success = userService.resetPassword(user.getId(), request.getNewPassword());
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("success", true);
            result.put("message", "密码重置成功");
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "密码重置失败");
            return ResponseEntity.badRequest().body(result);
        }
    }


    @ApiOperation("批量更新用户状态")
    @PostMapping("/batch-update-status")
    public ResponseEntity<Map<String, Object>> batchUpdateUserStatus(
            @ApiParam(value = "用户ID列表，用逗号分隔", required = true) @RequestParam String ids,
            @ApiParam(value = "状态：0-禁用，1-启用", required = true) @RequestParam Integer status) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 将逗号分隔的字符串转换为Long列表
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (idList.isEmpty()) {
                result.put("success", false);
                result.put("message", "用户ID列表不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            if (status == null || (status != 0 && status != 1)) {
                result.put("success", false);
                result.put("message", "状态参数不合法，只能为0或1");
                return ResponseEntity.badRequest().body(result);
            }

            // 调用批量更新服务
            int updatedCount = userService.batchUpdateStatus(idList, status);

            result.put("success", true);
            result.put("message", "成功更新 " + updatedCount + " 个用户状态");
            result.put("updatedCount", updatedCount);
            result.put("totalCount", idList.size());

            return ResponseEntity.ok(result);

        } catch (NumberFormatException e) {
            result.put("success", false);
            result.put("message", "用户ID格式错误，请确保使用逗号分隔的数字ID");
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量更新用户状态失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @ApiOperation("批量删除用户")
    @PostMapping("/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeleteUsers(
            @ApiParam(value = "用户ID列表，用逗号分隔", required = true) @RequestParam String ids) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 将逗号分隔的字符串转换为Long列表
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            if (idList.isEmpty()) {
                result.put("success", false);
                result.put("message", "用户ID列表不能为空");
                return ResponseEntity.badRequest().body(result);
            }

            // 调用批量删除服务
            int deletedCount = userService.batchDeleteUsers(idList);

            result.put("success", true);
            result.put("message", "成功删除 " + deletedCount + " 个用户");
            result.put("deletedCount", deletedCount);
            result.put("totalCount", idList.size());

            return ResponseEntity.ok(result);

        } catch (NumberFormatException e) {
            result.put("success", false);
            result.put("message", "用户ID格式错误，请确保使用逗号分隔的数字ID");
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "批量删除用户失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

}