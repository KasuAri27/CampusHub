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
| 500 | Internal Server Error | 服务器内部异常 |

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
  | studentId | String | 是 | 学号（唯一） |
  | name | String | 是 | 真实姓名（AES-256-GCM 加密存储） |
  | phone | String | 是 | 手机号（AES-256-GCM 加密存储） |
  | code | String | 是 | 短信/邮箱验证码 |
  | password | String | 是 | 登录密码（BCrypt 加密） |
  | nickname | String | 是 | 昵称 |
  | college | String | 否 | 学院院系 |
- **成功响应**：返回 `LoginResponse`，包含 `token`、`role`、`user` 基本信息。

### 2.3 账号密码登录
- **URL**：`/v1/auth/login/password`
- **Method**：`POST`
- **认证**：否
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | account | String | 是 | 学号或 11 位手机号 |
  | password | String | 是 | 登录密码 |
- **成功响应**：返回 `token`、`role`、`user`。

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
- **切换角色**：`PUT /v1/auth/role?role=requester|provider`

---

## 3. 需求任务模块 (`/v1/tasks`)

### 3.1 发布需求
- **URL**：`/v1/tasks`
- **Method**：`POST`
- **认证**：是（信用分需 ≥ 70）
- **请求参数**：
  | 字段名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | categoryId | Integer | 是 | 需求分类 ID |
  | title | String | 是 | 需求标题（≤30字） |
  | description | String | 是 | 需求详细描述 |
  | reward | BigDecimal | 否 | 报酬金额（组队招募固定为 0） |
  | campus | String | 是 | 校区（鼓楼 / 仙林 / 浦口 / 苏州） |
  | location | String | 否 | 线下地点或送达地址 |
  | deadline | String | 是 | 截止时间（ISO 8601 格式，如 `2026-05-01T12:00:00`） |
- **成功响应**：返回生成的 `TaskDTO`，状态为 `pending_review`。

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
  | sort | String | 否 | 排序规则：`latest`（最新发布）、`reward_desc`（报酬最高）、`deadline_asc`（即将截止） |
  | page | Integer | 否 | 页码（从 1 开始，默认 1） |
  | page_size | Integer | 否 | 每页数量（默认 20） |
- **成功响应**：返回 `PageResult<TaskDTO>`。

### 3.3 需求详情
- **URL**：`/v1/tasks/{id}`
- **Method**：`GET`
- **认证**：否

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
- **认证**：是（信用分需 ≥ 70，不能接自己发布的需求）
- **Query 参数**：
  | 参数名 | 类型 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | taskId | Long | 是 | 待承接的需求 ID |
- **机制与成功响应**：后端通过数据库排他行锁抢接需求，成功后原需求置为 `in_progress`，生成 `OrderDTO`（状态为 `in_progress`）。若已被他人接单则抛出 409 冲突。

### 4.2 订单详情与历史
- **获取订单详情**：`GET /v1/orders/{orderId}`（仅订单参与双方或管理员可见）
- **根据任务查订单**：`GET /v1/orders/task/{taskId}`
- **我参与的所有订单**：`GET /v1/orders/mine`（同时包含作为需求方与服务方的订单）
- **我承接的订单**：`GET /v1/orders/my-accepted?status=...`

### 4.3 服务方提交完工
- **URL**：`/v1/orders/{orderId}/submit-completion`
- **Method**：`POST`
- **认证**：是（仅限服务承接方）
- **说明**：服务方完成履约后提交，订单状态流转为待验收 `awaiting_acceptance`，启动 24 小时自动验收计时。

### 4.4 需求方确认完成 / 验收
- **URL**：`/v1/orders/{orderId}/complete`
- **Method**：`POST`
- **认证**：是（仅限需求发布方）
- **说明**：需求方主动确认验收，订单状态置为 `completed`，生成待支付状态（`unpaid`，有偿订单启动 1 小时正常付款倒计时）。

### 4.5 取消订单
- **URL**：`/v1/orders/{orderId}/cancel`
- **Method**：`POST`
- **认证**：是（仅订单双方在进行中且无付款无争议时允许取消）

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
- **认证**：是（仅限待付款的需求方调用）
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
      "attemptId": 1001,
      "orderId": 501,
      "channel": "WECHAT",
      "qrCode": "weixin://wxpay/bizpayurl?pr=...",
      "amount": 25.00,
      "status": "PENDING",
      "expireAt": "2026-05-01T15:30:00"
    }
  }
  ```

### 5.3 查询订单支付状态
- **URL**：`/v1/payments/orders/{orderId}`
- **Method**：`GET`
- **认证**：是（订单参与双方或管理员）

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
- **待结算订单列表**：`GET /v1/admin/finance/orders?page=1`
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
  - 分页检索：`GET /v1/admin/users?page=1&size=20&keyword=...`
  - 封禁/解冻：`PUT /v1/admin/users/{id}/status`
  - 手动调整信用分：`PUT /v1/admin/users/{id}/credit`（Body: `{ "delta": -5 }`）
- **需求审批与驳回**：
  - 待审需求分页：`GET /v1/admin/tasks?status=pending_review`
  - 审核通过：`PUT /v1/admin/tasks/{id}/approve`
  - 审核驳回：`PUT /v1/admin/tasks/{id}/reject`（Body: `{ "reason": "信息不合规" }`）
- **分类维护**：`GET /v1/admin/categories`, `POST /v1/admin/categories`, `PUT /v1/admin/categories/{id}`, `DELETE /v1/admin/categories/{id}`
- **系统广播推送**：`POST /v1/admin/notifications`
