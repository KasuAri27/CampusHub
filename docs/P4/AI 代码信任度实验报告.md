# AI 代码信任度实验报告

本报告旨在评估 AI 生成的 Spring Boot 业务代码在实际生产环境中的可用性、规范性与安全性，并与团队人工审查修复后的最终代码进行对比分析。

---

## 1. 实验测试用例

### 1.1 实验输入 (Prompt)
 你是一个熟练的 Spring Boot 开发工程师。请帮我实现 ReviewService 类中的 submitReview 方法

### 1.2 AI 直出结果 (原始生成代码)
```java
@Transactional
public void submitReview(Long orderId, Long currentUserId, Integer score, String comment) {
    // 1. 获取订单
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("订单不存在"));

    // 2. 验证当前用户是该订单的接单方还是发布方，并且订单已完成
    if (!order.getRequester().getId().equals(currentUserId) && 
        !order.getProvider().getId().equals(currentUserId)) {
        throw new BusinessException("您没有权限评价该订单");
    }

    if (order.getStatus() != OrderStatus.COMPLETED) {
        throw new BusinessException("只有完成的订单才能评价");
    }

    // 3. 防止重复评价
    if (reviewRepository.existsByOrder_Id(orderId)) {
        throw new BusinessException("该订单已被评价");
    }

    // 4. 创建 Review 对象
    Review review = new Review();
    review.setOrder(order);
    review.setScore(score);
    review.setComment(comment);
    reviewRepository.save(review);

    // 5. 更新被评价人的信用分
    User reviewee = order.getRequester().getId().equals(currentUserId) ? order.getProvider() : order.getRequester();
    applyCreditScore(reviewee, score);
}
```

| 指标 | AI 直出（第二版精调代码） | 人工审查修复后（团队原版代码） |
| :--- | :--- | :--- |
| **编译是否通过** | **部分通过 / 存在隐患**<br>若自定义异常 `BusinessException` 在底层强制要求传入 HTTP/业务错误码状态器，则直接**编译失败**；若框架支持单参数构造器则可通过编译。 | **是**<br>完全符合项目现有架构规范。 |
| **功能是否可运行** | **否（存在核心运行时 Bug 与漏项）**<br>1. 当前端传入 `score` 为 `null` 时，由于自动拆箱直接触发 `NullPointerException` 导致服务崩溃。<br>2. 业务功能越权：无法卡控不支持评价的任务分类。 | **是**<br>完全可运行。具备极强的防御性编程意识，跑通核心与边缘业务流。 |
| **测试是否通过** | **否**<br>无法通过边界值测试（如 `null` 输入测试）、特定分类拦截测试以及全局异常拦截器的状态码校验测试。 | **是**<br>核心业务逻辑及异常路径的测试 **全部通过**。 |
| **主要问题/<br>修复说明** | **主要问题：**<br>1. **隐式语法陷阱（包装类型拆箱 NPE）**：AI 未对 `Integer score` 做 `null` 检查即进行大小比较，导致自动拆箱遭遇空指针崩溃。<br>2. **深层业务逻辑缺失**：缺乏全局领域感知，漏掉了深层级对象（任务分类 `HasReview`）的权限卡控。<br>3. **架构规范偏离**：忽略项目统一错误码设计（如 400, 403, 404, 409）。 | **修复说明：**<br>1. **前置短路校验**：增加 `score == null` 校验，彻底杜绝拆箱 NPE 隐患。<br>2. **补齐业务切面**：引入 `order.getTask().getCategory().getHasReview()` 校验，卡死分类权限。<br>3. **规范化重构**：统一注入正确的业务状态码，回归产品设计闭环。 |
