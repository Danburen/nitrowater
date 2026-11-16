# 广播配置（定时与被动）

本文对应 `broadcast.yml`，包含两类广播：

- **定时广播**：由插件内置计时器周期性发送，适合新手引导、全服公告。
- **被动广播**：由玩家加入/离开子服或代理服触发。

## 一、定时广播

### 1. 混合广播模型

定时广播采用「混合广播」：把**全局广播**（`global`）与**区服广播**（`locale`）的消息合并，再**随机抽取**一条发送，以适配分区聊天。

- 两者都启用时：`global` 的消息排在前面，然后合并 `locale` 中匹配到的消息。
- 仅 `global` 启用：只发全服广播。
- 仅 `locale` 启用：只向匹配到的频道/子服发送。

发送间隔由 `interval` 指定，单位**秒**：

```yaml
interval: 300
```

> 广播会遍历所有子服的所有玩家，玩家规模大时建议调大间隔，降低性能开销。

### 2. 全服广播 `global`

```yaml
global:
  enable: true
  prefix: "§7[§b服务器娘§7]§r §8:§r"
  message-list:
    - "欢迎进入{proxy}，快来和大家一起冒险吧！"
    - "本服§r每日§a活跃§r玩家有机会获得丰厚奖励！"
    - "§c注意：§r请遵守服务器规则，共同创建和谐的游戏环境。"
    - "§a欢迎大家来到§7{server}§r，祝您游戏愉快！"
```

- `prefix`：该组广播的统一前缀。
- `message-list`：候选消息，支持颜色码与占位符。
- 启用后，`message-list` 会与区服广播的消息合并。

### 3. 区服广播 `locale`

```yaml
locale:
  enable: true
  broadcast-list:
    example:
      enable: true
      prefix: "[§a服务器娘§r] §8:§r"
      message-list:
        - "服务器正在§a搭建§r中，更多精彩内容即将呈现。"
        - "§6欢迎§r加入服务器官方群"
      channel-list:
        - login
      server-list:
        - lobby
    example2:
      enable: false
      prefix: "[§a服务器娘§r] §8:§r"
      message-list:
        - "§b新手村§r已开启，来吧！新玩家，开始您的冒险吧！"
      channel-list:
        - lobby
        - games
      server-list:
        - server1
```

- `broadcast-list.<name>`：一组区服广播的命名空间。
- `channel-list` / `server-list`：该组广播生效的频道与子服；命中的子服都会收到。
- `prefix`：该组广播的前缀。

边界规则：

- 同名区服广播只有第一次读取到的生效。
- 同一频道/子服命中多组广播时：`prefix` 继承**最后一组**；不同组中重复的消息会合并，**不会提高被抽中的概率**。

## 二、被动广播

### 1. 子服加入/离开 `join-leave-broadcast`

```yaml
join-leave-broadcast:
  enable: true
  log-to-console: true
  send-to-all-subServer: false
  broadcast-in-channel: true
  join-prefix: "§a(+)§r"
  leave-prefix: "§c(-)§r"
  player-join-message: "{Group}{Prefix}{Player}{Suffix} 加入了 {Server}"
  player-leave-message: "{Group}{Prefix}{Player}{Suffix} 离开了 {Server}"
```

- 触发时机：玩家在子服之间切换——对原/新子服分别发送离开与加入消息。
- `send-to-all-subServer`：是否广播到所有子服（会覆盖 `broadcast-in-channel`）。
- `broadcast-in-channel`：是否只广播到该子服所属频道。
- `log-to-console`：是否同时写入终端。

### 2. 代理服加入/离开 `join-leave-proxy-broadcast`

```yaml
join-leave-proxy-broadcast:
  enable: true
  log-to-console: true
  send-to-all-subServer: true
  join-prefix: "§a[+]§r"
  leave-prefix: "§c[-]§r"
  player-join-message: "{Group}{Prefix}{Player}{Suffix} 现在加入了 {Proxy}"
  player-leave-message: "{Group}{Prefix}{Player}{Suffix} 现在离开了 {Proxy}"
```

- 触发时机：玩家连接/断开**代理服**，与具体进入哪个子服无关。
- 文案中的占位符与主配置一致（`{Group}`、`{Prefix}`、`{Player}`、`{Suffix}`、`{Proxy}`）。

### 3. 欢迎消息 `welcome-broadcast`

```yaml
welcome-broadcast:
  enable: true
  only-first-join: false
  message: "欢迎加入§5群组§r!"
```

- `only-first-join`：仅首次加入时发送。
- 欢迎消息与加入/离开广播相互独立，可单独开关。

## 三、排障提示

- 广播不生效时，先确认 `interval` 已设置且大于 0，再确认 `enable` 为 `true`。
- 区服广播没有匹配目标时不会发送：检查 `channel-list` / `server-list` 的 key 是否与 Velocity 子服名一致。
- 修改 `broadcast.yml` 后执行 `/vcx reload broadcast`；若间隔或计时器行为异常，重启代理端。
