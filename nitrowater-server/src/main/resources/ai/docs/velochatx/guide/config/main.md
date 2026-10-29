# 主配置与频道

本文对应 `config.yml`（主配置）以及其中的跨服显示与频道（分区）部分。所有键都在 `config.yml` 中，修改为 YAML 格式，注意缩进。

## 1. 聊天格式 `chat-format`

```yaml
chat-format: "{channel}{Group}{Server}{Prefix}{Player}{Suffix} §8:§r {Message}"
```

| 占位符 | 含义 |
|---|---|
| `{channel}` | 玩家所在服务器所属的频道显示名 |
| `{Group}` | 玩家组别（LuckPerms） |
| `{Server}` | 玩家所在子服的显示名（由 `server-display` 映射） |
| `{Proxy}` | 代理服显示名 |
| `{Prefix}` / `{Suffix}` | 玩家前缀 / 后缀 |
| `{Player}` | 玩家名 |
| `{Message}` | 聊天正文 |

规则与边界：

- **`{Server}`、`{Message}`、`{PlayerName}` 是必需字段**。插件加载时校验，缺失任意一个会整体回退为缺省格式：`[{Server}]{PlayerName}: {Message}`。
- 占位符大小写不敏感，推荐统一小写。
- 文本支持 Minecraft 颜色码（`§` 与 `&`），也支持通过 `chat-format-model` 转换后发送。

## 2. 聊天文本转换 `chat-format-model`

控制转发前的文本转换方式，用于兼容不同客户端（例如 Fabric 客户端出现格式异常时）：

| 值 | 行为 |
|---|---|
| `none` | 不做转换，发送原始文本（原版客户端兼容性最好） |
| `legacy` | 转换为 Adventure Legacy Component（客户端格式异常时推荐） |
| `mini-message` | 转换为 MiniMessage（支持更丰富的文本效果） |

## 3. 终端输出 `log-text`

Velocity 默认不记录子服聊天，因此插件提供终端输出开关：

```yaml
log-text:
  enable: true
  convert: true
```

- `enable`：是否把跨服聊天写入终端/日志。
- `convert`：是否把颜色码转换为 ANSI 终端转义序列。终端出现乱码或颜色异常时设为 `false`。

## 4. 提及标题 `mention-show-title`

```yaml
mention-show-title:
  enable: true
  time:
    fade-in: 1
    stay: 2
    fade-out: 1
```

- 时间单位均为**秒**。
- 主/副标题文本来自 `message.yml` 中以 `mention-...` 开头的键。
- 标题会占用玩家主屏幕，用于强提醒；不影响聊天消息本身。

## 5. TabList 替换 `tab-list`

```yaml
tab-list:
  enable: true
  interval: 1000
  format: "{server}{prefix}{player}{Suffix}"
  header: "欢迎加入{proxy} 当前服务器: {server}"
  footer: "在线人数 §8{online}§r / §8{total_online}§r | Ping: §8{ping}"
```

- `interval`：刷新间隔（毫秒）。`0` 表示被动刷新，仅在玩家加入/离开时更新。
- `format`：每个玩家的显示格式，支持玩家与服务器两类占位符。
- `header` / `footer`：仅支持服务器类占位符。
- 玩家数量大时 TabList 刷新会占用性能，可适当调大 `interval`。

## 6. 违禁词屏蔽 `ban-words`

```yaml
ban-words:
  enable: true
  log-to-console: true
  words: "违禁词1,违禁词2"
```

- 消息包含任一违禁词时**不转发**。
- 多个词用英文逗号 `,` 分隔。
- `log-to-console` 为 `true` 时在终端输出命中的玩家与内容，便于管理员处理。
- 只作用于跨服转发；子服内部聊天需要子服侧插件处理。

## 7. 子服显示名 `server-display`

键名必须与 Velocity 的 `velocity.toml` 中 `[servers]` 的键一一对应：

```toml
[servers]
lobby = "127.0.0.1:25565"
server1 = "127.0.0.1:25566"
server2 = "127.0.0.1:25567"
```

```yaml
server-display:
  enable: true
  proxy: "[§5代理服§r]"
  display:
    lobby: "[§d大厅服§r]"
    server1: "[§3小游戏服§r]"
    server2: "[§2生存服§r]"
```

- 插件会把 Velocity 的子服 key 翻译为这里的显示名，供 `{server}` / `{proxy}` 使用。
- 玩家处于未在 `display` 中列出的子服时，`{server}` 会退化为「未知」或 `UNKNOWN`。
- `proxy` 用于代理端（控制台）发出的消息。

## 8. 频道（分区聊天）`channels`

```yaml
channels:
  global: true
  channel-list:
    login:
      display: "[§6登录频道§r]"
      welcome:
        enable: true
        message: "欢迎加入登录频道"
      servers:
        - lobby
    games:
      display: "[§6小游戏频道§r]"
      welcome:
        enable: true
        message: "欢迎加入小游戏频道"
      servers:
        - server1
    survive:
      display: "[§6生存频道§r]"
      welcome:
        enable: true
        message: "欢迎加入生存频道"
      servers:
        - server2
```

- `global: true`：所有频道互通（不隔离）；`false`：频道之间隔离，其他频道的消息不会传入。
- `servers`：该频道包含的子服 key（对应 Velocity `[servers]`）。
- 一个频道可包含多个子服；一个子服也可以属于多个频道。插件按子服的**交集**决定转发范围。
- 频道隔离**不影响跨服私聊和提及**。

边界规则：

- 多个 `channel` 同名时，只有第一次出现的生效。
- 未出现在 `channels` 中的子服默认全服互通，`{channel}` 占位符渲染为空。
- 频道隔离是转发层的规则，子服内部聊天始终由子服自己处理。
