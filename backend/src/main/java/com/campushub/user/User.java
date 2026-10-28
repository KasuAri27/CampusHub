package com.campushub.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体类，映射数据库 {@code users} 表.
 * <p>
 * 用户是平台的核心参与者，支持三种角色：
 * <ul>
 * <li>{@code requester} — 需求方，可以发布互助需求</li>
 * <li>{@code provider} — 服务方，可以接单提供帮助</li>
 * <li>{@code admin} — 管理员，拥有后台管理权限</li>
 * </ul>
 * </p>
 * <p>
 * 敏感字段处理：
 * <ul>
 * <li>密码通过 bcrypt 加盐哈希后存入，永不明文存储</li>
 * <li>手机号通过 AES-256 加密后存入 {@code phone}，API 返回时需脱敏</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /** 用户唯一标识，数据库自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学号，唯一标识 */
    @Column(unique = true, length = 20)
    private String studentId;

    /** 用户真实姓名，最长 20 字符 */
    @Column(length = 20)
    private String name;

    /** 用户昵称 */
    private String nickname;

    /** 学校邮箱，全局唯一，用于登录和身份验证 */
    @Column(length = 100, unique = true)
    private String email;

    /** 手机号，加密存储，数据库唯一约束 */
    @Column(unique = true, nullable = false)
    private String phone;

    /** 密码（bcrypt 哈希存储） */
    @Column(name = "password_hash", nullable = false)
    private String password;

    /** 用户角色枚举 */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('requester','provider','admin')")
    private Role role;

    /** 信用评分，默认 100 */
    @Column(nullable = false)
    @Builder.Default
    private Integer creditScore = 100;

    /** 头像图片 URL，最长 500 字符 */
    @Column(length = 500)
    private String avatar;

    /** 账号状态：1=正常，0/其他=禁用 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /** 记录创建时间，由数据库自动填充，不可更新 */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 记录更新时间，每次 UPDATE 由数据库自动刷新 */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 用户角色枚举 */
    public enum Role {
        requester,
        provider,
        admin
    }
}