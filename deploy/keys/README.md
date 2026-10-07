# deploy/keys — JWT 密钥目录

放置 SSO 的 RSA 密钥对（JWT RS256）：

- `private.key` —— 私钥（仅 SSO 持有，**勿提交**）
- `public.key`  —— 公钥（供网关验签）

## 来源二选一

1. **复用 waterfun 密钥**（推荐，迁移期 token 兼容）：
   复制 `D:\Project\waterfun\deploy\keys\{private,public}.key` 到本目录。

2. **重新生成**：`.\deploy\bin\gen-keys.ps1`

## 引用位置

`nitrowater-account/src/main/resources/application.yml`：

```yaml
jwt:
  private-key: file:./deploy/keys/private.key
  public-key:  file:./deploy/keys/public.key
```

（`bootRun` 的 workingDir 设为仓库根，故 `./deploy/keys` 可解析。）

密钥文件已在 `.gitignore` 中排除。
