运行后端前，请设置以下环境变量：

- `CAMPUSHUB_DB_PASSWORD`：数据库密码。
- `CAMPUSHUB_SMTP_USERNAME`：发送验证码的邮箱地址。
- `CAMPUSHUB_SMTP_PASSWORD`：邮箱 SMTP 授权码。
- `CAMPUSHUB_JWT_SECRET`：JWT 签名密钥，至少 64 字节。
- `CAMPUSHUB_AES_KEY`：AES 密钥，长度为 16、24 或 32 字节。

以下变量可选，不设置时使用默认值：

- `CAMPUSHUB_REDIS_HOST`、`CAMPUSHUB_REDIS_PORT`：Redis 地址，默认 `localhost:6379`。
- `CAMPUSHUB_REDIS_PASSWORD`：Redis 密码，默认为空，仅限本地开发。

Windows 没有原生 Redis，本地开发可在 WSL 中运行；WSL 发行版空闲后会自动停止，启动后端前需保持其处于运行状态。

测试配置使用独立的示例密钥。请在运行环境中配置实际凭据，不要将它们提交到仓库。
