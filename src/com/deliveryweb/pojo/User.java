package com.deliveryweb.pojo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应数据表 user。
 * 四类角色（CUSTOMER / SHOP / RIDER / ADMIN）统一账号。
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户编号（自增主键） */
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码加密摘要，存储格式“盐:摘要”，禁止明文 */
    private String password;

    /** 角色：CUSTOMER-顾客 / SHOP-商家 / RIDER-骑手 / ADMIN-管理员 */
    private String role;

    /** 手机号 */
    private String phone;

    /** 账户状态：NORMAL-正常 / DISABLED-禁用 */
    private String status;

    /** 注册时间 */
    private LocalDateTime createdAt;

    public User() {
    }

    public User(Long id, String username, String password, String role,
                String phone, String status, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", phone='" + phone + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
