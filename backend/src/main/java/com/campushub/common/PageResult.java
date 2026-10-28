package com.campushub.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页查询结果通用包装类.
 * <p>
 * 用于统一后端分页返回给前端的 JSON 结构，包含当前页数据列表及标准分页元数据。
 * 支持直接通过 Spring Data 的 {@link Page} 对象进行便捷构建。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页的数据列表 */
    private List<T> items;

    /** 分页元数据信息 */
    private Pagination pagination;

    /**
     * 手动构建分页结果的构造函数（自动计算总页数）.
     *
     * @param items    当前页数据列表
     * @param total    总记录数
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页记录数
     */
    public PageResult(List<T> items, long total, int page, int pageSize) {
        this.items = items;
        this.pagination = new Pagination(page, pageSize, total);
    }

    /**
     * 静态工厂方法：从 Spring Data JPA 的 {@link Page} 对象直接转换.
     * <p>
     * 注意：Spring Data JPA 的页码默认从 0 开始，此处转换会自动 +1 以符合前端通常从 1 开始的习惯。
     * </p>
     *
     * @param page Spring Data JPA 的 Page 对象
     * @param <T>  数据实体类型
     * @return 统一结构的分页包装对象
     */
    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize()
        );
    }

    /**
     * 分页元数据内部类.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        /** 当前页码（从 1 开始） */
        private int page;

        /** 每页记录数 */
        private int pageSize;

        /** 总记录数 */
        private long total;

        /** 总页数 */
        private int totalPages;

        /**
         * 内部构造函数，传入核心参数并自动计算总页数.
         *
         * @param page     当前页码
         * @param pageSize 每页记录数
         * @param total    总记录数
         */
        public Pagination(int page, int pageSize, long total) {
            this.page = page;
            this.pageSize = pageSize;
            this.total = total;
            this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        }
    }
}