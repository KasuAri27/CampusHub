# 贡献规范

## 分支策略

| 分支 | 职责范围 |
|---|---|
| `main` | 主干：整体文档编写、横切共享代码（common、config 基础、util 除外见下）、数据库全局件（建库/迁移登记/结构指纹/种子/断言清单）；所有合并的目标 |
| `redis` | 缓存扩展：Redis 缓存与分布式锁落地（见 TODO §2.1） |
| `user` | 用户页面、登录注册、用户权限：后端 user/verification/file/security/util 与 SecurityConfig，前端 user/、views/、userStore |
| `admin` | 管理员相关功能：admin 前后端 |
| `task` | 核心业务：发布需求、接单、交易、组队等（task/order/payment/review/complaint/category 前后端，高度耦合不再细分） |
| `chat` | 通知与 WebSocket 对话系统：chat/notification 前后端 |

- **未经许可不许合并**：任何分支合并（含合入 main）必须经用户明确批准；不要直接把未经检查的改动合入主分支。
- **文件位置纪律**：所有分支产生的文件（代码、文档、脚本、测试数据）一律放在项目根目录之内，不得延伸到项目外。
- 模块边界、冻结接口与共享文件所有权见 [模块契约](docs/Engineer/模块契约.md)；跨分支只能调用冻结接口。

## 提交信息

沿用 Conventional Commits 格式：`<type>(可选范围)[!]: <简短描述>`。描述部分不超过15个字符，提交信息只写一行，不写副标题或正文；一次提交聚焦一项改动。

类型：`feat` 功能、`fix` 修复、`docs` 文档、`refactor` 重构、`test` 测试、`chore` 工程维护。破坏性变更用 `!`，详细影响写在 PR 描述中。

示例：`feat(payment): 接入真实支付`、`fix(order): 修复重复扣分`。

## 开发与本地检查

- 后端使用 Java 17、Maven 3.9；前端使用 Node 22及npm，保留唯一的 `package-lock.json`，不要混用npm与pnpm锁文件。
- 后端：在 `backend` 执行 `mvn -B -ntp clean verify`。
- 前端：在 `frontend` 执行 `npm ci`、`npm run check`、`npm test`、`npm run build`。
- 自动测试使用独立测试库，不连接日常开发或生产数据库。测试的清理代码只接受 `campushub_it`、`campushub_ci` 命名的数据库。
- 编译产物、依赖、私钥、证书、环境文件和结算凭证不进入Git。实际工具安装优先放在本机 `D:/Dev`。

## 业务与代码审查

现行支付、验收、逾期、评价、信用和人工结算规则见 [后端交易说明](backend/PAYMENTS.md)。P0–P4文档保留设计演进，不以旧示例覆盖现行规则。

新增和修改的业务代码需要注释说明业务理由、状态前置条件、身份来源、金额来源、事务及重试策略；不要只把代码逐句翻译成注释。支付SDK协议、敏感数据和外部请求尤其需要清楚标明信任边界。

改动规则时，同一PR更新实现、测试和现行说明。保持真实失败：不能删除有效断言、跳过失败用例、伪造网络成功或以Mock成功冒充真实渠道联调。

PR描述说明：问题和结果、涉及的信息流和状态迁移、验证命令与结果、数据迁移及尚未验证的外部环节。涉及支付、退款、结算、权限和处罚的改动须人工审查。

## GitHub持续集成

`.github/workflows/ci.yml` 在 `main`、`user`、`admin`、`task`、`chat`、`redis` 推送及所有PR上执行业务测试与前端检查。数据库结构校验拆至 `.github/workflows/sql-schema.yml`，仅在 `backend/SQL/` 及相关结构测试资源变动时触发，避免业务分支重构或升级时被结构断言连带失败。

检查项：`backend-h2`、`backend-mysql`、`frontend`（全分支触发），以及 `sql-schema`（结构校验，仅 SQL 路径触发）。后端运行全部测试、打包及核心包60%行覆盖率门槛；前端的全文件检查、单元测试和生产构建分步执行，前一项失败不影响后面的检查运行。失败时仍保存Surefire与JaCoCo报告。

- **用例必须跑全**：流水线不允许出现被跳过的用例。后端按Surefire报告核对，前端按测试汇总核对，跳过数不为0即失败。`main` 上的运行不会被后续提交取消。
- **Redis检测**：两个后端任务都自带Redis服务，`RedisConnectivityTest` 用应用自身的连接配置做PING与读写往返。本机默认跳过该用例；已启动Redis时设置 `CAMPUSHUB_TEST_REDIS_REQUIRED=true` 可一并执行。
- **建库校验的客户端字符集**：容器内的mysql客户端必须显式使用utf8mb4，否则中文数据会被误读并触发长度约束。

`main` 禁止直接推送：仓库规则集 `main-protection`（定义见 `.github/rulesets/main-protection.json`）要求所有改动经PR合入、以上三个检查全部通过，并禁止强制推送和删除分支，管理员同样不可绕过。规则集当前不强制审批人数；涉及支付、退款、结算、权限和处罚的改动仍须人工审查。

工作流文件不能自行修改仓库的分支保护设置，规则集的变更由仓库管理员按上述文件在GitHub上更新；本地编写和静态验证也不能当成远端CI已运行。

## 商户与真实资金

真实商户配置通过部署环境提供，参照 `backend/.env.example`。不要在Issue、PR、日志或聊天里发送私钥、APIv3密钥、支付认证信息或原始财务凭证。

本版采用真实收款和人工结算登记。CI及普通开发检查不发起真实支付、退款或转账；真实联调需要另行指定资金范围和参与账户，并记录渠道结果。
