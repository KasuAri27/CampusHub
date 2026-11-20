# 校园互助服务平台 API 规范文档

## 1. 通用规范

### 1.1 基础信息
- **Base URL**：`/v1`
- **字符编码**：`UTF-8`
- **时区基准**：`Asia/Shanghai`
- **认证方式**：在 HTTP 请求头中携带 JWT 令牌：
  ```http
  Authorization: Bearer <token>
  ```

### 1.2 通用响应结构

#### 成功响应：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```
或对于无数据返回的操作：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

#### 失败响应：
```json
{
  "code": 400,
  "message": "业务错误或参数异常描述",
  "error": "详细异常追踪信息"
}
```

### 1.3 核心状态码表

| 状态码 | 语义说明 | 典型场景 |
| --- | --- | --- |
| 200 | OK | 请求处理成功 |
| 201 | Created | 资源创建成功（如需求发布） |
| 400 | Bad Request | 业务规则校验失败、参数不合法 |
| 401 | Unauthorized | Token 缺失、无效或已过期 |
| 403 | Forbidden | 越权访问（如非本人或非管理员操作、信用分不足） |
| 404 | Not Found | 请求的资源不存在 |
| 409 | Conflict | 并发状态冲突（如需求已被他人抢接、重复评价） |
| 422 | Unprocessable Entity | 参数 Bean Validation 校验不通过 |
| 429 | Too Many Requests | 请求过于频繁（如验证码重复发送） |
| 500 | Internal Server Error | 服务器内部异常 |
| 502 / 503 | Bad Gateway / Service Unavailable | 外部渠道（支付、邮件）调用失败或暂不可用 |

> 唯一约束或并发冲突（数据库层）统一返回 409，提示刷新后核对；不要把 409 当作操作成功。

---

## 2. 用户与认证模块 (`/v1/auth`, `/v1/verification`)

### 2.1 发送验证码
- **URL**：`/v1/verification/send`
- **Method**：`POST`
- **认证**：否
- **请求参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | phone | String | 是 | 11 位中国大陆手机号 |
  | type | String | 是 | `REGISTER`（注册）或 `LOGIN`（登录） |
  | studentId | String | 否 | 学号（注册时必填，校验唯一性） |
- **响应**：`{ "code": 200, "message": "验证码已发送", "data": null }`

### 2.2 用户注册
- **URL**：`/v1/auth/register`
- **Method**：`POST`
- **认证**：否
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | studentId | String | 是 | 学号（唯一，注册后不可更改） |
  | name | String | 是 | 真实姓名（AES-256-GCM 加密存储，注册后不可更改） |
  | phone | String | 是 | 手机号（AES-256 ECB 模式加密存储：同一手机号密文固定，用于唯一约束和登录时按密文查找） |
  | code | String | 是 | 短信/邮箱验证码 |
  | password | String | 是 | 登录密码（BCrypt 加密） |
  | nickname | String | 是 | 昵称 |
  | college | String | 否 | 学院院系 |
- **成功响应**：返回 `LoginResponse`，包含 `token`、`role`、`user` 基本信息。`role` 取值为 `user`（普通用户）或 `admin`（管理员）。

### 2.3 账号密码登录
- **URL**：`/v1/auth/login/password`
- **Method**：`POST`
- **认证**：否
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | account | String | 是 | 学号或 11 位手机号 |
  | password | String | 是 | 登录密码 |
- **成功响应**：返回 `token`、`role`、`user`，`role` 取值同 2.2。

### 2.4 手机验证码登录
- **URL**：`/v1/auth/login/sms`
- **Method**：`POST`
- **认证**：否
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | phone | String | 是 | 手机号 |
  | code | String | 是 | 验证码 |

### 2.5 获取与修改个人资料
- **获取资料**：`GET /v1/auth/profile`（需要认证）
- **修改资料**：`PUT /v1/auth/profile`（需要认证，支持修改昵称、头像、学院等）
- **修改密码**：`PUT /v1/auth/password`（需要认证，包含 `oldPassword` 和 `newPassword`）
- **不可更改字段**（2026-11 修订）：学号、真实姓名、邮箱在注册时确定，之后不可更改；修改资料的请求即使携带这些字段也不采纳。管理员不登记真实姓名，姓名固定为“管理员”。
- **角色说明**（2026-11 修订）：原“切换角色”接口 `PUT /v1/auth/role` 已删除。普通用户不再区分需求方/服务方，同一账号既能发布需求也能接单（不能接自己发布的需求）；管理员只负责审核与监管，不发布、不接单。

---

## 3. 需求任务模块 (`/v1/tasks`)

### 3.1 发布需求
- **URL**：`/v1/tasks`
- **Method**：`POST`
- **认证**：是（普通用户且信用分需 ≥ 70；管理员不可发布）
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | categoryId | Integer | 是 | 需求分类 ID（发布类型与分类的对应关系见下） |
  | taskType | String | 是 | 需求类型：`RUNNING` 跑腿、`TUTORING` 辅导、`FLEA` 二手、`TEAM` 组队、`CONSULTING` 咨询 |
  | title | String | 是 | 需求标题（5–50 字） |
  | description | String | 是 | 需求详细描述（10–500 字） |
  | reward | BigDecimal | 否 | 报酬金额（≥0）。组队不填，库内为空、订单金额为 0；咨询可为 0 表示免费 |
  | campus | String | 是 | 校区（鼓楼校区 / 仙林校区 / 浦口校区 / 苏州校区） |
  | location | String | 否 | 线下地点或送达地址（组队活动地点、线下咨询地点） |
  | deadline | String | 是 | 截止时间（ISO 8601，不带时区，按 Asia/Shanghai 解释，须晚于当前时间） |
- **类型专属字段**（2026-11 对齐现行实现）：
  | 类型 | 字段 | 说明 |
  | --- | --- | --- |
  | RUNNING | `pickupLocation`、`deliveryLocation`（必填），`pickupCode`（可选），`expressReward`（必填，作为报酬） | 跑腿取件/送达地点与报酬 |
  | TUTORING | `subject`、`pricePerHour`、`durationHours`（0.01–1000，最多两位小数）、`tutoringLocation` | 总价 = 时薪 × 时长，按分四舍五入，接单时冻结为订单金额 |
  | FLEA | `tradeMode`（`SELL` 出售 / `BUY` 求购）、`itemPrice`（必填，≥0） | 出售：接单人付款给发布者；求购：发布者付款给接单人 |
  | TEAM | `maxMembers`（人数上限，含发起人） | 无偿招募，审核通过后自动建群 |
  | CONSULTING | `consultingMode`（`ONLINE` / `OFFLINE`） | 线下时可填 `location` |
- **类型与分类对应**：当前发布入口固定使用分类 1–5 分别对应 RUNNING、TUTORING、FLEA、TEAM、CONSULTING（种子数据）。
- **成功响应**：HTTP 201，返回生成的 `TaskDTO`，状态为 `pending_review`，并通知管理员审核。

### 3.2 广场需求列表多维检索
- **URL**：`/v1/tasks`
- **Method**：`GET`
- **认证**：否（公开广场）
- **Query 参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | categoryId | Integer | 否 | 分类过滤 |
  | status | String | 否 | 任务状态（默认查询 `published`） |
  | keyword | String | 否 | 标题与描述模糊搜索 |
  | campus | String | 否 | 校区过滤 |
  | sort | String | 否 | 排序规则：`latest`（最新发布）、`reward`（报酬最高，2026-11 由 `reward_desc` 改为与实现一致）、`deadline_asc`（即将截止） |
  | page | Integer | 否 | 页码（从 1 开始，默认 1） |
  | page_size | Integer | 否 | 每页数量（默认 20） |
- **成功响应**：返回 `PageResult<TaskDTO>`。

### 3.3 需求详情
- **URL**：`/v1/tasks/{id}`
- **Method**：`GET`
- **认证**：否
- **成功响应**：`TaskDTO`，含基础字段、`publisher`（`userId`、`nickname`、`avatar`、`creditScore`）、3.1 所列类型专属字段，组队另含 `chatGroupId`（群聊会话 ID）。

### 3.4 我发布的需求
- **URL**：`/v1/tasks/mine/published`
- **Method**：`GET`
- **认证**：是
- **Query 参数**：`page`, `page_size`

### 3.5 删除/撤回需求
- **URL**：`/v1/tasks/{id}`
- **Method**：`DELETE`
- **认证**：是（仅限发布者本人；若已产生订单记录则软取消以保留审计，无历史订单则物理移除）

---

## 4. 订单履约模块 (`/v1/orders`)

### 4.1 承接需求（接单）
- **URL**：`/v1/orders/accept`
- **Method**：`POST`
- **认证**：是（普通用户且信用分需 ≥ 70，不能接自己发布的需求；管理员不可接单）
- **Query 参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | taskId | Long | 是 | 待承接的需求 ID |
- **机制与成功响应**：后端先对需求加排他行锁再生成订单，返回 `OrderDTO`（状态为 `in_progress`）。普通需求接单后置为 `in_progress`；组队需求每加入一人生成一张订单，满员（含发起人）后才置为 `in_progress`。已截止、已被接走或本人已参与时返回 400/409。
- **金额与付款方向**：接单时冻结 `amountDue`，并确定 `payerId`（付款人，同时是验收方）与 `payeeId`（收款人，同时是提交完成的一方）。一般由发布者付款；二手出售由接单人（买家）付款给发布者；组队与免费需求 `paymentStatus` 为 `not_required`。

### 4.2 订单详情与历史
- **获取订单详情**：`GET /v1/orders/{orderId}`（仅订单参与双方或管理员可见）
- **根据任务查订单**：`GET /v1/orders/task/{taskId}`
- **我参与的所有订单**：`GET /v1/orders/mine`（同时包含我作为发布者与接单人的订单）
- **我承接的订单**：`GET /v1/orders/my-accepted?status=...`
- **OrderDTO 主要字段**：`orderId`、`taskId`、`title`、`orderStatus`（`in_progress`/`awaiting_acceptance`/`completed`/`cancelled`）、`paymentStatus`（`not_required`/`unpaid`/`paid`）、`settlementStatus`（`not_required`/`pending`/`recorded`）、`requesterId`/`providerId`（发布者/接单人）、`payerId`/`payeeId`、`amountDue`，以及 `acceptanceDueAt`、`paymentDueAt`、`escalationDueAt`、`reviewEligibleAt` 等期限字段和 `disputed`、`hasReview`。`taskType` 只区分 `TEAM`、`FLEA`、`SERVICE`。履约、付款、结算三态分离，`completed` 不代表已付款。

### 4.3 服务方提交完工
- **URL**：`/v1/orders/{orderId}/submit-completion`
- **Method**：`POST`
- **认证**：是（仅限订单收款方 `payeeId`；二手出售时为发布者）
- **说明**：收款方完成履约后提交，订单状态流转为待验收 `awaiting_acceptance`，启动 24 小时自动验收计时。

### 4.4 需求方确认完成 / 验收
- **URL**：`/v1/orders/{orderId}/complete`
- **Method**：`POST`
- **认证**：是（仅限订单付款方 `payerId`，即验收方；二手出售时为接单人）
- **说明**：付款方主动确认验收（或提交后 24 小时无异议自动验收），订单状态置为 `completed`；有偿订单进入 `unpaid` 并启动 1 小时付款期，逾期一次性扣 10 分，再过 24 小时未付款转管理员处置。

### 4.5 取消订单
- **URL**：`/v1/orders/{orderId}/cancel`
- **Method**：`POST`
- **认证**：是（仅订单双方在进行中且无争议时允许取消）
- **说明**：接单人退出且需求未过截止时间时，需求回到 `published` 重新开放接单；发布者取消组队需求时级联取消所有进行中的成员订单（存在待验收或争议成员时返回 409）。

---

## 5. 真实支付与线下人工结算模块 (`/v1/payments`, `/v1/admin/finance`)

### 5.1 获取支持的支付渠道
- **URL**：`/v1/payments/channels`
- **Method**：`GET`
- **认证**：是
- **成功响应**：返回平台已签约并启用的渠道列表：`["WECHAT", "ALIPAY"]`。

### 5.2 创建支付订单并获取二维码
- **URL**：`/v1/payments/orders/{orderId}`
- **Method**：`POST`
- **认证**：是（仅限订单付款方 `payerId` 调用，订单须已验收且待付款）
- **Body 参数**：
  ```json
  {
    "channel": "WECHAT"
  }
  ```
- **成功响应**：
  ```json
  {
    "code": 200,
    "message": "success",
    "data": {
      "orderId": 501,
      "outTradeNo": "...",
      "channel": "WECHAT",
      "amount": 25.00,
      "codeUrl": "weixin://wxpay/bizpayurl?pr=...",
      "status": "PENDING"
    }
  }
  ```

- **说明**：`codeUrl` 为渠道收款码内容，前端在本地生成二维码；同一订单同时只有一张有效支付单，仍可支付时不能换渠道重开，渠道确认关闭后才可重新下单。

### 5.3 查询订单支付状态
- **URL**：`/v1/payments/orders/{orderId}`
- **Method**：`GET`
- **认证**：是（订单参与双方或管理员）
- **成功响应**：与 5.2 相同结构；`status` 为 `PENDING` / `PAID` / `CLOSED`，未确认付款时会先向渠道查单补偿。

### 5.4 微信 Native 异步通知回调
- **URL**：`/v1/payments/notify/wechat`
- **Method**：`POST`
- **认证**：否（微信官方商户私钥签名验签与 AES-GCM 解密）
- **响应**：`204 No Content`

### 5.5 支付宝当面付异步通知回调
- **URL**：`/v1/payments/notify/alipay`
- **Method**：`POST`
- **认证**：否（支付宝 RSA2 验签）
- **响应**：文本 `"success"`

### 5.6 管理员线下结算登记与凭证管理 (`/v1/admin/finance`)
- **订单列表**：`GET /v1/admin/finance/orders?page=1`（全部订单按 ID 倒序，每页固定 50 笔，返回 `OrderDTO` 数组，不含总数）
- **争议裁决**：`POST /v1/admin/finance/orders/{id}/resolve`，Body：`{ "amount": 20.00, "paymentHours": 24, "reason": "..." }`。裁定应付金额（0 至原金额，最多两位小数）与付款期限（1–168 小时）；已签发待支付或已付款的支付单时不能改金额，须先在渠道关单或退款核对。
- **逾期处置**：`POST /v1/admin/finance/orders/{id}/penalty`，Body：`{ "operationId": "op-1", "deduction": 5, "ban": false, "reason": "..." }`。在自动扣 10 分之外追加扣分或封禁付款人；`operationId` 规则同第 9 节调分，重试不重复处罚。
- **核对历史支付单**：`GET /v1/admin/finance/orders/{id}/payments`
- **登记人工打款凭证**：
  - **URL**：`POST /v1/admin/finance/orders/{id}/settlement`
  - **Content-Type**：`multipart/form-data`
  - **参数**：`amount`（结算金额）, `reference`（转账银行/流水单号）, `evidence`（凭证文件，PNG/JPG/PDF）
  - **成功响应**：返回结算记录 ID，订单结算状态更新为 `recorded`。
- **查看与下载私有结算凭证**：`GET /v1/admin/finance/orders/{id}/evidence`

---

## 6. 单向评价模块 (`/v1/reviews`)

### 6.1 提交服务评价
- **URL**：`/v1/reviews/submit`
- **Method**：`POST`
- **认证**：是（仅限需求验收方，订单状态为 completed 且完成付款后 7 天内）
- **Query / Form 参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | orderId | Long | 是 | 关联的已完工订单 ID |
  | score | Integer | 是 | 评分（1-5 整数） |
  | comment | String | 否 | 纯文本评语（≤200 字，不支持图片） |
- **成功响应**：`{ "code": 200, "message": "success", "data": null }`
- **信用分影响**：5星 +1分，1-2星 -1分，3-4星不变。完工 7 天未评系统调度自动默认 4 星（不加分）。

### 6.2 查询订单评价
- **URL**：`/v1/reviews/order/{orderId}`
- **Method**：`GET`
- **认证**：否（公开展示）

---

## 7. 即时通讯与聊天模块 (`/v1/conversations`)

### 7.1 获取会话列表
- **URL**：`/v1/conversations`
- **Method**：`GET`
- **认证**：是
- **成功响应**：返回用户参与的私聊、群聊及系统通知会话列表（含最新消息与未读数）。

### 7.2 查询会话历史消息
- **URL**：`/v1/conversations/{id}/messages`
- **Method**：`GET`
- **认证**：是（需具备会话参与权限）
- **Query 参数**：`page`（默认 1）, `size`（默认 30）

### 7.2.1 会话详情与发送消息
- **会话详情**：`GET /v1/conversations/{id}`
- **发送消息**：`POST /v1/conversations/{id}/messages`，Body：`{ "content": "...", "msgType": "TEXT" }`；`msgType` 为 `TEXT` 或 `IMAGE`（图片时 `content` 为上传接口返回的地址）。发送后经 WebSocket 推送给会话成员（见第 12 节）。

### 7.3 标记会话已读
- **URL**：`/v1/conversations/{id}/read`
- **Method**：`PUT`
- **认证**：是

### 7.4 会话生命周期操作
- **保留/归档组队会话**：`POST /v1/conversations/{id}/keep`
- **解散/清理会话**：`DELETE /v1/conversations/{id}`

---

## 8. 站内通知模块 (`/v1/notifications`)

### 8.1 通知列表分页查询
- **URL**：`/v1/notifications`
- **Method**：`GET`
- **认证**：是
- **Query 参数**：`pageNum`（默认 1）, `pageSize`（默认 10）, `unreadOnly`（布尔值，可选）

### 8.2 标记已读
- **单条标记已读**：`PUT /v1/notifications/{id}/read`
- **一键全部已读**：`PUT /v1/notifications/read-all`
- **未读总数角标查询**：`GET /v1/notifications/unread-count`

---

## 9. 管理员后台运营模块 (`/v1/admin`)

- **平台运营仪表盘**：`GET /v1/admin/stats`
- **用户管理与封禁**：
  - 分页检索：`GET /v1/admin/users?page=1&page_size=20&keyword=...&role=user`（keyword 匹配学号、邮箱、姓名；role 取 `user` / `admin`）
  - 用户详情：`GET /v1/admin/users/{id}`
  - 封禁/解冻：`PUT /v1/admin/users/{id}/status`（切换正常 1 / 封禁 0；封禁后为受限登录，只能处理本人订单、付款、投诉与账号安全）
  - 手动调整信用分：`PUT /v1/admin/users/{id}/credit`（Body: `{ "delta": -5, "operationId": "op-123" }`）
    - `operationId` 可选，字母/数字/下划线/连字符，≤36 字符；同一编号重复提交只生效一次且不重复通知，同编号但参数不一致返回 409；缺省时服务端生成，每次调用独立生效。
    - 信用分已达 100 时继续上调不再返回 400，按 [0,100] 钳制后记录流水。
- **需求审批与驳回**：
  - 需求分页：`GET /v1/admin/tasks?page=1&page_size=20&status=pending_review&keyword=...`（keyword 匹配标题；`taskType` 只区分 `TEAM`、`FLEA`、`SERVICE`）
  - 删除需求：`DELETE /v1/admin/tasks/{id}`（已有有效付款或争议订单的需求不能删除）
  - 审核通过：`PUT /v1/admin/tasks/{id}/approve`
  - 审核驳回：`PUT /v1/admin/tasks/{id}/reject`（Body: `{ "reason": "信息不合规" }`）
- **分类维护**：`GET /v1/admin/categories`, `POST /v1/admin/categories`, `PUT /v1/admin/categories/{id}`, `DELETE /v1/admin/categories/{id}`
- **系统通知**：
  - 通知记录：`GET /v1/admin/notifications?page=1&page_size=20`（全平台通知，含系统自动发出的）
  - 发送：`POST /v1/admin/notifications`，Body：`{ "receiverId": 1, "title": "...", "content": "...", "type": "SYSTEM" }`；`receiverId` 为空时向全部账号广播
- **会话监管**（只读）：`GET /v1/admin/conversations?page=1&page_size=20&convType=TEAM_GROUP`，`GET /v1/admin/conversations/{id}/messages?page=1&page_size=50`（含已撤回消息原文）
- **仪表盘字段**：`totalUsers`、`bannedUsers`、`totalTasks`、`publishedTasks`、`inProgressTasks`、`completedTasks`、`totalOrders`（历史命名，实际为已取消需求数）、`todayNewTasks`、`totalConversations`、`totalMessages`、`todaysMessages`

---

## 10. 订单投诉与争议 (`/v1/complaints`)（2026-11 补录现行接口）

- **提交投诉**：`POST /v1/complaints`，Body：`{ "orderId": 9, "reason": "服务未完成", "description": "...", "imageUrl": "/api/uploads/..." }`
  - 认证：订单双方；被投诉人由服务端按订单另一方确定，传入的 `targetId` 必须与之一致；同一订单同一人只能投诉一次（重复返回 409）。
  - 付款方以“服务未完成”或“质量不符”投诉时，订单置为争议中，暂停自动验收、扣分与升级计时，等待管理员在 5.6 裁决；收款方的催款投诉不暂停付款追缴。
- **按订单查询**：`GET /v1/complaints/order/{orderId}`；**投诉详情**：`GET /v1/complaints/{id}`（订单当事人与管理员可见）。

## 11. 文件上传 (`/v1/files`)（2026-11 补录现行接口）

- **上传**：`POST /v1/files/upload`，`multipart/form-data`，字段 `file`（单个文件不超过 5MB）。
- **成功响应**：`{ "url": "/api/uploads/<随机文件名>" }`。返回相对路径，前端经同源代理访问；部署时须保证 `/api/uploads/**` 映射到后端静态资源。
- 结算凭证不走本接口，见 5.6（私有存储、仅管理员可下载）。

## 12. 实时消息 WebSocket（2026-11 补录现行接口）

- **地址**：`/api/ws/chat?token=<JWT>`（浏览器 WebSocket 不能自定义请求头，令牌放在查询参数；仅状态正常的账号可连接）。
- **消息类型**：客户端发送 `PING`（心跳，约 30 秒一次）、`SEND`；服务端推送 `PONG`、`MSG`（新消息）、`RECALL`（撤回）、`ERROR`。载荷形如 `{ "type": "MSG", "convId": 1, "message": { ...MessageDTO } }`。
