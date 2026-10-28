package com.campushub.category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 分类数据访问层接口.
 * <p>
 * 继承 Spring Data JPA 的 {@link JpaRepository}，
 * 自动获得以下方法（无需手写 SQL）：
 * <ul>
 *   <li>{@code findById(Integer id)} — 按主键查分类</li>
 *   <li>{@code findAll()} — 查全部分类列表</li>
 *   <li>{@code save(Category)} — 新增/更新分类</li>
 *   <li>{@code deleteById(Integer id)} — 按主键删除分类</li>
 * </ul>
 * </p>
 * <p>
 * 泛型参数：{@code <Category, Integer>} — 实体类型为 Category，主键类型为 Integer。
 * </p>
 */
public interface CategoryRepository extends JpaRepository<Category, Integer> {
}
