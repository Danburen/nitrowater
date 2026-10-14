# deploy/plugin-docs — 插件文档 / 插件资源（独立部署）

本目录是 **运行时** 挂载的、由 **各插件仓独立构建并发布** 的静态子站与下载资源。它 **不属于 web 构建**，
所以更新文档/资源 **不需要** 重打/重发前端或后端，也 **不在** `nitrowater-web/public/` 里。

## 目录约定

```
deploy/plugin-docs/
  README.md            # 本文件（入库）
  velochatx/           # 各插件的命名空间（构建产物 gitignore）
    docs/              #   VitePress 站点  -> /plugin/velochatx/docs/
      index.html  assets/  guide/  reference/  en/  ...
    res/               #   下载资源      -> /plugin/velochatx/res/
      VeloChatX.jar  VeloChatX-2.0.8.jar  ...  manifest.json
```

- BFF 把 `GET /plugin/<name>/**` 映射到这里的 `<name>/`（见 `nitrowater-bff` 的 `WebConfig`；
  根目录可用 `app.web.plugin-docs-dir` 覆盖，默认 `./deploy/plugin-docs`）。
  即 `velochatx` → `GET /plugin/velochatx/**`。
- `docs/` = 插件文档站；`res/` = 本地下发资源（插件中心「下载」按钮绑定的 Jar，**不经 GitHub/CDN 中转**）。
- 门户侧 **只保留路由/链接**：SPA `/plugins`（插件中心）→ 文档 `<a href="/plugin/velochatx/docs/">`，
  下载 `<a href="/plugin/velochatx/res/VeloChatX.jar">`。

## 契约（必须遵守）

1. **`base` = 文档挂载路径**：文档构建时 `base` 必须等于它的 URL 前缀（例：`/plugin/velochatx/docs/`）。
   velochatx-docs 已用环境变量支持：`VITEPRESS_BASE=/plugin/velochatx/docs/ npm run docs:build`。
2. **整套产物**放到 `deploy/plugin-docs/<plugin>/docs/`（`index.html` 必须在根部）。
3. **内部链接带 `.html`**（VitePress 默认 `cleanUrls: false`）：这样深层页面是真实文件、
   不会被 SPA fallback 劫持；目录首页（`/plugin/velochatx/docs/`）由 BFF 的目录解析兜底。
4. **资源清单**：`res/manifest.json` 列出全部 Jar（`name` / `version` / `bytes` / `modified`，新→旧）。
   插件中心据此渲染版本列表，因此 **新增一个 Jar 无需改前端代码**，重跑 `bin/sync-res.ps1` 即可。

## 发布（文档仓流水线）

文档仓 CI：构建（带 `VITEPRESS_BASE=/plugin/<name>/docs/`）→ 把产物同步到本目录（rsync/scp 到服务器，
或写入被挂载的卷）。建议 **原子替换**：先传到临时目录、再整体切换，避免读到半套。

多实例 / 版本化（可选）：`deploy/plugin-docs/velochatx/docs/<semver>/` + 一个 `current` 指针，
BFF 指向 `.../velochatx/docs/current/`；回滚 = 切指针。

## 本地 / 开发

- `bin/sync-docs.ps1`：按 `/plugin/<plugin>/docs/` 构建 velochatx-docs 并同步到 `docs/`（dev 用，一条命令）。
- `bin/sync-res.ps1`：把插件构建出的 Jar（默认 `VeloChatX\out\artifacts\VeloChatX\*.jar`）同步到 `res/`
  并重写 `manifest.json`。
- docker compose（`deploy/docker/docker-compose.app.yml`）已把本目录 **只读挂进 bff**：
  `../plugin-docs:/app/deploy/plugin-docs:ro`。

## 为什么不用 `nitrowater-web/public/`？

`public/` 的内容会在 `vite build` 时被拷进 `dist/`，等于把文档 **焊进前端产物** —— 更新文档就得
重发前端。放到这里（web 构建之外、运行期读盘）才能让文档仓 **独立发版 / 独立回滚**。
