package com.campushub.category;

import com.campushub.common.BusinessException;
import com.campushub.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 需求分类写服务（交易核心拥有 categories 表，模块契约 §7 写权限）。
 * 后台（admin）只做管理员鉴权，增删改一律经此处，不再直接写 CategoryRepository（K1 遗留项）。
 * 入参全部是标量，避免交易核心依赖后台包的请求类型。调用方须已确认操作人是管理员。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    /** 新增分类；hasReview 缺省为不开放评价，sortOrder 缺省为 0。名称唯一性由库唯一索引保证。 */
    @Transactional
    public Category create(String name, String icon, Boolean hasReview, Integer sortOrder) {
        return categoryRepository.save(Category.builder()
                .name(name).icon(icon)
                .hasReview(hasReview != null && hasReview)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build());
    }

    /** 局部更新：入参为 null 的字段保持原值。 */
    @Transactional
    public Category update(Integer categoryId, String name, String icon, Boolean hasReview, Integer sortOrder) {
        Category c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(404, "分类不存在"));
        if (name != null) c.setName(name);
        if (icon != null) c.setIcon(icon);
        if (hasReview != null) c.setHasReview(hasReview);
        if (sortOrder != null) c.setSortOrder(sortOrder);
        return categoryRepository.save(c);
    }

    /**
     * 删除分类。仍被任何需求（含历史、已取消的需求）引用的分类不能删除，返回 409，
     * 不能把外键冲突冒成 500，更不能级联删需求——需求下挂着订单与资金审计记录。
     * 与新发布需求并发时，库外键 fk_task_category 是最后一道防线。
     */
    @Transactional
    public void delete(Integer categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(404, "分类不存在");
        }
        if (taskRepository.existsByCategory_Id(categoryId)) {
            throw new BusinessException(409, "该分类下仍有需求，不能删除");
        }
        categoryRepository.deleteById(categoryId);
    }
}
