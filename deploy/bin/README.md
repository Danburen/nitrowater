# deploy/bin — 运维 / 部署脚本

> 与 `bin/`（本地启动）分离：这里放**运维/部署**脚本。均从**仓库根目录**执行。
> 每个脚本同时提供 **`.ps1`（Windows）** 与 **`.sh`（Linux/macOS）**；`.sh` 首次使用前 `chmod +x deploy/bin/*.sh`。

| 用途 | Windows | Linux/macOS |
|------|---------|-------------|
| 生成 JWT 密钥 → `deploy/keys/` | `gen-keys.ps1` | `gen-keys.sh` |
| 环境自检（端口/密钥/KEK/salt；加载 `.env`） | `check-env.ps1` | `check-env.sh` |
| 备份 `nitrowater_account`（gzip + 保留 30 天） | `backup_mysql.ps1` | `backup_mysql.sh` |
| 清空 SSO 表（Flyway 重建） | `reset-sso-db.ps1` | `reset-sso-db.sh` |
| **单入口部署**（校验 `.env` → compose config → 起 infra+app） | `deploy.ps1` | `deploy.sh` |

## 部署（单入口）

```powershell
# Windows
Copy-Item deploy/docker/.env.example deploy/docker/.env   # 填写真实值
.\deploy\bin\deploy.ps1            # 起 infra + app
.\deploy\bin\deploy.ps1 -InfraOnly # 只起中间件
.\deploy\bin\deploy.ps1 -Build     # 本地构建镜像
```
```bash
# Linux / macOS
cp deploy/docker/.env.example deploy/docker/.env          # 填写真实值
chmod +x deploy/bin/*.sh
bash deploy/bin/deploy.sh              # 起 infra + app
bash deploy/bin/deploy.sh --infra-only # 只起中间件
bash deploy/bin/deploy.sh --build      # 本地构建镜像
```

`deploy.{ps1,sh}` 先 `docker compose config` 校验（缺必需变量/语法错即失败），再依次拉起 infra、app —— 对齐 waterfun 的 `deploy/bin/deploy.sh`；compose 自带 mysql/redis `healthcheck`。

详见 `deploy/docker/` 与 `deploy/env-vars-reference.md`。

