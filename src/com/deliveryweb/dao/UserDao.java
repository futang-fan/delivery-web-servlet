package com.deliveryweb.dao;

import com.deliveryweb.pojo.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户数据访问接口（MyBatis Mapper，注解实现）。
 */
public interface UserDao {

    String BASE_COLUMNS = "id, username, password, role, phone, status, created_at";

    @Select("SELECT " + BASE_COLUMNS + " FROM user WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT " + BASE_COLUMNS + " FROM user WHERE phone = #{phone}")
    User findByPhone(@Param("phone") String phone);

    @Select("SELECT " + BASE_COLUMNS + " FROM user WHERE id = #{id}")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO user (username, password, role, phone, status, created_at) "
            + "VALUES (#{username}, #{password}, #{role}, #{phone}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 管理员用户分页：角色/状态可选筛选，关键字匹配用户名或手机号 */
    @Select("<script>"
            + "SELECT " + BASE_COLUMNS + " FROM user "
            + "<where>"
            + "<if test=\"role != null and role != ''\">AND role = #{role} </if>"
            + "<if test=\"status != null and status != ''\">AND status = #{status} </if>"
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (username LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "</where>"
            + "ORDER BY id DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<User> listByScope(@Param("role") String role,
                           @Param("status") String status,
                           @Param("keyword") String keyword,
                           @Param("offset") int offset,
                           @Param("size") int size);

    @Select("<script>"
            + "SELECT COUNT(*) FROM user "
            + "<where>"
            + "<if test=\"role != null and role != ''\">AND role = #{role} </if>"
            + "<if test=\"status != null and status != ''\">AND status = #{status} </if>"
            + "<if test=\"keyword != null and keyword != ''\">"
            + "AND (username LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%')) </if>"
            + "</where>"
            + "</script>")
    long countByScope(@Param("role") String role,
                      @Param("status") String status,
                      @Param("keyword") String keyword);

    /** 禁用/启用状态更新 */
    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新密码摘要（改密与忘记密码重置共用） */
    @Update("UPDATE user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /** 更新手机号（账户中心改手机号） */
    @Update("UPDATE user SET phone = #{phone} WHERE id = #{id}")
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);
}
