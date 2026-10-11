# bin/ — nitrowater 启动脚本（本地 dev）

> 目录分工（均从**仓库根目录**执行）：
> - `bin/` — 本地**启动**包装（`start-*.bat`）
> - `bin/test/` — **端到端冒烟**（smoke-*）
> - `deploy/bin/` — **运维/部署**脚本（gen-keys / check-env / backup_mysql / reset-sso-db / deploy）
> - `deploy/docker/` — Docker Compose（infra / app / piston）

## 启动（本地 dev）

| 脚本 | 用途 |
|------|------|
| `start-redis.bat`     | 本地 Redis（127.0.0.1:6379） |
| `start-account.bat`   | `nitrowater-account`（SSO，:8090） |
| `start-bff.bat`       | `nitrowater-bff`（BFF / Token Handler，:8080） |
| `start-seaweedfs.bat` | 自托管 SeaweedFS（S3，:8333；本地磁盘） |
| `start-resource.bat`  | `nitrowater-resource`（统一资源服务，:8093） |
| `start-piston.bat`    | Piston 代码沙箱（判题/运行，:2000；需 Docker + cgroup v2 + privileged） |

> **Piston 语言运行时**：官方镜像 `ghcr.io/engineer-man/piston` **不含任何语言**。首次用
> `.\bin\install-piston-langs.ps1` 安装（经 GitHub 代理 `ghproxy.net` 拉包；装 java/gcc/node/python/typescript/lua，
> 覆盖 server 的 7 语言）。运行时持久化在命名卷 `nitrowater-piston`，只需装一次。生产/compose 路径见
> `deploy/docker/docker-compose.piston.yml`（不映射宿主端口）。

## 快速开始（本地 dev）

```powershell
.\deploy\bin\gen-keys.ps1        # 1) 生成/放置 JWT 密钥
.\bin\start-redis.bat            # 2) 本地 Redis（MySQL 假定已在运行）
.\deploy\bin\check-env.ps1       # 3) 自检
.\bin\start-account.bat          # 4) SSO
.\bin\start-bff.bat              # 5) BFF
.\bin\start-seaweedfs.bat        # 6) 存储
.\bin\start-resource.bat         # 7) 资源服务
```

## 冒烟测试
`bin/test/`：`smoke-auth` / `smoke-oidc` / `smoke-logout` / `smoke-resource`。

## 运维 / 部署 / Docker
- 运维/部署脚本：`deploy/bin/`（详见 `deploy/bin/README.md`）
- Docker Compose：`deploy/docker/`（infra + app + piston）；单入口 `deploy/bin/deploy.ps1`

## 前置配置

1. **JWT 密钥**：`deploy/keys/{private,public}.key`（`deploy/bin/gen-keys.ps1` 生成；已 gitignore）。
2. **WATERFUN_KEK**：32 字节 AES KEK 的 Base64（或 `nitrowater-account/build.gradle` bootRun 开发默认）。
3. **DEVICE_SALT**：已在 `application.yml` 复用 waterfun 开发值。

详见 `deploy/env-vars-reference.md`。脚本编码：`.bat`=ASCII+CRLF；`.ps1`=UTF-8 with BOM。
