package com.campushub.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台分类管理 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryManagementDTO {
    private Integer id;
    private String name;
    private String icon;
    private Boolean hasReview;
    private Integer sortOrder;
}
