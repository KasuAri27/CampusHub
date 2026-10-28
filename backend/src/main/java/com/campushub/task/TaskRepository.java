package com.campushub.task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 需求数据访问层接口.
 * <p>
 * 同时继承两个接口：
 * <ul>
 *   <li>{@link JpaRepository} — 提供基础 CRUD（findById, findAll, save, delete 等）</li>
 *   <li>{@link JpaSpecificationExecutor} — 支持动态条件查询（Specification），
 *       用于需求列表的多条件筛选（分类、状态、关键词）</li>
 * </ul>
 * </p>
 * <p>
 * 泛型参数：{@code <Task, Long>} — 实体类型为 Task，主键类型为 Long。
 * </p>
 */
public interface TaskRepository extends JpaRepository<Task, Long>,
        JpaSpecificationExecutor<Task> {

    /**
     * 按 ID 查询需求并立即加载关联的发布者和分类信息.
     * <p>
     * 使用 JPQL {@code JOIN FETCH} 一次性查出 Task + User + Category，
     * 避免在详情页中触发 N+1 次懒加载查询。
     * 仅在详情接口（单条查询）中使用，因为 JOIN FETCH 与分页查询不兼容。
     * </p>
     *
     * @param id 需求主键 ID
     * @return 包含已加载 requester 和 category 的 Task Optional
     */
    @Query("SELECT t FROM Task t JOIN FETCH t.requester JOIN FETCH t.category WHERE t.id = :id")
    Optional<Task> findByIdWithDetails(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Task t SET t.status = :nextStatus WHERE t.id = :taskId AND t.status = :currentStatus")
    int updateStatusIfCurrent(@Param("taskId") Long taskId,
                              @Param("currentStatus") Task.TaskStatus currentStatus,
                              @Param("nextStatus") Task.TaskStatus nextStatus);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.deadline <= :now")
    List<Task> findExpiredTasks(@Param("status") Task.TaskStatus status, @Param("now") LocalDateTime now);
}
