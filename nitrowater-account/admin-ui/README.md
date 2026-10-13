# admin-ui — NitroWater 管理台（账号中心）

Vue 3 + TypeScript + Vite 的单页应用，**同源托管**在 `nitrowater-account` 的 `/admin/` 下（hash 路由
`/admin/#/...`）。构建产物直接输出到 `../src/main/resources/static/admin`（已 gitignore），
随 `:nitrowater-account:build` 打包进 `app.jar` —— 一条命令即可得到含管理台的完整 jar。

## 技术栈

- **Vue 3**（`<script setup>`）+ **vue-router 5**（hash history）
- **Element Plus**（复杂组件）+ **Tailwind CSS v4**（`@tailwindcss/vite`，工具类样式）
- **ESLint 10**（flat config：`eslint-plugin-vue` + `typescript-eslint`）

## 命令

| 命令 | 说明 |
|---|---|
| `npm run dev` | 开发服务器 `:5174`，`/api` 代理到 account `:8090`（复用 SSO 会话 Cookie） |
| `npm run build` | 类型检查（`vue-tsc`）+ 构建到 `../src/main/resources/static/admin` |
| `npm run lint` / `npm run lint:fix` | ESLint 检查 / 自动修复 |
| `npm run typecheck` | 仅类型检查 |

## 页面

- **用户管理**（`/#/users`）：分页 + 关键字搜索 + 分配角色（对话框）
- **角色管理**（`/#/roles`）：角色 CRUD（内置角色不可删除）

## 样式与主题

与 `nitrowater-web` 共用同一套设计令牌：`src/style.css` 用 Tailwind v4 的 `@theme` 定义颜色令牌
（`--color-bg/card/soft/line/ink/dim/accent/...`），深色为默认、浅色通过 `:root[data-theme='light']`
反转同一批令牌（组件代码不变）；并用 `html:root` 把 Element Plus 的 `--el-*` 变量桥接到这些令牌，
使 EP 组件（表格/对话框/分页/标签/按钮）自动跟随主题（含对 EP 表格硬编码 `#fff` 的覆盖）。
`src/theme/useTheme.ts` 负责切换与持久化（`localStorage` 键 `nitrowater-admin-theme`，默认跟随系统），
`index.html` 的内联脚本在首屏前应用以避免主题闪烁（FOUC）；组件类 `.btn/.btn-primary/.tag/.panel/.section-head`
复用参考模块同名工具类。

## 权限

后端 `/api/admin/**` 仅 `ROLE_SUPER_ADMIN` 可访问（`SUPER_ADMIN` 自动蕴含 `ROLE_ADMIN`）；
`/admin/**` 是 SPA 壳，匿名可加载，但所有数据接口仍受保护。未登录访问接口返回 401，
前端 `src/api/http.ts` 会自动跳转 `/login`。

> 冒烟：`..\..\.ai\testkit\smoke-admin.ps1`
