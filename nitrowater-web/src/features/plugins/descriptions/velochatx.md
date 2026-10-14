# VeloChatX

> 面向 **Minecraft Velocity** 代理的高性能跨服聊天插件。

VeloChatX 让整个代理网络共用一套干净、快速、可定制的聊天体验——从玩家加入 / 离开提示，到跨服广播、
定时公告、@提及与 TabList 替换，全部开箱即用；配置集中、可增量启停，适合作为社区服的基础设施。

## 核心特性

- **跨服聊天** — 消息在代理网络内实时互通；玩家进入 / 离开时，提示会同时广播到**原服**与**当前服**。
- **高度可定制** — 频道 / 组别 / 前后缀等占位符自由组合，支持 MiniMessage 富文本（渐变、悬浮、点击）。
- **跨服与群组广播** — 把一条消息定向广播到指定服务器或服务器组。
- **定时广播** — 按间隔自动轮播公告（可配置顺序、按权限可见）。
- **@ 提及** — 在聊天中 @ 玩家并高亮其标题。
- **TabList 替换** — 自定义 Tab 列表的页眉 / 页脚与玩家排序。
- **违禁词过滤** — 内置敏感词过滤，命中可拦截或替换。
- **i18n** — 内置 `zh_CN` / `en_US`，按玩家客户端语言自动切换。
- **数据持久化** — 基于数据库保存玩家设置等数据。
- **自动更新** — 检测新版本并提示升级。

## 安装

1. 在下方 **下载** 区取最新的 `VeloChatX.jar`。
2. 放入 Velocity 的 `plugins/` 目录。
3. **启动** 服务器，插件会自动生成配置文件。
4. **停止** 服务器，按需修改 `config`。
5. **重启** 服务器即可生效。

> 依赖 **Java 17+**；建议配合 [LuckPerms](https://luckperms.net/) 读取玩家前后缀等数据。

## 命令与权限（概要）

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/vchat reload` | 重载配置文件 | `velochatx.command.reload` |
| `/vchat broadcast <消息>` | 发送一次跨服广播 | `velochatx.command.broadcast` |
| `/vchat` | 其余子命令 | 见完整文档 |

> 上表仅为概要。**完整命令树、全部权限节点与占位符**见
> [文档 · 命令与权限](/plugin/velochatx/docs/reference/cmdsAndPerms.html)。

## 兼容性

| 项 | 说明 |
| --- | --- |
| 平台 | Velocity 3.x |
| 依赖 | LuckPerms（可选）、Java 17+ |
| 语言 | 简体中文 / English |
| 更新 | 自动检查新版本 |

## 相关链接

- **完整文档**：见本页上方「查看完整文档」（[快速开始](/plugin/velochatx/docs/guide/intro/get-start.html)）
- **源码仓库**：<https://github.com/Danburen/VeloChatX>
- **问题反馈**：<https://github.com/Danburen/VeloChatX/issues>
