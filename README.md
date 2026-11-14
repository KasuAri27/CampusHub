# CampusHub

校园互助与二手交易平台，SEC-II-2026 课程项目。

- 前端 `frontend/`：Vue 3 + Vite + Element Plus + Pinia + Vue Router
- 后端 `backend/`：Java 17 + Spring Boot 3.4 + Spring Data JPA + MySQL 8.0

## 文档索引

| 位置 | 内容 |
|------|------|
| [docs/Engineer/架构文档.md](docs/Engineer/架构文档.md) | 现行架构、模块依赖、信息流、已知问题 |
| [docs/Engineer/模块契约.md](docs/Engineer/模块契约.md) | 模块边界、冻结接口、分支所有权、数据库契约 |
| [docs/Course/](docs/Course/) | 课程 P0–P4 交付物历史存档 |
| [AGENTS.md](AGENTS.md) | 开发纪律与约束 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 分支策略、提交规范、本地检查 |
| [TODO.md](TODO.md) | 已批准的改进方向 |
| [backend/PAYMENTS.md](backend/PAYMENTS.md) | 交易、支付与结算现行规则 |

## 快速开始

- 后端：按 [backend/README.md](backend/README.md) 配置环境变量后启动 Spring Boot（端口 8080）。
- 前端：`npm ci` 后 `npm run dev`（端口 5173，`/api` 代理到后端）。