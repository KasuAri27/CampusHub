package com.campushub.category;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 需求分类实体类，映射数据库 {@code categories} 表.
 * <p>
 * 每个互助需求都归属于一个分类，不同分类决定：
 * <ul>
 *   <li>该分类下的需求是否支持评价功能（{@code hasReview} 字段控制）</li>
 *   <li>分类在前端列表中的显示顺序（{@code sortOrder} 升序排列）</li>
 * </ul>
 * </p>
 * <p>
 * 当前预置分类（见 03-init-data.sql）：快递代取、学习辅导（支持评价）、
 * 二手交易、组队匹配（不支持评价）
 * </p>
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    /** 分类唯一标识，数据库自增主键（INT 类型，因为分类数量有限） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 分类名称（如"快递代取"），全局唯一，最长 20 字符 */
    @Column(nullable = false, length = 20, unique = true)
    private String name;

    /** 分类图标 URL，用于前端分类选择页展示，最长 255 字符 */
    @Column(length = 255)
    private String icon;

    /**
     * 是否支持评价功能.
     * true=支持（订单完成后服务方可评价需求方），false=不支持。
     * 默认 false，建表后在 init-data 中对快递代取、学习辅导设为 1（true）。
     */
    @Column(name = "has_review", nullable = false)
    @Builder.Default
    private Boolean hasReview = false;

    /** 排序权重，用于前端分类列表展示顺序，数值越小越靠前 */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** 记录创建时间，由 JPA 回调自动填充 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA 持久化前自动调用，设置创建时间为当前时刻.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
