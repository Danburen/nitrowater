# admin-ui — NitroWater 管理台（账号中心）

Vue 3 + TypeScript + Vite 的单页应用，**同源托管**在 `nitrowater-account` 的 `/admin/` 下（hash 路由
`/admin/#/...`）。构建产物直接输出到 `../src/main/resources/static/admin`（已 gitignore），
随 `:nitrowater-account:build` 打包进 `app.jar` —— 一条命令即可得到含管理台的完整 jar。

admin-ui 与 auth-ui 同属 `nitrowater-account/` 下的 pnpm workspace（`pnpm-workspace.yaml`），
共享包在 `packages/*`（`@nitrowater/ui` 设计令牌/主题、`@nitrowater/http`、ESLint/TS 配置）。
在 `nitrowater-account/` 下先执行一次 `pnpm install`。

## 技术栈

- **Vue 3**（`<script setup>`）+ **vue-router 5**（hash history）
- **Element Plus**（复杂组件）+ **Tailwind CSS v4**（`@tailwindcss/vite`，工具类样式）
- **ESLint 10**（flat config：`eslint-plugin-vue` + `typescript-eslint`）

## 命令

| 命令 | 说明 |
|---|---|
| `pnpm dev:admin`（workspace 根） | 开发服务器 `:5174`，`/api` 代理到 account `:8090`（复用 SSO 会话 Cookie） |
| `pnpm dev`（本目录） | 同上 |
| `pnpm build`（本目录） | 类型检查（`vue-tsc`）+ 构建到 `../src/main/resources/static/admin` |
| `pnpm build:admin`（workspace 根） | 同上 |
| `pnpm lint` / `pnpm lint:fix` | ESLint 检查 / 自动修复 |
| `pnpm typecheck` | 仅类型检查 |

## 页面

- **用户管理**（`/#/users`）：分页 + 关键字搜索 + 分配角色（对话框）
- **角色管理**（`/#/roles`）：角色 CRUD（内置角色不可删除）

## 样式与主题

与 `nitrowater-web` 共用同一套设计令牌：`src/style.css` 引入 `@nitrowater/ui/theme.css`（Tailwind v4
`@theme` 颜色令牌 `--color-bg/card/soft/line/ink/dim/accent/...`），深色为默认、浅色通过
`:root[data-theme='light']` 反转同一批令牌（组件代码不变）；`@nitrowater/ui/element-plus.css`
用 `html:root` 把 Element Plus 的 `--el-*` 变量桥接到这些令牌，使 EP 组件（表格/对话框/分页/标签/按钮）
自动跟随主题（含对 EP 表格硬编码 `#fff` 的覆盖）。`@nitrowater/ui` 的 `configureTheme`/`initTheme`
负责切换与持久化（`localStorage` 键 `nitrowater-admin-theme`，默认跟随系统），
`index.html` 的内联脚本在首屏前应用以避免主题闪烁（FOUC）；组件类 `.btn/.btn-primary/.tag/.panel/.section-head`
复用参考模块同名工具类。

## 权限

后端 `/admin/**`（SPA 壳 + 资源）与 `/api/admin/**` 均需登录；其中数据面 `/api/admin/**` 另限
`ROLE_SUPER_ADMIN`（`SUPER_ADMIN` 自动蕴含 `ROLE_ADMIN`）。匿名访问页面会由 Spring `302` 到
`/login`（带 SavedRequest，登录后回到 `/admin/`）；接口 401 返回 JSON，前端 `src/api/http.ts` 兜底跳 `/login`。

前端另有路由守卫（`src/router/index.ts` + `src/auth/session.ts`）：**除 `meta.public` 外一律私有**，
首次导航前解析一次会话——**401 → 交 http 层跳登录**，**403（已登录但非超管）→ 跳 `/forbidden`**。
今天没有任何 `meta.public` 路由（登录页由 account 服务出，不在 SPA 内）。

> 冒烟：`..\..\.ai\testkit\smoke-admin.ps1`
