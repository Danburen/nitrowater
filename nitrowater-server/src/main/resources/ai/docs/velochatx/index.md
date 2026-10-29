# VeloChatX 概述与安装

VeloChatX 是一款运行在 **Minecraft Velocity 代理端**上的跨服聊天插件，解决多子服场景下的消息互通、频道隔离、私聊、屏蔽、广播与信息展示问题。

- 运行位置：Velocity 代理端（不是 Paper/Spigot 子服插件）
- 技术栈：自研 WaterAPI + Velocity API + LuckPerms API + H2（本地数据）
- 推荐版本：2.0 及以上；2.0 以下版本的目录结构与本文不同
- 命令别名：`/vcx`、`/vc`、`/chatx`

## 1. 能力范围

| 能力 | 说明 | 边界 |
|---|---|---|
| 跨服聊天 | 把玩家消息按规则转发给其他子服/频道 | 只处理**跨服转发**；子服内部聊天由子服插件处理，VeloChatX 不干预 |
| 分区聊天 | 按 `channel` 隔离消息，可全局互通或分区隔离 | 频道内互通、频道外隔离；不影响私聊与提及 |
| 跨服私聊 | `/vcmsg <player> <message>` | 消息仅在玩家之间传递，后台会记录指令调用 |
| 忽略 / 拒收 | 玩家可屏蔽指定玩家的消息或提及 | 忽略=接受并丢弃；拒收=明确拒绝并提示发送方 |
| 违禁词屏蔽 | 转发前检查配置中的违禁词 | 只作用于跨服转发，子服内部聊天不生效 |
| 定时广播 | 通过内置计时器按间隔发送全服/区服广播 | 广播内容支持占位符与颜色代码 |
| 被动广播 | 玩家加入/离开子服或代理服时播报 | 支持欢迎消息与首次加入判断 |
| 在线信息占位符 | `{online}`、`{total_online}`、频道/子服在线人数等 | 分玩家环境与服务器环境 |
| TabList 替换 | 用自定义格式替换玩家列表显示 | 玩家量大时有性能开销，可调大刷新间隔 |

## 2. 环境要求

| 依赖 | 版本 |
|---|---|
| Velocity | 推荐 3.3.0 及以上 |
| LuckPerms（Velocity 版） | 推荐 5.4 及以上 |
| VeloChatX | 推荐 2.0 及以上 |

## 3. 安装步骤

1. 将 `velochatx-x.x.x.jar` 放入 Velocity 的 `plugins/` 目录。
2. 启动代理端，等待控制台出现「成功加载本插件」。
3. 关闭服务器后编辑配置文件，再启动生效；大部分配置可用 `/vcx reload` 热重载。

> 插件加载时会校验配置完整性：缺失的键会使用缺省值，因此配置不完整通常不会导致启动失败，但相关功能会退化为默认行为。

## 4. 首次运行生成的文件

```text
plugins/VeloChatX/
├─ config.yml      # 主配置：聊天格式、频道、TabList、违禁词等
├─ broadcast.yml   # 定时广播与被动广播配置
├─ message.yml     # 可自定义的提示文本（如提及标题）
└─ data.mv.db      # H2 数据库：玩家 UUID、忽略/拒收关系等
```

- `config.yml`：绝大部分行为在此修改。
- `broadcast.yml`：广播相关内容，见《广播配置》。
- `message.yml`：面向玩家的文案，可按全局或按玩家语言选择。
- `data.mv.db`：保存玩家的屏蔽关系，**删除即丢失所有玩家的忽略/拒收数据**，其中没有敏感信息。

## 5. 配置通用注意事项

- 所有配置文件都是 **YAML**，对缩进敏感；修改前备份，修改后用 `/vcx reload` 或重启验证是否解析成功。
- 文本普遍支持 Minecraft 颜色码（`§` 与 `&` 会被转义），也支持 Adventure Legacy / MiniMessage（见主配置的 `chat-format-model`）。
- 占位符大小写不敏感，推荐统一小写。

## 6. 相关文档

- [主配置与频道](guide/config/main.md)
- [广播配置](guide/config/active-broadcast.md)
- [命令与权限](reference/cmdsAndPerms.md)
- [占位符](reference/placeholder.md)
