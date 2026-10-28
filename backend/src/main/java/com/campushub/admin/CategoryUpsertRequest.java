package com.campushub.admin;

import lombok.Data;

/**
 * 管理后台新增/编辑分类请求体.
 */
@Data
public class CategoryUpsertRequest {
    private String name;
    private String icon;
    private Boolean hasReview;
    private Integer sortOrder;
}
