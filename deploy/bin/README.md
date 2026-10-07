# deploy/bin — nitrowater 运维/调试脚本

> 参考 waterfun `deploy/` 迁移而来；**docker 部署部分暂不引入**。

所有脚本位于 `deploy/bin/`，在**仓库根目录**执行。

## 脚本清单

| 脚本 | 用途 |
|------|------|
| `check-env.ps1`      | 自检：MySQL/Redis 端口、JWT 密钥、WATERFUN_KEK、DEVICE_SALT |
| `start-redis.bat`    | 启动本地 Redis（127.0.0.1:6379） |
| `start-account.bat`  | 启动 `nitrowater-account`（SSO，端口 8090） |
| `gen-keys.ps1`       | 生成 RSA 2048 密钥对 → `deploy/keys/{private,public}.key` |
| `backup_mysql.ps1`   | 备份 `nitrowater_account` 库（gzip + 保留 30 天） |

## 快速开始

```powershell
# 1) 生成/放置 JWT 密钥（若已有 waterfun 密钥可直接复制到 deploy/keys/）
.\deploy\bin\gen-keys.ps1

# 2) 启动 Redis（MySQL 作为 Windows 服务，假定已在运行）
.\deploy\bin\start-redis.bat

# 3) 自检
.\deploy\bin\check-env.ps1

# 4) 启动 account
.\deploy\bin\start-account.bat
```

## 前置配置

1. **JWT 密钥**：`deploy/keys/private.key` + `deploy/keys/public.key`
   （可直接复制 `D:\Project\waterfun\deploy\keys\*`；已 gitignore）。
   也可用 `gen-keys.ps1` 重新生成。
2. **WATERFUN_KEK**：32 字节 AES KEK 的 Base64。设置环境变量，或使用
   `nitrowater-account/build.gradle` 中 `bootRun` 的开发默认值。
3. **DEVICE_SALT**：设备指纹盐值，已在 `application.yml` 复用 waterfun 开发值。

详见 `deploy/env-vars-reference.md`。
