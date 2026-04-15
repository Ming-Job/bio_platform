package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.dto.LoginRequest;
import com.example.bio_platform.dto.LoginResponse;
import com.example.bio_platform.dto.RegisterRequest;
import com.example.bio_platform.dto.RegisterResponse;
import com.example.bio_platform.entity.User;
import com.example.bio_platform.mapper.UserMapper;
import com.example.bio_platform.service.UserService;
import com.example.bio_platform.utils.AvatarUploadUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AvatarUploadUtil avatarUploadUtil;



    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        try {
            log.info("用户登录: {}", request.getUsername());

            // 1. 参数校验
            if (StringUtils.isBlank(request.getUsername()) || StringUtils.isBlank(request.getPassword())) {
                return LoginResponse.error("用户名或密码不能为空");
            }

            // 2. 查询用户
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("username", request.getUsername());
            User user = this.getOne(wrapper);

            if (user == null) {
                return LoginResponse.error("用户不存在");
            }

            // 3. 检查用户状态
            if (user.getStatus() != null && user.getStatus() == 0) {
                return LoginResponse.error("账号已被禁用");
            }

            // 4. 验证密码 - 关键：后端统一加密逻辑
            String encryptedPassword = encryptPassword(request.getPassword());
            if (!user.getPassword().equals(encryptedPassword)) {
                return LoginResponse.error("密码错误");
            }

            // 5. 更新最后登录时间
            user.setLastLoginTime(LocalDateTime.now());
            this.updateById(user);

            // 6. 生成token（简单实现，实际项目建议用JWT）
            String token = generateToken(user);

            // 7. 记录登录日志
            log.info("用户登录成功: {}, 角色: {}", user.getUsername(), user.getRole());

            return LoginResponse.success(user, token);
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage(), e);
            return LoginResponse.error("登录失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterResponse register(RegisterRequest request) {
        try {
            log.info("用户注册: {}", request.getUsername());

            // 1. 参数校验
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return RegisterResponse.error("两次输入的密码不一致");
            }

            // 2. 检查用户名是否已存在
            if (checkUsernameExists(request.getUsername())) {
                return RegisterResponse.error("用户名已存在");
            }

            // 3. 检查邮箱是否已存在（如果提供了邮箱）
            if (StringUtils.isNotBlank(request.getEmail())) {
                User existingEmailUser = getUserByEmail(request.getEmail());
                if (existingEmailUser != null) {
                    return RegisterResponse.error("邮箱已被注册");
                }
            }

            // 4. 检查手机号是否已存在（如果提供了手机号）
            if (StringUtils.isNotBlank(request.getPhone())) {
                User existingPhoneUser = getUserByPhone(request.getPhone());
                if (existingPhoneUser != null) {
                    return RegisterResponse.error("手机号已被注册");
                }
            }

            // 5. 创建用户对象
            User user = new User();
            BeanUtils.copyProperties(request, user);
            user.setPassword(encryptPassword(request.getPassword()));
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());

            // 设置默认头像
            if (StringUtils.isBlank(user.getAvatar())) {
                user.setAvatar("/avatar/1.jpg");
            }

            // 6. 保存用户
            boolean saved = this.save(user);
            if (!saved) {
                return RegisterResponse.error("注册失败");
            }

            // 7. 生成token并返回
            String token = generateToken(user);
            log.info("用户注册成功: {}, ID: {}", user.getUsername(), user.getId());

            return RegisterResponse.success(user, token);
        } catch (Exception e) {
            log.error("注册失败: {}", e.getMessage(), e);
            return RegisterResponse.error("注册失败: " + e.getMessage());
        }
    }

    @Override
    public User getUserByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return this.getOne(wrapper);
    }

    @Override
    public User getUserByEmail(String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("email", email);
        return this.getOne(wrapper);
    }

    @Override
    public User getUserByPhone(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        return this.getOne(wrapper);
    }

    @Override
    public boolean checkUsernameExists(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return this.count(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        try {
            User user = this.getById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }

            String encryptedOldPassword = encryptPassword(oldPassword);
            if (!user.getPassword().equals(encryptedOldPassword)) {
                throw new RuntimeException("原密码错误");
            }

            user.setPassword(encryptPassword(newPassword));
            user.setUpdateTime(LocalDateTime.now());
            return this.updateById(user);
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String newPassword) {
        try {
            User user = this.getById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }

            user.setPassword(encryptPassword(newPassword));
            user.setUpdateTime(LocalDateTime.now());
            return this.updateById(user);
        } catch (Exception e) {
            log.error("重置密码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean updateUserStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        return this.updateById(user);
    }

    @Override
    public IPage<User> getUserPage(Page<User> page, Map<String, Object> params) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();

        if (params != null) {
            // 用户名模糊查询
            if (params.containsKey("username") && StringUtils.isNotBlank(params.get("username").toString())) {
                wrapper.like("username", params.get("username").toString());
            }

            // 角色精确查询
            if (params.containsKey("role") && StringUtils.isNotBlank(params.get("role").toString())) {
                wrapper.eq("role", params.get("role").toString());
            }

            // 状态查询
            if (params.containsKey("status") && params.get("status") != null) {
                wrapper.eq("status", params.get("status"));
            }

            // 时间范围查询
            if (params.containsKey("startTime") && params.get("startTime") != null) {
                wrapper.ge("create_time", params.get("startTime"));
            }

            if (params.containsKey("endTime") && params.get("endTime") != null) {
                wrapper.le("create_time", params.get("endTime"));
            }

            // 排序
            if (params.containsKey("sortField") && params.containsKey("sortOrder")) {
                String sortField = params.get("sortField").toString();
                String sortOrder = params.get("sortOrder").toString();
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    wrapper.orderByAsc(sortField);
                } else {
                    wrapper.orderByDesc(sortField);
                }
            } else {
                wrapper.orderByDesc("create_time");
            }
        }

        return this.page(page, wrapper);
    }

    @Override
    public Map<String, Object> getUserStatistics() {
        Map<String, Object> result = new HashMap<>();

        // 总用户数
        Long totalUsers = userMapper.countUsers();
        result.put("totalUsers", totalUsers);

        // 各角色用户数
        List<Map<String, Object>> roleCounts = userMapper.countByRole();
        result.put("roleCounts", roleCounts);

        return result;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("用户ID列表不能为空");
        }

        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("状态参数不合法，只能为0或1");
        }

        log.info("批量更新用户状态，用户ID列表: {}, 状态: {}", ids, status);

        // 调用Mapper的批量更新方法
        int updatedCount = userMapper.updateBatchStatus(ids, status);

        log.info("批量更新用户状态完成，成功更新 {} 个用户", updatedCount);

        return updatedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDeleteUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("用户ID列表不能为空");
        }

        log.info("批量删除用户，用户ID列表: {}", ids);

        int deletedCount = userMapper.batchDelete(ids);

        log.info("批量删除用户完成，成功删除 {} 个用户", deletedCount);

        return deletedCount;
    }


    @Override
    public List<Map<String, Object>> getUserGrowthTrend(Integer days) {
        try {
            // 参数验证和默认值
            if (days == null || days <= 0) {
                days = 30;
            }

            // 限制最大查询天数
            if (days > 365) {
                days = 365;
            }

            // 调用Mapper方法
            return userMapper.selectRecentUserGrowth(days);

        } catch (Exception e) {
            log.error("获取用户增长趋势数据失败", e);
            return new ArrayList<>(); // 返回空列表而不是null
        }
    }


    @Override
    public String updateUserAvatar(Long userId, MultipartFile file) {
        try {
            // 1. 上传头像并获取路径
            String avatarPath = avatarUploadUtil.upload(file);
            // 2. 更新数据库
            int rows = userMapper.updateAvatar(userId, avatarPath);
            if (rows == 0) {
                throw new RuntimeException("用户不存在或头像更新失败");
            }
            return avatarPath;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败：" + e.getMessage());
        }
    }


    /**
     *  根据用户 ID 获取用户名
     * */
    @Override
    public String getUsername(Long userId) {
        // 简单参数校验
        if (userId == null || userId <= 0) {
            return null; // 或者返回空字符串，在Controller中统一处理
        }

        // 查询用户名
        String username = userMapper.getUsername(userId);

        // 如果查询结果为空，说明用户不存在
        if (username == null) {
            return null; // 返回null，在Controller中处理
        }

        return username;
    }

    /**
     * 密码加密（使用MD5）
     */
    private String encryptPassword(String password) {
        // 这里为了简单使用MD5加盐
        String salt = "bio_platform_salt_2024";
        String str = password + salt;
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        return DigestUtils.md5DigestAsHex(bytes).toUpperCase();
    }

    /**
     * 生成token（简单实现）
     * 实际项目中应该使用JWT
     */
    private String generateToken(User user) {
        // 简单实现：UUID + 用户ID
        String token = UUID.randomUUID().toString().replace("-", "");

        return token;
    }

    @Override
    public long countTodayNewUsers() {
        // 核心：查询 create_time >= 今天的 00:00:00
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.ge("create_time", todayStart);
        return this.count(wrapper);
    }

    @Override
    public Map<String, Object> getRealUserGrowthChart(int days) {
        // 1. 查询在此之前的所有老用户总数作为基数
        LocalDateTime startDate = LocalDateTime.now().minusDays(days - 1).withHour(0).withMinute(0).withSecond(0);
        QueryWrapper<User> baseWrapper = new QueryWrapper<>();
        baseWrapper.lt("create_time", startDate);
        long baseTotal = this.count(baseWrapper);

        // 2. 查出这 N 天内的所有用户，并只查 create_time 字段以提升性能
        QueryWrapper<User> rangeWrapper = new QueryWrapper<>();
        rangeWrapper.ge("create_time", startDate).select("create_time");
        List<User> recentUsers = this.list(rangeWrapper);

        // 3. 在 Java 内存中按日期分组聚合 (这是跨数据库最安全的做法)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d");
        Map<String, Integer> dailyCountMap = new HashMap<>();

        for (User user : recentUsers) {
            if (user.getCreateTime() != null) {
                String dateStr = user.getCreateTime().format(formatter);
                dailyCountMap.put(dateStr, dailyCountMap.getOrDefault(dateStr, 0) + 1);
            }
        }

        // 4. 按连续的日期序列组装结果
        List<String> dates = new ArrayList<>();
        List<Integer> newUsers = new ArrayList<>();
        List<Integer> totalUsers = new ArrayList<>();

        long currentTotal = baseTotal;

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            String dateStr = date.format(formatter);

            int newCount = dailyCountMap.getOrDefault(dateStr, 0);
            currentTotal += newCount;

            dates.add(dateStr);
            newUsers.add(newCount);
            totalUsers.add((int) currentTotal); // ECharts 通常处理 int 即可
        }

        // 5. 封装为前端需要的格式
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("newUsers", newUsers);
        result.put("totalUsers", totalUsers);

        return result;
    }



}