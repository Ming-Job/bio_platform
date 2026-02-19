package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bio_platform.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用户Mapper接口（手动编写）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 基础CRUD由BaseMapper提供，如需自定义SQL可在此添加


    /**
    *   根据用户名查询用户
    * */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);


    /**
    *   根据用户名和密码查询用户（用户登录）
    * */
    @Select("SELECT * FROM user WHERE username = #{username} AND password = #{password}")
    User selectByUsernameAndPassword(@Param("username") String username,
                                     @Param("password") String password);

    /**
     * 根据角色查询用户
     *
     * */
    @Select("SELECT * FROM user WHERE role = #{role} ORDER BY create_time DESC")
    List<User> selectByRole(@Param("role") String role);

    /**
     *  根据角色分页查询用户
     * */
    @Select("SELECT * FROM user WHERE role = #{role}")
    IPage<User> selectPageByRole(Page<User> page, @Param("role") String role);

    /**
     *  更新用户状态
     * */
    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int UpdateStatus(@Param("id") Long id,
                     @Param("status") Integer status);


    /**
     * 修改用户密码（根据用户名和邮箱）
     */
    @Update("UPDATE user SET password = #{newPassword} WHERE id = #{id}")
    int UpdatePassword(@Param("id") Long id,
                       @Param("password") Integer password);

    /**
     * 更新用户最后登录时间
     */
    @Update("UPDATE user SET last_login_time = NOW() WHERE id = #{id}")
    int updateLastLoginTime(@Param("id") Long id);

    /**
     * 批量更新用户状态
     */
    @Update({
            "<script>",
            "UPDATE user SET status = #{status} WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int updateBatchStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     *  批量删除用户
     * */
    @Delete({
            "<script>",
            "DELETE FROM user WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</script>"
    })
    int batchDelete(@Param("ids") List<Long> ids);

    /**
     * 统计各角色用户数量
     */
    @Select("SELECT role, COUNT(*) as count FROM user GROUP BY role")
    List<Map<String, Object>> countByRole();


    /**
     * 查询用户数量
     */
    @Select("SELECT COUNT(*) FROM user")
    Long countUsers();

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM user WHERE username = #{username}")
    Integer checkUsernameExists(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     */
    @Select("SELECT * FROM user WHERE email = #{email}")
    User selectByEmail(@Param("email") String email);

    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User selectByPhone(@Param("phone") String phone);


    /**
     * 使用@Results注解进行结果映射（复杂映射时使用）
     */
    @Select("SELECT u.*, COUNT(o.id) as order_count FROM user u LEFT JOIN orders o ON u.id = o.user_id WHERE u.id = #{id}")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "username", property = "username"),
            @Result(column = "order_count", property = "orderCount")
    })
    Map<String, Object> selectUserWithStats(@Param("id") Long id);

    /**
     * 统计最近N天的新增用户数（生成连续日期序列）
     * @param days 最近多少天（例如：7, 30, 90）
     * @return 每天的新增用户数和累计用户数
     */
    @Select({
            "WITH RECURSIVE date_series AS (",
            "    SELECT CURDATE() - INTERVAL #{days} DAY + INTERVAL 1 DAY as date",
            "    UNION ALL",
            "    SELECT date + INTERVAL 1 DAY",
            "    FROM date_series",
            "    WHERE date < CURDATE()",  // 直接使用 <，不要CDATA
            ")",
            "SELECT ",
            "    DATE_FORMAT(ds.date, '%Y-%m-%d') as date,",  // 格式化为字符串
            "    COALESCE(COUNT(u.id), 0) as new_users,",
            "    (",
            "        SELECT COUNT(*) ",
            "        FROM user ",
            "        WHERE DATE(create_time) <= ds.date",
            "    ) as total_users",
            "FROM date_series ds",
            "LEFT JOIN user u ON DATE(u.create_time) = ds.date",
            "GROUP BY ds.date",
            "ORDER BY ds.date ASC"
    })
    List<Map<String, Object>> selectRecentUserGrowth(@Param("days") Integer days);


    // 更新用户头像（也可用MyBatis-Plus的updateById）
    @Update("UPDATE user SET avatar = #{avatar} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long userId, @Param("avatar") String avatar);

    // 根据ID获取用户姓名
    @Select("SELECT username FROM user where id = #{id}")
    String getUsername(@Param("id") Long userId);

}

