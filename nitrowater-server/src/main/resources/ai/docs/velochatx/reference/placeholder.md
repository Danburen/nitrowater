# 占位符参考

VeloChatX 提供一组内置占位符，用于聊天格式、TabList、广播与提示文本。游戏内可用 `/vcx placeholder <placeholder>` 验证渲染结果。

## 1. 环境限制

- **玩家环境**（聊天、TabList、玩家触发的提示）才能解析玩家类占位符。
- **服务器环境**（控制台、定时广播、服务器消息）才能解析服务器类占位符。
- 在不适用的环境中，占位符不会被填充。

## 2. 服务器类占位符

| 占位符 | 含义 |
|---|---|
| `{channel}` | 当前服务器所属频道的显示名；未配置频道时为空 |
| `{server}` | 当前子服的显示名（来自 `server-display.display`） |
| `{proxy}` | 代理服显示名（来自 `server-display.proxy`） |
| `{online}` | 当前子服的在线人数 |
| `{total_online}` | 所有子服的在线总人数 |

## 3. 玩家类占位符

| 占位符 | 含义 |
|---|---|
| `{player}` | 当前玩家名称 |
| `{prefix}` | 玩家前缀（LuckPerms） |
| `{suffix}` | 玩家后缀（LuckPerms） |
| `{group}` | 玩家组别 |
| `{ping}` | 玩家延迟 |

## 4. 通用类占位符

| 占位符 | 含义 |
|---|---|
| `{xxx_channel_online}` | 指定频道的在线人数，`xxx` 为频道名 |
| `{xxx_server_online}` | 指定子服的在线人数，`xxx` 为子服 key |

示例：

```text
{login_channel_online}   -> 登录频道的在线人数
{survive_server_online}  -> 生存服的在线人数
```

## 5. 使用要点

- 占位符大小写不敏感，推荐统一小写。
- 未知子服或未配置的频道会退化为「未知」/`UNKNOWN` 或空白，不会抛出异常。
- 聊天格式中 `{server}`、`{message}`、`{player}` 为必需项，缺失会导致格式回退（见主配置）。
