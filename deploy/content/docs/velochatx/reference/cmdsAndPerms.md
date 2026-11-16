# 命令与权限

VeloChatX 的命令前缀支持别名：`/vcx`、`/vc`、`/chatx`。任何人（玩家或管理员）都可以用 `/vcx help` 查看帮助。

## 1. 玩家命令

权限路径：`velochatx.player`

| 命令 | 别名 | 作用 |
|---|---|---|
| `/mention <player>` | `/men`、`/at` | 对指定玩家显示提及标题（标题样式见 `mention-show-title` 与 `message.yml`） |
| `/vcmsg <player> <message>` | `/vctell` | 跨服私聊，消息只在双方之间传递 |
| `/vcx on` / `/vcx off` | - | 开启 / 关闭自己的跨服聊天接收 |
| `/vcx ignore <player>` | - | 忽略某玩家：消息被接受但丢弃，发送方仍看到「已发送」 |
| `/vcx reject <player>` | - | 拒收某玩家：消息被拒绝，发送方会收到拒收提示 |
| `/vcx list <player>` | - | 查看忽略/拒收列表 |
| `/vcx remove <player>` | - | 取消对某玩家的忽略/拒收 |

忽略与拒收的区别：

- **忽略（ignore）**：不打扰自己，但对发送方表现为正常送达。
- **拒收（reject）**：明确拒绝，发送方会知道消息未被接受。

屏蔽关系保存在 `data.mv.db`，只要该文件不丢失就长期有效。

## 2. 管理员命令

| 命令 | 执行环境 | 作用 |
|---|---|---|
| `/vcx reload [config\|message\|broadcast]` | 玩家或控制台 | 热重载对应配置文件；不指定则重载全部 |
| `/vcx show-channel-info` | 玩家或控制台 | 显示所有频道、对应子服与在线玩家信息 |
| `/vcx placeholder <placeholder>` | 仅玩家（游戏内） | 演示某个占位符的渲染结果 |

注意事项：

- `reload` 只覆盖可热更新的配置；部分配置（例如涉及计时器、连接行为的项）修改后仍需重启代理端。
- `placeholder` 依赖玩家环境，控制台执行无法得到玩家相关占位符结果。
- 管理员命令需要相应的管理权限（代理端 OP 或 LuckPerms 授权），普通玩家只应拥有 `velochatx.player`。

## 3. 常见操作示例

```text
# 玩家：给 Steve 发一条跨服私聊
/vcmsg Steve 今晚八点开活动

# 玩家：屏蔽某人
/vcx ignore Griefer123

# 管理员：改完 config.yml 后热重载
/vcx reload config

# 管理员：确认频道与在线分布
/vcx show-channel-info

# 玩家：验证占位符
/vcx placeholder {server}
```
