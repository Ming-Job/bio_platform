package com.example.bio_platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.dto.LoginRequest;
import com.example.bio_platform.dto.LoginResponse;
import com.example.bio_platform.dto.RegisterRequest;
import com.example.bio_platform.dto.RegisterResponse;
import com.example.bio_platform.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * 根据用户名查询用户
     */
    User getUserByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    User getUserByEmail(String email);

    /**
     * 根据手机号查询用户
     */
    User getUserByPhone(String phone);

    /**
     * 检查用户名是否存在
     */
    boolean checkUsernameExists(String username);

    /**
     * 修改密码
     */
    boolean changePassword(Long id, String oldPassword, String newPassword);

    /**
     * 重置密码
     */
    boolean resetPassword(Long id, String newPassword);

    /**
     * 更新用户状态
     */
    boolean updateUserStatus(Long id, Integer status);

    /**
     * 分页查询用户
     */
    IPage<User> getUserPage(Page<User> page, Map<String, Object> params);

    /**
     * 统计用户数据
     */
    Map<String, Object> getUserStatistics();

    /**
     * 批量更新用户状态
     *
     * @param ids 用户ID列表
     * @param status 状态：0-禁用，1-启用
     * @return 更新成功的数量
     */
    int batchUpdateStatus(List<Long> ids, Integer status);

    /**
     * 批量删除用户
     *
     * @param ids 用户ID列表
     * @return 删除成功的数量
     */
    int batchDeleteUsers(List<Long> ids);

    /**
     * 获取用户增长趋势数据（按天统计）
     * @param days 最近多少天（例如：7, 30, 90）
     * @return 每日新增用户数和累计用户数
     */
    List<Map<String, Object>> getUserGrowthTrend(Integer days);

    /**
     * 更新用户头像
     * @param userId 用户ID
     * @param file 头像文件
     * @return 新头像路径
     */
    String updateUserAvatar(Long userId, MultipartFile file);

    String getUsername(Long userId);


}