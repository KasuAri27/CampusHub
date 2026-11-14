## 3.1 ER 图

### 3.1.1 核心实体识别

| 实体 | 说明 | 核心属性 |
|------|------|---------|
| **User** | 用户（含需求方、服务方、管理员三种角色） | id, name, email, phone, password_hash, role, credit_score, avatar, status |
| **Category** | 需求分类 | id, name, icon, has_review, sort_order |
| **Task** | 互助需求 | id, title, description, reward, status, location, deadline, published_at |
| **Order** | 订单 / 参与记录（用户与 Task 的连接） | id, status, accepted_at, completed_at, cancelled_at |
| **Review** | 评价 | id, score, comment, created_at |
| **Notification** | 消息通知 | id, type, content, is_read, created_at |

### 3.1.2 ER 图

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            CampusHub ER 图                               │
└──────────────────────────────────────────────────────────────────────────┘

                                             ┌──────────────────────┐
                                             │     Notification     │
                                             ├──────────────────────┤
                                             │ PK │ id: BIGINT      │
                                             │    │ receiver_id  ───┼──────────┐
                                             │    │ type: VARCHAR   │          │
                                             │    │ title: VARCHAR  │          │
                                             │    │ content: TEXT   │          │
                                             │    │ is_read: TINYINT│          │
                                             │    │ created_at      │          │
                                             └──────────────────────┘          │
                                                                               │ 接收
                                                                               │
                                             ┌──────────────────────┐          │
                 ┌───────────────────────────│        User          │◄─────────┘
                 │                           ├──────────────────────┤
                 │   ┌───────────────────────│ PK │ id: BIGINT      │
                 │   │                       │    │ name: VARCHAR   │
                 │   │                       │    │ email: VARCHAR  │─── UK
                 │   │                       │    │ phone: VARCHAR  │─── UK
                 │   │                       │    │ password_hash   │
                 │   │                       │    │ role: ENUM      │
                 │   │                       │    │ credit_score    │
                 │   │                       │    │ avatar: VARCHAR │
                 │   │                       │    │ status: TINYINT │
                 │   │                       │    │ created_at      │
                 │   │                       │    │ updated_at      │
                 │   │                       └──────────┬───────────┘
                 │   │                                  │
                 │   │        ┌─────────────────────────┼─────────────────────┐
                 │   │        │ 1                       │ 1                   │
                 │   │        │ 发布                    │ 接单/参与            │
                 │   │        │ N                       │ N                   │
                 │   │        ▼                         ▼                     │
                 │   │  ┌───────────────────┐  ┌──────────────────┐           │
                 │   │  │       Task        │  │      Order       │           │
                 │   │  ├───────────────────┤  ├──────────────────┤           │
                 │   │  │ PK │ id: BIGINT   │  │ PK │ id: BIGINT  │           │
                 │   │  │ FK │ requester_id─┼──│ FK │ task_id ────┤           │
                 │   │  │ FK │ category_id ─┼─┐│ FK │ requester   │           │
                 │   │  │    │ title        │  ││ FK │ provider    │          │
                 │   │  │    │ description  │  ││    │ status      │          │
                 │   │  │    │ reward       │  ││    │ accepted_at │          │
                 │   │  │    │ status       │  ││    │ completed   │          │
                 │   │  │    │ location     │  ││    │ cancelled   │          │
                 │   │  │    │ deadline     │  ││    │ created_at  │          │
                 │   │  │    │ published_at │  ││    │ updated_at  │          │
                 │   │  │    │ created_at   │  │└──────┬───────────┘          │
                 │   │  │    │ updated_at   │  │       │ 1                    │
                 │   │  └─────────┬─────────┘  │       │                      │
                 │   │            │            │       │ 0..1                 │
                 │   │            │ N          │       │                      │
                 │   │            │            │       ▼                      │
                 │   │  ┌─────────┴────────┐   │  ┌──────────────────┐        │
                 │   │  │    Category      │   │  │     Review       │        │
                 │   │  ├──────────────────┤   │  ├──────────────────┤        │
                 │   │  │ PK │ id: INT     │   │  │ PK │ id: BIGINT  │        │
                 │   │  │    │ name        │   │  │ FK │ order_id    │─── UK  │
                 │   │  │    │ icon        │   │  │    │ score: INT  │        │
                 │   │  │    │ has_review  │   │  │    │ comment     │        │
                 │   │  │    │ sort_order  │   │  │    │ created_at  │        │
                 │   │  └──────────────────┘   │  └──────────────────┘        │
                 │   │           ▲             │                              │
                 │   │           └─────────────┘                              │
                 │   │            分类 N:1                                    │
                 │   │                                                        │
```

### 3.1.3 实体关系说明

| 关系 | 类型 | 说明 |
|------|:---:|------|
| User → Task | **1 : N** | 一个需求方发布多个需求 |
| Category → Task | **1 : N** | 一个分类下有多个需求 |
| User → Order（需求方） | **1 : N** | 一个需求方产生多个订单 |
| User → Order（服务方） | **1 : N** | 一个服务方产生多条订单/参与记录 |
| Task → Order | **1 : N** | 一个 Task 可以有多个 Order（一人接单时 1:1；多人参与时 1:N） |
| Order → Review | **1 : 0..1** | 一个订单最多一条评价（是否可评由 Category.has_review 决定） |
| User → Notification | **1 : N** | 一个用户接收多条通知 |

### 3.1.4 主键与外键汇总

| 实体 | 主键 (PK) | 外键 (FK) |
|------|----------|----------|
| User | `id` | — |
| Category | `id` | — |
| Task | `id` | `requester_id` → User.id, `category_id` → Category.id |
| Order | `id` | `task_id` → Task.id, `requester_id` → User.id, `provider_id` → User.id |
| Review | `id` | `order_id` → Order.id |
| Notification | `id` | `receiver_id` → User.id |

---

## 3.2 建表 SQL

### 3.2.1 数据库与字符集

```sql
CREATE DATABASE IF NOT EXISTS campushub
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE campushub;
```

### 3.2.2 用户表 `users`

```sql
CREATE TABLE users (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    name            VARCHAR(20)     NOT NULL                 COMMENT '真实姓名',
    email           VARCHAR(100)    NOT NULL                 COMMENT '学校邮箱',
    phone           VARCHAR(255)    NOT NULL                 COMMENT '手机号（AES加密存储）',
    password_hash   VARCHAR(255)    NOT NULL                 COMMENT 'bcrypt哈希密码',
    role            ENUM('requester','provider','admin')
                                    NOT NULL                 COMMENT '用户角色',
    credit_score    DECIMAL(3,1)    NOT NULL DEFAULT 5.0     COMMENT '信用评分',
    avatar          VARCHAR(500)    DEFAULT NULL             COMMENT '头像URL',
    status          TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：1=正常, 0=禁用',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

### 3.2.3 需求分类表 `categories`

```sql
CREATE TABLE categories (
    id              INT             NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    name            VARCHAR(20)     NOT NULL                 COMMENT '分类名称',
    icon            VARCHAR(255)    DEFAULT NULL             COMMENT '分类图标URL',
    has_review      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否支持评价：1=支持, 0=不支持',
    sort_order      INT             NOT NULL DEFAULT 0       COMMENT '排序权重',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求分类表';

INSERT INTO categories (name, has_review, sort_order) VALUES
    ('快递代取', 1, 1),
    ('学习辅导', 1, 2),
    ('二手交易', 0, 3),
    ('组队匹配', 0, 4);
```


### 3.2.4 需求表 `tasks`

```sql
CREATE TABLE tasks (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '需求ID',
    requester_id    BIGINT          NOT NULL                 COMMENT '发布者ID',
    category_id     INT             NOT NULL                 COMMENT '分类ID',
    title           VARCHAR(50)     NOT NULL                 COMMENT '需求标题',
    description     VARCHAR(500)    NOT NULL                 COMMENT '详细描述',
    reward          DECIMAL(10,2)   DEFAULT NULL             COMMENT '报酬金额',
    status          ENUM('published','in_progress','completed','cancelled')
                                    NOT NULL DEFAULT 'published' COMMENT '需求状态',
    location        VARCHAR(100)    DEFAULT NULL             COMMENT '地点',
    deadline        DATETIME        DEFAULT NULL             COMMENT '截止时间',
    published_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_requester_id (requester_id),
    KEY idx_category_id (category_id),
    KEY idx_status (status),
    KEY idx_published_at (published_at),
    CONSTRAINT fk_task_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_task_category  FOREIGN KEY (category_id)  REFERENCES categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表';
```

### 3.2.5 订单表 `orders`

```sql
CREATE TABLE orders (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    task_id         BIGINT          NOT NULL                 COMMENT '关联需求ID',
    requester_id    BIGINT          NOT NULL                 COMMENT '需求方ID',
    provider_id     BIGINT          NOT NULL                 COMMENT '服务方/参与者ID',
    status          ENUM('accepted','in_progress','completed','cancelled')
                                    NOT NULL DEFAULT 'accepted' COMMENT '订单状态',
    accepted_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接单/参与时间',
    completed_at    DATETIME        DEFAULT NULL             COMMENT '完成时间',
    cancelled_at    DATETIME        DEFAULT NULL             COMMENT '取消时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_requester_id (requester_id),
    KEY idx_provider_id (provider_id),
    KEY idx_status (status),
    CONSTRAINT fk_order_task      FOREIGN KEY (task_id)      REFERENCES tasks(id),
    CONSTRAINT fk_order_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_order_provider  FOREIGN KEY (provider_id)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单/参与记录表';
```

### 3.2.6 评价表 `reviews`

```sql
CREATE TABLE reviews (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    order_id        BIGINT          NOT NULL                 COMMENT '关联订单ID',
    score           TINYINT         NOT NULL                 COMMENT '评分，1-5',
    comment         VARCHAR(200)    DEFAULT NULL             COMMENT '评价内容',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_score CHECK (score >= 1 AND score <= 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';
```

### 3.2.7 通知表 `notifications`

```sql
CREATE TABLE notifications (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '通知ID',
    receiver_id     BIGINT          NOT NULL                 COMMENT '接收者ID',
    type            VARCHAR(30)     NOT NULL                 COMMENT '通知类型',
    title           VARCHAR(100)    NOT NULL                 COMMENT '通知标题',
    content         TEXT            NOT NULL                 COMMENT '通知内容',
    is_read         TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否已读',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_receiver_read (receiver_id, is_read),
    KEY idx_created_at (created_at),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';
```

### 3.2.8 索引设计说明

| 表 | 索引 | 类型 | 设计理由 |
|------|------|------|---------|
| users | `uk_email`, `uk_phone` | 唯一索引 | 登录查找 + 保证唯一性 |
| tasks | `idx_requester_id` | 普通索引 | "我的发布"列表 |
| tasks | `idx_category_id` | 普通索引 | 首页分类筛选 |
| tasks | `idx_status` | 普通索引 | 按状态过滤 |
| tasks | `idx_published_at` | 普通索引 | 首页按时间排序 |
| orders | `idx_task_id` | 普通索引 | 查某个 Task 的所有订单/参与者 |
| orders | `idx_requester_id` | 普通索引 | 需求方查看"我的订单" |
| orders | `idx_provider_id` | 普通索引 | 服务方查看"我的接单/参与" |
| orders | `idx_status` | 普通索引 | 按订单状态筛选 |
| reviews | `uk_order_id` | 唯一索引 | 一个订单最多一条评价 |
| notifications | `idx_receiver_read` | 复合索引 | 查某用户的未读通知（高频） |
| notifications | `idx_created_at` | 普通索引 | 通知列表按时间排序 |

### 3.2.9 隐私数据处理

| 敏感字段 | 存储方式 | 说明 |
|---------|---------|------|
| **密码** | bcrypt 加盐哈希 | `BCryptPasswordEncoder`，cost factor = 10，永不明文存储 |
| **手机号** | AES-256 加密 | 数据库存密文，API 返回脱敏（`138****1234`），密钥由环境变量注入 |


