运行后端前，请设置以下环境变量：

- `CAMPUSHUB_DB_PASSWORD`：数据库密码。
- `CAMPUSHUB_SMTP_USERNAME`：发送验证码的邮箱地址。
- `CAMPUSHUB_SMTP_PASSWORD`：邮箱 SMTP 授权码。
- `CAMPUSHUB_JWT_SECRET`：JWT 签名密钥，至少 64 字节。
- `CAMPUSHUB_AES_KEY`：AES 密钥，长度为 16、24 或 32 字节。

测试配置使用独立的示例密钥。请在运行环境中配置实际凭据，不要将它们提交到仓库。
