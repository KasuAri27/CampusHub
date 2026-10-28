package com.campushub.category;

import com.campushub.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类控制器 — 提供前端下拉选择所需的数据.
 */
@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    /**
     * GET /v1/categories — 获取全部分类列表（无需登录）.
     * 按 sort_order 升序排列，返回 id + name，供前端下拉框使用。
     */
    @GetMapping
    public ApiResponse<List<CategoryDTO>> listCategories() {
        List<CategoryDTO> list = categoryRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(c -> new CategoryDTO(c.getId(), c.getName(), c.getIcon()))
                .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @lombok.AllArgsConstructor
    @lombok.Data
    static class CategoryDTO {
        private Integer id;
        private String name;
        private String icon;
    }
}
