package com.campushub.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 用户实体类，映射数据库 {@code users} 表.
 * <p>
 * 账号角色只分普通用户与管理员，见 {@link Role}；普通用户不区分需求方/服务方。
 * </p>
 * <p>
 * 敏感字段处理：
 * <ul>
 * <li>密码通过 bcrypt 加盐哈希后存入，永不明文存储</li>
 * <li>手机号通过确定性 AES 加密后存入 {@code phone}（需按密文做唯一约束和查找），API 返回时需脱敏</li>
 * <li>真实姓名通过 AES-GCM 加密后存入 {@code name}，见 {@link EncryptedNameConverter}</li>
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

    /** 管理员账号的固定姓名：管理员不登记真实姓名。 */
    public static final String ADMIN_NAME = "管理员";

    /** 用户唯一标识，数据库自增主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 学号：密码登录账号，也是 JWT 的 subject；注册时必填并绑定，全局唯一，不允许为空。
     * 注册后不可更改（updatable = false：任何 UPDATE 都不会带上该列，改了属性也不落库）。
     */
    @Column(unique = true, length = 20, nullable = false, updatable = false)
    private String studentId;

    /**
     * 真实姓名，明文最长 20 字符（由注册入参校验），注册后不可更改。
     * 落库前经 {@link EncryptedNameConverter} 加密，列宽 255 容纳的是密文而非明文；
     * 本属性始终是明文。密文每次不同，不能按姓名写 SQL 条件查询。
     * 管理员不登记真实姓名，固定为 {@link #ADMIN_NAME}，见 {@link #enforceAdminName()}。
     */
    @Convert(converter = EncryptedNameConverter.class)
    @Column(length = 255, nullable = false, updatable = false)
    private String name;

    /**
     * 用户昵称：最长 10 个汉字或 20 个英文字符（汉字计 2，英文数字计 1）。
     * 列宽 20 只限制字符数，按显示宽度的限制由数据库约束 chk_users_nickname_length 兜底。
     */
    @Column(length = 20)
    private String nickname;

    /**
     * 校园邮箱，全局唯一，只用于接收验证码（不是登录账号）；由学号生成 学号@smail.nju.edu.cn。
     * 随学号一起在注册时确定，之后不可更改。
     */
    @Column(length = 100, unique = true, nullable = false, updatable = false)
    private String email;

    /** 手机号，加密存储，数据库唯一约束 */
    @Column(unique = true, nullable = false)
    private String phone;

    /** 密码（bcrypt 哈希存储） */
    @Column(name = "password_hash", nullable = false)
    private String password;

    /** 账号角色：普通用户或管理员，见 {@link Role}。 */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('user','admin')", nullable = false)
    private Role role;

    /** 信用评分，默认 100 */
    @Column(nullable = false)
    @Builder.Default
    private Integer creditScore = 100;

    /** 头像图片 URL，最长 500 字符 */
    @Column(length = 500)
    private String avatar;

    /** 学院院系：注册时可选填，可在个人资料中修改；未填写为 null。 */
    @Column(length = 50)
    private String college;

    /** 账号状态：1=正常，0=封禁。封禁后仍可受限登录，只保留本人订单、付款、投诉和账号安全入口。 */
    @Column(nullable = false)
    @Builder.Default
    private Integer status = 1;

    /** 记录创建时间，由数据库自动填充，不可更新 */
    @CreationTimestamp
    @Column(name = "create_time", updatable = false, nullable = false)
    private LocalDateTime createTime;

    /** 记录更新时间，每次 UPDATE 由数据库自动刷新 */
    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * 管理员姓名强制为 {@link #ADMIN_NAME}，创建时无需提供真实姓名，之后也无法修改。
     * <p>
     * 入库前覆盖一次，保证新建的管理员行存的就是固定姓名；读出后再覆盖一次，
     * 这样即使库里的管理员行是用 SQL 直接写入的其他姓名，业务代码看到的也始终是固定姓名。
     * 读出后的覆盖不会触发 UPDATE：姓名列是 updatable = false。
     * </p>
     */
    @PrePersist
    @PostLoad
    void enforceAdminName() {
        if (role == Role.admin) {
            name = ADMIN_NAME;
        }
    }

    /**
     * 账号角色。
     * <p>
     * 普通用户不再区分“需求方/服务方”：同一账号既能发布需求也能接单，不能接自己的单
     * 由接单逻辑校验。需求与订单中的 requester/provider 表示“这一单的发布者/接单人”，
     * 与账号角色无关。管理员只做审核与监管，不发布、不接单。
     * </p>
     */
    public enum Role {
        /** 普通用户：可发布需求，也可接他人的单。 */
        user,
        /** 管理员：审核需求、处理投诉、登记结算。 */
        admin
    }
}