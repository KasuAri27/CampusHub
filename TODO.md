# CampusHub 后续待办事项 (TODO)

本项目所有经过团队讨论与评审通过的后续改进、功能扩展及演进计划记录于此。

---

## 1. 业务与状态机演进

### 1.1 需求状态机细化：增加“被拒绝 (rejected)”状态
- **现状**：当前管理员审核驳回待审需求（`pending_review`）时，直接将其置为终态 `cancelled`（已取消），并通过站内信下发驳回原因。
- **改进目标**：
  - 在 `Task.TaskStatus` 枚举及数据库中新增独立状态 `rejected`；
  - 任务被驳回后状态变更为 `rejected`，发布者可在个人中心查阅驳回原因并支持“修改后重新提交审核”，避免重新从头录入全部表单。
- **进展**：2026-11-20 契约修订已批准并写入（API 契约 §3 状态总述、3.3 可见性、3.6 修改并重新提交、§9 驳回；模块契约 §7 预留迁移 M006）。分工：task 做表结构、实体、服务与接口（**已合入，093e44f，2026-11-22**）→ admin 补管理端驳回理由与状态筛选（待做）→ Frontend 做被驳回分栏与编辑重提页面（Frontend 分支 88c69e6，待合入）。

---

## 2. 架构与中间件演进

### 2.1 引入 Redis 缓存中间件
- **现状**：JWT 采用完全无状态的内存验签，会话由客户端 LocalStorage 持久化，验证码与高频未读数直接查数据库。依赖与连接配置已就绪，业务代码尚未使用 Redis。
- **改进目标**：
  - 引入 `spring-boot-starter-data-redis`（2026-11-15 批准并完成，连接走 `CAMPUSHUB_REDIS_*` 环境变量）；
  - 使用 Redis 缓存高频字典与广场热门需求列表，降低 MySQL 压力；
  - 实现分布式缓存与高频消息未读计数原子递增；
  - 引入 Redisson 统一管理跨实例分布式锁（如每日信用分自动恢复、热点订单接单互斥）。

---

## 3. 外部服务与硬件扩展

### 3.1 地图 SDK 与位置服务扩展
- **现状**：地点采用纯手工文本输入与校区下拉框（鼓楼、仙林、浦口、苏州），无地理坐标数据。
- **改进目标**：
  - 对接高德/百度地图 Web API；
  - 发布需求时支持地图图形化选点，自动保存经纬度坐标与标准地址名称；
  - 需求广场支持“按距离由近到远”排序及指定服务半径筛选。

### 3.2 移动端与小程序多端适配
- **现状**：当前为基于 Vue 3 + Element Plus 的桌面端 Web SPA。
- **改进目标**：
  - 评估 UniApp 或跨端框架，扩展移动端 H5 及微信小程序，提升学生随时随地发布与接单体验。

---

## 4. 模块间修复与健壮性测试（2026-11 批准方向）

后续开发重心：先修复各板块之间的耦合问题，再开展高并发与大数据量测试。问题编号与证据见 `docs/Engineer/模块契约.md` §9 与 `docs/Engineer/架构文档.md` §9。2026-11-20 全项目审查后按分支重排如下。

### 4.1 当前阶段解耦任务（按分支）

凡标注“须走 §8”的，需先由 main 拟契约修订草案、经用户批准后再动代码（K9、K3、K10、C7 的修订已于 2026-11-20 批准写入模块契约，开工时间由用户安排）；改动接口或 DTO 的后端分支完成后通知 Frontend 对话同步前端。

| 优先级 | 编号 | 任务 | 负责分支 | 前置 |
| --- | --- | --- | --- | --- |
| P0 | 安全 | ~~上传接口存储型 XSS~~ **已修复（5466cd6，2026-11-22 合入）**：仅放行 jpg/jpeg/png/webp，服务端生成文件名，响应带 nosniff | user | — |
| P0 | K9 | 管理员逾期处置选择封禁时转调 `UserService.ban(userId, adminId)`：① `ban` **已合入（77fd2db）**；② task 改调用——待做 | user + task | — |
| P0 | 隐私 | ~~取件码公开泄露~~ **已修复（7ccd170，2026-11-22 合入）** | task | — |
| P1 | K10 | chat 改读 `TaskChatView.hasOpenObligations`（`TaskReadService` 已合入），待 chat 第②步 | chat + task | 迁移步骤见模块契约 §9 |
| P1 | K3 | `ChatIntegrationService` 改标量参数：① 已合入；②④ chat、③ task 待做 | chat + task | 与 K10 同一迁移顺序 |
| P1 | K12 | ~~发布校验校区、广场默认状态、关键词含描述~~ **已修复（c13167c）** | task | — |
| P1 | K12 | ~~评价公开查询免登录~~ **已修复（117bd0c）** | user | — |
| P1 | Bug | ~~订单 taskType 恒为 SERVICE~~ **已修复（abffd26）** | task | — |
| P1 | K11 | ~~控制器自拼错误响应~~ **已修复**（chat 0066a26、user 5466cd6） | chat + user | — |
| P1 | K1 遗留 | `CategoryService` **已合入（644d3e2）**；待 admin 把分类增删改改为调用它 | admin | — |
| P2 | K4 | ~~OrderLifecycleService 全限定名直引仓库~~ **已修复（4f03f05）** | task | — |
| P2 | K5 | 同步机制已建立：后端 `OrderRules` 与前端副本均由单测固定（e803026）；规则变更须通知 Frontend | task + Frontend | 规则查询接口须走 §8 |
| 收尾 | K6、K8 | 已在 Frontend 分支修复（80f57aa），随 Frontend 合入 main 后关闭 | Frontend | 合并须用户批准 |
| — | K2 | notification→chat 同事务双写维持现状（设计决策），两域同属 chat 分支 | chat | — |
| — | K7 | 前端热点文件统一归 Frontend；后端 SecurityConfig 白名单仍需小步追加 | user | — |

### 4.2 高并发与大数据量测试

- **目标**：验证接单互斥、支付回调幂等、信用分行锁、通知双写事务边界在高并发下的正确性；大数据量下广场查询、订单分页、消息列表的性能。
- **前置**：K1 已修复（信用分审计对账依赖）；测试数据生成脚本由 main 协调（数据库全局件归 main）。
- **约定**：压测工具选型另议；脚本与数据一律放项目根目录内；测试库独立，不连开发/生产库。
- **死锁审查结论（2026-11-20）**：悲观写锁全部按“需求 → 订单 → 支付单 → 用户”顺序获取，支付、结算、评价只锁订单且不写需求行，通知与聊天联动只做插入；未发现加锁环路。新代码须遵守模块契约 §7 的锁顺序。
- **压测前须修复的并发隐患**：

| 编号 | 隐患 | 位置 | 负责分支 |
| --- | --- | --- | --- |
| C1 | 多线程并发调用同一 `WebSocketSession.sendMessage`（标准会话不支持并发发送，会抛 `IllegalStateException` 且未捕获）；改用 `ConcurrentWebSocketSessionDecorator` | `ChatWebSocketHandler.java:79,161,168` | chat |
| C2 | 断开连接时“移除会话、列表为空再删整组”非原子，同一用户另一标签页恰好连入时新会话随列表被丢弃，之后收不到推送；改用 `computeIfPresent` 原子处理 | `ChatWebSocketHandler.java:127-130` | chat |
| C3 | 系统通知会话“先查后建”无唯一约束，同一新用户同时收到两条通知时会建出两个“系统通知”会话；需加唯一约束（改 04 建表脚本并追加迁移，与实体同批合并） | `ConversationService.getOrCreateSystemNotify` | chat |
| C4 | 群聊清理定时任务在单个长事务里遍历全部会话（每条再查需求与订单），持有会话行锁期间，接单/取消事务会带着需求、订单锁排队等待；改为分页、逐会话短事务 | `GroupCleanupScheduler.cleanupExpiredGroups` | chat |
| C5 | 组队需求未满员时一直是 `published`，截止后被自动取消，但已加入成员的订单仍为进行中；自动取消需排除或级联处理有进行中订单的组队 | `TaskRepository.findExpiredTasks`、`TaskAutoCancelScheduler` | task |
| C6 | 行锁等待超时或死锁回滚（`PessimisticLockingFailureException`、`CannotAcquireLockException`）落入兜底处理返回 500；应映射为 409/503 并提示重试 | `common/GlobalExceptionHandler` | main |
| C7 | 全表加载后内存过滤：统计 3 处、“通知所有管理员”3 处、全体广播 1 处；改为计数查询与按角色查询（`UserRepository.findIdsByRole`、`countByStatus` 已批准写入模块契约 §3.4，user 先实现） | `AdminService.java:85,88,100,354`、`ComplaintService:65`、`OrderLifecycleService:168`、`TaskService:442` | admin + user + task |
| C8 | 定时任务没有分布式锁：单实例下靠行锁、条件更新和 eventKey 幂等保证正确，多实例部署时群聊清理会重复执行；随 §2.1 Redisson 落地处理 | 6 个 `@Scheduled` 任务 | redis |

### 4.3 工程协调

- `.github/workflows/ci.yml` 触发分支为 main/user/admin/task/chat/redis/Frontend（Frontend 于 2026-11-20 加入）；结构校验已拆至 sql-schema.yml 按 SQL 路径触发（2026-11-14 批准并完成）。
- 数据库结构变更（域内建表脚本归功能分支、全局件归 main）与对应 JPA 实体变更必须同批评审、同时合并；跨域结构改动走 AGENTS §6 通知协议。
- 前端由 Frontend 分支统一维护（2026-11-19 设立）；main 的文档与契约维护、全项目审查由 Frontend 对话兼任（2026-11-20 用户指定）。
- 本地 main 领先远程的提交（K1 修复、契约修订等）尚未推送，推送走 AGENTS §6 临时分支流程，须用户批准。
