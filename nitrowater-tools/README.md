# NitroWater 离线工具箱

本目录是**独立的静态站点包**：从主站抽离出的浏览器本地工具（MOTD、server.properties、server-icon、启动脚本、压缩/解压、树可视化、Java DTO 转换、MP4 转 GIF、Markdown 转 Word）。不与 BFF / OIDC 通信，文件与文本只在用户浏览器里处理。

在线代码运行器不在这里——它仍属于主站的登录功能（`/runner`）。

## 本地开发

```powershell
npm install
npm run dev       # http://localhost:5174
npm run typecheck
npm run lint
npm run build
```

无需环境变量：本站不回连门户，也不需要任何后端。门户通过 `_blank` 外链打开本站，跳转后两边完全独立。

## IntelliJ IDEA

仓库根是 Gradle 工程，本目录**不是** Gradle 子项目（不要加进 `settings.gradle`，否则 Gradle 同步会失败）。IDEA 里按静态 Web 模块识别：仓库根已有 `nitrowater-tools.iml`（与 `nitrowater-web.iml` 同款 `WEB_MODULE`），并注册在 `.idea/modules.xml`。`.idea/` 与 `*.iml` 被 `.gitignore` 忽略，换机器/重新导入 Gradle 后若模块消失，按 `nitrowater-web.iml` 的内容重建该 `.iml` 再在 Project Structure → Modules 里添加即可。

## 容器镜像

在仓库根目录构建，产物是可直接部署的静态站点镜像：

```powershell
docker build -f nitrowater-tools/Dockerfile -t nitrowater-tools:local .
docker run --rm -p 8080:80 nitrowater-tools:local
```

镜像没有任何构建参数，也不包含门户地址。nginx 已配置 history 回退与 `.wasm` MIME 类型，支持 `/tools/<name>` 深链接直接刷新。

## CI 镜像

`.github/workflows/build.yml` 会把本目录构建成 `ghcr.io/<owner>/<repo>/tools:<tag>`（以及 `:<sha>` 回滚标签）并推送，和四个后端镜像同一批发版；它不属于 app 的 compose 单元，deploy 工作流不会拉取它。

工具站本身没有任何 `VITE_*` 构建参数，也不保存门户地址。反向的入口关在门户侧：门户镜像用 `VITE_OFFLINE_TOOLS_URL`（GitHub 仓库变量，默认 `https://tools.example.com`）决定「离线工具箱」外链指向哪里，以 `_blank` 打开，与本站无运行时依赖。

外部服务只需拉取该镜像并暴露 80 端口，无需依赖 NitroWater 的任何后端服务。
