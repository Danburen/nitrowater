# deploy/plugin-docs — 插件文档（独立部署）

本目录是 **运行时** 挂载的、由 **各插件仓独立构建并发布** 的静态文档子站。它 **不属于 web 构建**，
所以更新文档 **不需要** 重打/重发前端或后端，也 **不在** `nitrowater-web/public/` 里。

## 目录约定

```
deploy/plugin-docs/
  README.md            # 本文件（入库）
  velochatx/           # 各插件的命名空间（构建产物 gitignore）
    docs/              #   VitePress 站点  -> /plugin/velochatx/docs/
      index.html  assets/  guide/  reference/  en/  ...
```

- **docs 容器**（`nitrowater-docs`，nginx）把本目录只读挂到 `/usr/share/nginx/html/plugin`，于是
  `GET /plugin/<name>/**` → 容器内 `/plugin/<name>/**` → 磁盘 `<name>/`。即 `velochatx` → `GET /plugin/velochatx/**`。
- **BFF 只做路由转发**：`/plugin/**` → `app.plugin-docs.upstream`（compose 里 `http://docs:80`，
  本地 dev `http://localhost:8085`）；BFF 自己不再读盘（旧 `WebConfig` 的 `/plugin/**` 资源处理器已移除）。
- `docs/` = 插件文档站（本目录**唯一**职责）。插件的**下发资源（Jar）已迁出本目录**：改由统一资源服务
  （`nitrowater-resource`，:8093）+ 对象存储（SeaweedFS）托管，见下方「插件下载资源」。
- 门户侧 **只保留路由/链接**：SPA `/plugins`（插件中心）→ 文档 `<a href="/plugin/velochatx/docs/">`；
  下载走资源服务列表接口（`/api/public/resources?app=nitrowater&group=velochatx`）。

## 契约（必须遵守）

1. **`base` = 文档挂载路径**：文档构建时 `base` 必须等于它的 URL 前缀（例：`/plugin/velochatx/docs/`）。
   velochatx-docs 已用环境变量支持：`VITEPRESS_BASE=/plugin/velochatx/docs/ npm run docs:build`。
2. **整套产物**放到 `deploy/plugin-docs/<plugin>/docs/`（`index.html` 必须在根部）。
3. **内部链接带 `.html`**（VitePress 默认 `cleanUrls: false`）：这样深层页面是真实文件、
   不会被 SPA fallback 劫持；目录首页（`/plugin/velochatx/docs/`）由 docs 容器的 nginx
   `try_files $uri $uri/ $uri/index.html` 兜底。
4. **下载资源（不在本目录）**：Jar 由 `bin/sync-res.ps1` 经内部发布端点（`POST /internal/resource/publish`）
   写入统一资源服务，落对象存储并以 `app=nitrowater` + `group_key=<plugin>` 归纳。
   插件中心调用列表接口渲染版本列表，因此 **新增一个 Jar 无需改前端代码**。

## 发布（文档仓流水线）

文档仓 CI：构建（带 `VITEPRESS_BASE=/plugin/<name>/docs/`）→ 把产物同步到本目录（rsync/scp 到服务器，
或写入被挂载的卷）。建议 **原子替换**：先传到临时目录、再整体切换，避免读到半套。

多实例 / 版本化（可选）：`deploy/plugin-docs/velochatx/docs/<semver>/` + 一个 `current` 指针，
门户 / 文档链接指向 `.../velochatx/docs/current/`；回滚 = 切指针。

## 本地 / 开发

- `bin/sync-docs.ps1`：按 `/plugin/<plugin>/docs/` 构建 velochatx-docs 并同步到 `docs/`（dev 用，一条命令）。
- `bin/sync-res.ps1`：把插件构建出的 Jar（默认 `VeloChatX\out\artifacts\VeloChatX\*.jar`）发布到统一资源服务
  （直连 `:8093` 的 `POST /internal/resource/publish`，需服务端 `app.resource.internal.token`；bootRun 已注入 dev 默认）。
- docker 容器：compose（`deploy/docker/docker-compose.app.yml`）把本目录 **只读挂进 docs 容器**
  `../plugin-docs:/usr/share/nginx/html/plugin:ro`，nginx 配置为 `deploy/docker/docs-nginx.conf`。
  本地 dev：`docker compose -f deploy/docker/docker-compose.app.yml up -d docs`（或直接跑
  `bin/start-apps.ps1`，已内置 docs 容器启动），容器监听 `127.0.0.1:8085`（= BFF 的 `app.plugin-docs.upstream` 默认）。
  发布新文档后无需重启 BFF，可选 `docker restart nitrowater-docs` 立即生效。

## 为什么不用 `nitrowater-web/public/`？

`public/` 的内容会在 `vite build` 时被拷进 `dist/`，等于把文档 **焊进前端产物** —— 更新文档就得
重发前端。放到这里（web 构建之外、运行期读盘）才能让文档仓 **独立发版 / 独立回滚**。
