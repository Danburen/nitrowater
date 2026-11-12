# ============================================================
# NitroWater 上线环境自检 (PowerShell)
#
# 检查：
#   1) 读取 deploy/docker/.env（进程内已有变量优先，与 compose 语义一致）
#   2) 必需变量 / 占位值（YOUR_*_HERE、123456、change-me、dev salt ...）
#   3) 条件变量（COMMUNICATION_MODE=real、RESOURCE_BACKEND=cos）
#   4) 内容资产：词表（必须双层且非空）、文档语料
#   5) 密钥文件与 JWT issuer
#   6) 本地端口（-Local）
#
# 用法：
#   .\deploy\bin\check-env.ps1                 # Docker 上线
#   .\deploy\bin\check-env.ps1 -Local          # 本地 dev（额外检查端口）
#   .\deploy\bin\check-env.ps1 -EnvFile .\deploy\docker\.env,.\deploy\docker\.env.prod.secrets
#
# 退出码：0 = 无 FAIL；1 = 存在 FAIL
# ============================================================

[CmdletBinding()]
param(
    [switch]$Local,
    [string[]]$EnvFile
)

$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$repo = Split-Path $root -Parent

$script:results = New-Object System.Collections.ArrayList

function Add-Result {
    param([string]$Level, [string]$Name, [string]$Detail = '')
    [void]$script:results.Add([pscustomobject]@{ Level = $Level; Name = $Name; Detail = $Detail })
}

function Get-EnvValue {
    param([string]$Name)
    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

# Keys this script exported itself. A variable that came from the real process environment must
# keep winning (compose behaves the same way); among files the last one wins, so that
# `.env` + `.env.prod.secrets` composes the way `docker compose --env-file a --env-file b` does.
$script:dotenvKeys = @{}

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim() -replace '^["'']|["'']$', ''
            if ((Get-EnvValue $key) -and -not $script:dotenvKeys.ContainsKey($key)) { continue }
            [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            $script:dotenvKeys[$key] = $true
        }
    }
    return $true
}

function Count-Entries {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return -1 }
    return @(Get-Content -LiteralPath $Path | Where-Object {
            $_.Trim() -ne '' -and -not $_.Trim().StartsWith('#')
        }).Count
}

# Template placeholders (YOUR_*_HERE) mean "not configured yet". Uppercase-only on purpose:
# a real value such as `your_name@corp.com` must not trip the check.
function Test-Placeholder {
    param([string]$Value)
    if ([string]::IsNullOrEmpty($Value)) { return $false }
    return $Value -cmatch 'YOUR_[A-Z0-9_]*|_HERE$'
}

function Get-PlaceholderKeys {
    param([string]$Path)
    $keys = New-Object System.Collections.ArrayList
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return @() }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $key = $matches[1]
            if (Test-Placeholder (Get-EnvValue $key)) { [void]$keys.Add($key) }
        }
    }
    return @($keys)
}

$contentRoot = Get-EnvValue 'CONTENT_ROOT'
if ([string]::IsNullOrWhiteSpace($contentRoot)) { $contentRoot = Join-Path $root 'content' }

# Maps a container mount path (docker-compose.app.yml) back to the host dir, so the check
# validates the very file the operator is going to mount.
function Resolve-HostPath {
    param([string]$ContainerPath)
    $path = $ContainerPath -replace '^file:', ''
    $map = @(
        @{ Prefix = '/etc/nitrowater/moderation'; Host = (Join-Path $contentRoot 'moderation') },
        @{ Prefix = '/etc/nitrowater/docs'; Host = (Join-Path $contentRoot 'docs') }
    )
    foreach ($entry in $map) {
        if ($path.StartsWith($entry.Prefix)) {
            return ($path -replace [regex]::Escape($entry.Prefix), $entry.Host) -replace '/', '\'
        }
    }
    # Local dev passes repo-relative paths (bootRun pins the working dir to the repo root);
    # absolute paths are used as-is.
    if ($path -match '^[A-Za-z]:[\\/]|^\\\\|^/') { return $path }
    return (Join-Path $repo $path)
}

Write-Host '=== NitroWater 上线环境自检 ===' -ForegroundColor White
Write-Host ("mode: {0}" -f $(if ($Local) { 'local (dev)' } else { 'docker (prod)' })) -ForegroundColor DarkGray
Write-Host ''

# ---------- 1. .env ----------
# Deploy keeps non-secret values in .env and secrets in .env.prod.secrets; compose is given both
# (--env-file a --env-file b), so this gate has to read the same pair in the same order.
$envFiles = @(if ($EnvFile) { $EnvFile } else {
        @((Join-Path $root 'docker\.env'), (Join-Path $root 'docker\.env.prod.secrets'))
    })
$resolvedEnv = @($envFiles | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
$loadedEnv = @($envFiles | Where-Object { Import-DotEnv $_ })
if ($loadedEnv.Count -gt 0) {
    Add-Result 'INFO' '.env' ("已加载 " + ($loadedEnv -join ' + '))
} else {
    Add-Result 'WARN' '.env' ("未找到 " + ($envFiles -join ' / ') + "（仅用进程环境变量；Docker 上线应存在）")
}

# ---------- 2. 必需变量 / 占位值 ----------
# Template placeholders (YOUR_*_HERE) must block: copying .env.example verbatim used to
# yield "Fail: 0" because the per-variable rules only knew about empty values and known weak
# defaults (assessment M3).
$placeholders = @($resolvedEnv | ForEach-Object { Get-PlaceholderKeys $_ } | Select-Object -Unique)
if ($placeholders.Count -gt 0) {
    Add-Result 'FAIL' 'placeholders' ('仍是模板占位值，需换成真实值：' + ($placeholders -join ', '))
} else {
    Add-Result 'PASS' 'placeholders' '未发现 YOUR_*_HERE 占位值'
}

# ---------- 2.1 compose → .env 覆盖 ----------
# The hand-written rules above only cover keys someone remembered to add here. This section derives
# the set from the compose files instead: a key whose fallback is harmless (URL, port, number) may
# stay unset, but one that carries a credential or a dev default must be filled in by hand —
# otherwise "复制模板 → 改几个字 → 上线" silently runs on 123456 / root / change-me.
# Two severities on purpose: "没给值" is a FAIL (the deploy cannot be safe), "给了但仍是 dev 值" is
# a WARN (a deliberate single-tenant choice must stay possible).
$devDefaultValues = @('123456', 'change-me', 'device-salt-dev', 'nitrowater-bff-secret', 'root', 'dev', 'not-configured')

$composeDir = Join-Path $root 'docker'
$composeKeys = @{}    # keys declared in an environment: block (what actually enters a container)
$referenced = @{}     # every ${VAR} interpolated anywhere, including binds/healthchecks
$unfilled = New-Object System.Collections.ArrayList
$devDefaulted = New-Object System.Collections.ArrayList
$fromEnv = 0
$fromDefault = 0
$literalValues = 0
foreach ($composeFile in (Get-ChildItem -LiteralPath $composeDir -Filter 'docker-compose.*.yml' | Sort-Object Name)) {
    $text = Get-Content -LiteralPath $composeFile.FullName -Raw
    foreach ($m in [regex]::Matches($text, '\$\{(?<name>[A-Za-z_][A-Za-z0-9_]*)')) {
        $referenced[$m.Groups['name'].Value] = $true
    }
    foreach ($line in ($text -split "`n")) {
        if ($line -cnotmatch '^      (?<key>[A-Z][A-Z0-9_]*):\s*(?<raw>.*)$') { continue }
        $key = $matches['key']
        $raw = $matches['raw'].Trim()
        if ($composeKeys.ContainsKey($key)) { continue }
        $composeKeys[$key] = $raw
        if ($raw -notmatch '\$\{') { $literalValues++; continue }

        # Single `${VAR...}` expression: the value that matters is VAR, not the compose key name.
        if ($raw -match '^\$\{(?<var>[A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?\}$') {
            $var = $matches['var']
            $value = Get-EnvValue $var
            if (-not [string]::IsNullOrEmpty($value)) {
                # Placeholders are the `placeholders` check's business; only real dev values here.
                if (-not (Test-Placeholder $value) -and ($devDefaultValues -ccontains $value.Trim())) {
                    [void]$devDefaulted.Add("$var=$value")
                } else {
                    $fromEnv++
                }
            } elseif ($raw -match ':\?' -or $raw -match '^\$\{[A-Za-z_][A-Za-z0-9_]*\}$') {
                [void]$unfilled.Add($var)                                   # no default at all
            } elseif ($raw -match '(?i)(123456|change-me|device-salt-dev|bff-secret|not-configured|:-root)') {
                [void]$unfilled.Add($var)                                   # unsafe built-in fallback
            } else {
                $fromDefault++
            }
            continue
        }

        # Mixed expression (URLs, healthcheck command): only an explicit required marker blocks.
        if ($raw -match ':\?' -and [string]::IsNullOrEmpty((Get-EnvValue $key))) {
            [void]$unfilled.Add($key)
        } else {
            $fromDefault++
        }
    }
}
$unfilled = @($unfilled | Select-Object -Unique)
$devDefaulted = @($devDefaulted | Select-Object -Unique)
# The compose inventory only describes the Docker path; local dev talks to the host MySQL/Redis
# with its own (root) credentials, so reporting it there would be pure noise.
if ($Local) {
    Add-Result 'INFO' 'compose-coverage' '本地 dev 跳过（仅 Docker 上线检查 compose 覆盖）'
} elseif ($unfilled.Count -gt 0) {
    Add-Result 'FAIL' 'compose-coverage' ('.env 缺这些值，compose 会回落到空值或 dev 默认值：' + ($unfilled -join ', '))
} else {
    Add-Result 'PASS' 'compose-coverage' ("compose 变量都已覆盖（.env $fromEnv / 安全默认 $fromDefault / 字面量 $literalValues）")
}
if ($Local) {
    Add-Result 'INFO' 'compose-defaults' '本地 dev 跳过'
} elseif ($devDefaulted.Count -gt 0) {
    Add-Result 'WARN' 'compose-defaults' ('仍是 dev 默认值（单机可接受，公网必须换）：' + ($devDefaulted -join ', '))
} else {
    Add-Result 'PASS' 'compose-defaults' '没有使用 dev 默认值'
}

# The reverse direction: a key written in .env that no compose file interpolates never reaches a
# container — "配了但没生效" is worse than "没配", because it looks configured.
$envOnly = New-Object System.Collections.ArrayList
foreach ($envPath in $resolvedEnv) {
    foreach ($line in Get-Content -LiteralPath $envPath) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        if ($line -match '^\s*(?<name>[A-Za-z_][A-Za-z0-9_]*)\s*=') {
            $key = $matches['name']
            if (-not $referenced.ContainsKey($key) -and -not $envOnly.Contains($key)) { [void]$envOnly.Add($key) }
        }
    }
}
if ($Local) {
    Add-Result 'INFO' '.env-unused' '本地 dev 跳过'
} elseif ($envOnly.Count -gt 0) {
    Add-Result 'WARN' '.env-unused' ('这些键没有任何 compose 引用，不会进容器：' + ($envOnly -join ', '))
} else {
    Add-Result 'PASS' '.env-unused' '.env 的键都被 compose 引用'
}

$kek = Get-EnvValue 'NITROWATER_KEK'
if ([string]::IsNullOrEmpty($kek)) {
    Add-Result 'FAIL' 'NITROWATER_KEK' '未设置（account 启动失败）'
} elseif ($kek.Length -lt 40) {
    Add-Result 'WARN' 'NITROWATER_KEK' "长度 $($kek.Length)，32 字节 Base64 应为 44"
} else {
    Add-Result 'PASS' 'NITROWATER_KEK' "len=$($kek.Length)"
}

$devSalt = $null
$accountYml = Join-Path $repo 'nitrowater-account\src\main\resources\application.yml'
if (Test-Path -LiteralPath $accountYml) {
    $m = Select-String -LiteralPath $accountYml -Pattern 'DEVICE_SALT:([^}]+)\}' | Select-Object -First 1
    if ($m) { $devSalt = $m.Matches[0].Groups[1].Value.Trim() }
}
$deviceSalt = Get-EnvValue 'DEVICE_SALT'
if ([string]::IsNullOrEmpty($deviceSalt)) {
    Add-Result 'FAIL' 'DEVICE_SALT' '未设置（会用 dev 默认值，设备指纹可被伪造）'
} elseif ($devSalt -and $deviceSalt -eq $devSalt) {
    Add-Result 'FAIL' 'DEVICE_SALT' '仍是 dev 默认值，生产必须换新随机值'
} else {
    Add-Result 'PASS' 'DEVICE_SALT' '已覆盖 dev 默认值'
}

$issuer = Get-EnvValue 'JWT_ISSUER'
if ([string]::IsNullOrEmpty($issuer)) {
    Add-Result 'FAIL' 'JWT_ISSUER' '未设置（必须是 URL）'
} elseif ($issuer -notmatch '^https?://') {
    Add-Result 'FAIL' 'JWT_ISSUER' "不是 URL：$issuer"
} elseif (-not $Local -and $issuer -match 'localhost|127\.0\.0\.1') {
    Add-Result 'WARN' 'JWT_ISSUER' "生产仍指向本机：$issuer"
} else {
    Add-Result 'PASS' 'JWT_ISSUER' $issuer
}

$dbPassword = Get-EnvValue 'DB_PASSWORD'
if ([string]::IsNullOrEmpty($dbPassword)) {
    Add-Result 'FAIL' 'DB_PASSWORD' '未设置'
} elseif (-not $Local -and $dbPassword -eq '123456') {
    Add-Result 'FAIL' 'DB_PASSWORD' '仍是 dev 默认值 123456'
} else {
    Add-Result 'PASS' 'DB_PASSWORD' '已设置'
}

# The app account is created by hand once (deploy/README.md §6); root is bootstrap-only. A pool
# credential that owns every schema turns any SQL injection into a full-database compromise.
$dbUser = Get-EnvValue 'DB_USERNAME'
if ($Local) {
    Add-Result 'INFO' 'DB_USERNAME' '本地 dev 用宿主机账号（Docker 上线必须是专用账号）'
} elseif ([string]::IsNullOrEmpty($dbUser)) {
    Add-Result 'FAIL' 'DB_USERNAME' '未设置（业务连库账号，见 deploy/README.md §6）'
} elseif ($dbUser.Trim() -eq 'root') {
    Add-Result 'FAIL' 'DB_USERNAME' 'root：改用 README §6 建的专用账号'
} elseif (Test-Placeholder $dbUser) {
    Add-Result 'FAIL' 'DB_USERNAME' '仍是模板占位值'
} elseif ($dbUser -notmatch '^[A-Za-z0-9_]{1,32}$') {
    Add-Result 'FAIL' 'DB_USERNAME' "只能是 [A-Za-z0-9_]：$dbUser"
} else {
    Add-Result 'PASS' 'DB_USERNAME' $dbUser
}

# MySQL root password is only used by the one-time manual bootstrap and by admin work.
$rootPassword = Get-EnvValue 'MYSQL_ROOT_PASSWORD'
if ($Local) {
    Add-Result 'INFO' 'MYSQL_ROOT_PASSWORD' '本地 dev 不跑 infra 容器，跳过'
} elseif ([string]::IsNullOrEmpty($rootPassword)) {
    Add-Result 'FAIL' 'MYSQL_ROOT_PASSWORD' '未设置（infra 初始化 MySQL 需要）'
} elseif (Test-Placeholder $rootPassword) {
    Add-Result 'FAIL' 'MYSQL_ROOT_PASSWORD' '仍是模板占位值'
} elseif ($rootPassword -eq '123456') {
    Add-Result 'FAIL' 'MYSQL_ROOT_PASSWORD' '仍是 dev 默认值 123456'
} elseif ($rootPassword -eq $dbPassword) {
    Add-Result 'WARN' 'MYSQL_ROOT_PASSWORD' '与 DB_PASSWORD 相同（建议分成两个）'
} else {
    Add-Result 'PASS' 'MYSQL_ROOT_PASSWORD' '已设置'
}

$bffSecret = Get-EnvValue 'BFF_CLIENT_SECRET'
if ([string]::IsNullOrEmpty($bffSecret)) {
    Add-Result 'WARN' 'BFF_CLIENT_SECRET' '未设置（用 dev 默认值）'
} elseif (-not $Local -and $bffSecret -eq 'nitrowater-bff-secret') {
    Add-Result 'FAIL' 'BFF_CLIENT_SECRET' '仍是 dev 默认值'
} else {
    Add-Result 'PASS' 'BFF_CLIENT_SECRET' '已设置'
}

# Redis holds BFF sessions (access + refresh tokens) and the account sessions, and it shares
# the compose network with Piston. Running it without AUTH in production is not acceptable.
$redisPassword = Get-EnvValue 'REDIS_PASSWORD'
if ([string]::IsNullOrEmpty($redisPassword)) {
    if ($Local) {
        Add-Result 'INFO' 'REDIS_PASSWORD' '未设置（本地 dev 允许无密码 Redis）'
    } else {
        Add-Result 'FAIL' 'REDIS_PASSWORD' '未设置：会话/令牌无认证，且与 Piston 同网段（评估 M4）'
    }
} elseif (Test-Placeholder $redisPassword) {
    Add-Result 'FAIL' 'REDIS_PASSWORD' '仍是模板占位值'
} else {
    Add-Result 'PASS' 'REDIS_PASSWORD' '已设置'
}

# SessionEpochFilter drops sessions whose epoch differs from the current one; a constant
# "dev" means redeploys never invalidate old sessions (assessment H5).
$sessionEpoch = Get-EnvValue 'APP_SESSION_EPOCH'
if ($Local) {
    Add-Result 'INFO' 'APP_SESSION_EPOCH' ("本地 dev 使用默认 epoch={0}" -f $(if ($sessionEpoch) { $sessionEpoch } else { 'dev' }))
} elseif ([string]::IsNullOrEmpty($sessionEpoch)) {
    Add-Result 'FAIL' 'APP_SESSION_EPOCH' '未设置：恒为 dev，发布不会失效旧会话'
} elseif ($sessionEpoch -eq 'dev') {
    Add-Result 'FAIL' 'APP_SESSION_EPOCH' '仍是 dev 默认值，每次发布应写入新值'
} elseif (Test-Placeholder $sessionEpoch) {
    Add-Result 'FAIL' 'APP_SESSION_EPOCH' '仍是模板占位值'
} else {
    Add-Result 'PASS' 'APP_SESSION_EPOCH' $sessionEpoch
}

$backend = Get-EnvValue 'RESOURCE_BACKEND'
if ([string]::IsNullOrEmpty($backend)) { $backend = 's3' }
if ($backend -eq 's3') {
    foreach ($name in 'S3_ACCESS_KEY', 'S3_SECRET_KEY', 'S3_BUCKET') {
        $value = Get-EnvValue $name
        if ([string]::IsNullOrEmpty($value) -or $value -eq 'change-me') {
            Add-Result 'FAIL' $name 'S3 后端必填且不能是占位值'
        } else {
            Add-Result 'PASS' $name $value
        }
    }
}

# ---------- 3. 条件变量 ----------
$mode = Get-EnvValue 'COMMUNICATION_MODE'
if ([string]::IsNullOrEmpty($mode)) { $mode = 'mock' }
if ($mode -eq 'real') {
    foreach ($name in 'MAIL_HOST', 'MAIL_PORT', 'MAIL_USERNAME', 'MAIL_PASSWORD', 'MAIL_FROM',
        'ALIBABA_CLOUD_ACCESS_KEY_ID', 'ALIBABA_CLOUD_ACCESS_KEY_SECRET', 'ALIYUN_SMS_SIGN_NAME',
        'ALIYUN_SMS_TEMPLATE_CODE') {
        $value = Get-EnvValue $name
        if ([string]::IsNullOrEmpty($value)) {
            Add-Result 'FAIL' $name 'COMMUNICATION_MODE=real 时必填'
        } else {
            Add-Result 'PASS' $name '已设置'
        }
    }
} else {
    Add-Result 'INFO' 'COMMUNICATION_MODE' 'mock：验证码只写日志（生产建议 real）'
}

if ($backend -eq 'cos') {
    foreach ($name in 'COS_SECRET_ID', 'COS_SECRET_KEY', 'COS_REGION', 'COS_BUCKET') {
        $value = Get-EnvValue $name
        if ([string]::IsNullOrEmpty($value)) {
            Add-Result 'FAIL' $name 'RESOURCE_BACKEND=cos 时必填'
        } else {
            Add-Result 'PASS' $name '已设置'
        }
    }
}

# ---------- 4. AI 凭据 ----------
$chatKey = Get-EnvValue 'AI_CHAT_API_KEY'
if ([string]::IsNullOrEmpty($chatKey)) { $chatKey = Get-EnvValue 'DEEPSEEK_API_KEY' }
if ([string]::IsNullOrEmpty($chatKey)) {
    Add-Result 'FAIL' 'AI_CHAT_API_KEY' '未设置（也未见 DEEPSEEK_API_KEY），AI 接口不可用'
} else {
    Add-Result 'PASS' 'AI_CHAT_API_KEY' '已设置（或回落到 DEEPSEEK_API_KEY）'
}

$embeddingKey = Get-EnvValue 'AI_EMBEDDING_API_KEY'
if ([string]::IsNullOrEmpty($embeddingKey)) { $embeddingKey = Get-EnvValue 'SILICONFLOW_API_KEY' }
if ([string]::IsNullOrEmpty($embeddingKey)) {
    Add-Result 'FAIL' 'AI_EMBEDDING_API_KEY' '未设置（也未见 SILICONFLOW_API_KEY），索引无法重建'
} else {
    Add-Result 'PASS' 'AI_EMBEDDING_API_KEY' '已设置（或回落到 SILICONFLOW_API_KEY）'
}

$dims = Get-EnvValue 'AI_EMBEDDING_DIMENSIONS'
$collection = Get-EnvValue 'AI_COLLECTION'
if ([string]::IsNullOrEmpty($collection)) { $collection = 'nitrowater_velochatx_docs' }
if ($dims -and $dims -ne '1024' -and $collection -eq 'nitrowater_velochatx_docs') {
    Add-Result 'WARN' 'AI_EMBEDDING_DIMENSIONS' "维度 $dims 与现有集合不匹配，请换 AI_COLLECTION 后重建"
} elseif ($dims) {
    Add-Result 'PASS' 'AI_EMBEDDING_DIMENSIONS' $dims
} else {
    Add-Result 'PASS' 'AI_EMBEDDING_DIMENSIONS' '1024（默认）'
}

# ---------- 5. 内容资产 ----------
$dict = Get-EnvValue 'AI_MODERATION_DICT'
if ([string]::IsNullOrEmpty($dict)) { $dict = 'classpath:ai/moderation/words.txt' }
$dictLocations = @($dict -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

$hasBaseline = @($dictLocations | Where-Object { $_ -like 'classpath:*' }).Count -gt 0
if (-not $hasBaseline) {
    Add-Result 'FAIL' 'AI_MODERATION_DICT' '缺少 classpath 基线层：挂载丢失会让闸门静默失效（words=0）'
} else {
    Add-Result 'PASS' 'AI_MODERATION_DICT' '含 classpath 基线层'
}

$checkedWordsFile = $false
foreach ($location in $dictLocations) {
    if ($location -like 'classpath:*') { continue }
    $hostPath = Resolve-HostPath $location
    if (-not $hostPath) {
        Add-Result 'WARN' 'AI_MODERATION_DICT' "无法映射到宿主机路径：$location"
        continue
    }
    $count = Count-Entries $hostPath
    if ($count -lt 0) {
        Add-Result 'FAIL' 'words.txt' "文件不存在：$hostPath"
    } elseif ($count -eq 0) {
        Add-Result 'FAIL' 'words.txt' "去注释后为空：$hostPath（闸门等于关闭）"
    } else {
        Add-Result 'PASS' 'words.txt' "$count 条（$hostPath）"
    }
    $checkedWordsFile = $true
}
if (-not $checkedWordsFile) {
    $baseline = Join-Path $repo 'nitrowater-server\src\main\resources\ai\moderation\words.txt'
    $count = Count-Entries $baseline
    if ($count -gt 0) {
        Add-Result 'PASS' 'words.txt' "classpath 基线 $count 条（未挂载增量词表）"
    } else {
        Add-Result 'FAIL' 'words.txt' "classpath 基线为空：$baseline"
    }
}

$allow = Get-EnvValue 'AI_MODERATION_ALLOW'
if ($allow) {
    foreach ($location in ($allow -split ',' | ForEach-Object { $_.Trim() })) {
        if ($location -like 'classpath:*') { continue }
        $hostPath = Resolve-HostPath $location
        if (-not $hostPath) { continue }
        if (Test-Path -LiteralPath $hostPath -PathType Leaf) {
            Add-Result 'PASS' 'allow.txt' (Resolve-Path -LiteralPath $hostPath).Path
        } else {
            Add-Result 'INFO' 'allow.txt' "未提供（可选）：$hostPath"
        }
    }
}

$docsLocation = Get-EnvValue 'AI_DOCS_LOCATION'
if ([string]::IsNullOrEmpty($docsLocation)) {
    Add-Result 'INFO' 'AI_DOCS_LOCATION' '未设置：文档语料取镜像内 classpath'
} elseif ($docsLocation -like 'classpath:*') {
    Add-Result 'INFO' 'AI_DOCS_LOCATION' "classpath 语料：$docsLocation"
} else {
    $hostPath = Resolve-HostPath $docsLocation
    if (-not $hostPath) {
        Add-Result 'WARN' 'AI_DOCS_LOCATION' "无法映射到宿主机路径：$docsLocation"
    } elseif (-not (Test-Path -LiteralPath $hostPath -PathType Container)) {
        Add-Result 'FAIL' 'AI_DOCS_LOCATION' "目录不存在：$hostPath"
    } else {
        $md = @(Get-ChildItem -LiteralPath $hostPath -Recurse -Filter '*.md' -File -ErrorAction SilentlyContinue)
        if ($md.Count -eq 0) {
            Add-Result 'FAIL' 'AI_DOCS_LOCATION' "目录内没有 .md：$hostPath（该 corpus 重建后会从索引消失）"
        } else {
            Add-Result 'PASS' 'AI_DOCS_LOCATION' "$($md.Count) 个 md（$hostPath）"
        }
    }
}

$skillsDir = Get-EnvValue 'AI_SKILLS_DIR'
if ($skillsDir) {
    Add-Result 'INFO' 'AI_SKILLS_DIR' '已废弃（服务不再读取），可从 .env 删除'
}

# ---------- 6. 密钥 ----------
foreach ($file in 'private.key', 'public.key') {
    $path = Join-Path $root "keys\$file"
    if (Test-Path -LiteralPath $path) {
        Add-Result 'PASS' $file (Resolve-Path -LiteralPath $path).Path
    } else {
        Add-Result 'FAIL' $file "缺失：$path（运行 deploy\bin\gen-keys.ps1）"
    }
}

# ---------- 7. 边缘 / 暴露面 ----------
$bind = Get-EnvValue 'PUBLISH_BIND'
if ([string]::IsNullOrEmpty($bind)) { $bind = '127.0.0.1' }
$opsBind = Get-EnvValue 'OPS_BIND'
if ([string]::IsNullOrEmpty($opsBind)) { $opsBind = '127.0.0.1' }

if ($bind -eq '0.0.0.0' -and -not $Local) {
    Add-Result 'WARN' 'PUBLISH_BIND' '0.0.0.0：业务端口对整个网段开放（生产应保持 127.0.0.1 + 边缘层）'
} else {
    Add-Result 'PASS' 'PUBLISH_BIND' $bind
}
if ($opsBind -eq '0.0.0.0' -and -not $Local) {
    Add-Result 'FAIL' 'OPS_BIND' '0.0.0.0：Qdrant / SeaweedFS 管理面无认证，不能对网络开放'
} else {
    Add-Result 'PASS' 'OPS_BIND' $opsBind
}

if (-not $Local) {
    $webDomain = Get-EnvValue 'WEB_DOMAIN'
    $authDomain = Get-EnvValue 'AUTH_DOMAIN'
    $sslEmail = Get-EnvValue 'SSL_EMAIL'
    if (-not $webDomain -and -not $authDomain -and -not $sslEmail) {
        Add-Result 'WARN' 'edge' '未配置 WEB_DOMAIN / AUTH_DOMAIN / SSL_EMAIL：无公网入口（未叠加 edge）'
    } else {
        $missing = @()
        if (-not $webDomain) { $missing += 'WEB_DOMAIN' }
        if (-not $authDomain) { $missing += 'AUTH_DOMAIN' }
        if (-not $sslEmail) { $missing += 'SSL_EMAIL' }
        if ($missing.Count -gt 0) {
            Add-Result 'FAIL' 'edge' ('缺：' + ($missing -join ', ') + '（叠加 edge 时必须齐全）')
        } else {
            Add-Result 'PASS' 'edge' "web=$webDomain auth=$authDomain"
        }
        if ($sslEmail -and -not ($sslEmail -match '^[^@\s]+@[^@\s]+\.[^@\s]+$')) {
            Add-Result 'FAIL' 'SSL_EMAIL' "不是有效邮箱：$sslEmail"
        }
        if ((Get-EnvValue 'SESSION_COOKIE_SECURE') -ne 'true') {
            Add-Result 'WARN' 'SESSION_COOKIE_SECURE' 'HTTPS 部署应为 true（否则会话 cookie 仍可走 http）'
        } else {
            Add-Result 'PASS' 'SESSION_COOKIE_SECURE' 'true'
        }
    }
    $userUpload = Get-EnvValue 'RESOURCE_USER_UPLOAD'
    if ($userUpload -ne 'false') {
        Add-Result 'WARN' 'RESOURCE_USER_UPLOAD' '用户上传仍开启：需审核的内容尚未上线，生产建议 false（接口保留，返回 503）'
    } else {
        Add-Result 'PASS' 'RESOURCE_USER_UPLOAD' 'false（仅保留接口）'
    }
    $redirects = Get-EnvValue 'BFF_REDIRECT_URIS'
    if ($webDomain) {
        $expected = "https://$webDomain/login/oauth2/code/nitrowater"
        if ([string]::IsNullOrEmpty($redirects)) {
            Add-Result 'WARN' 'BFF_REDIRECT_URIS' "未设置：默认值只含 localhost，登录回调会失败（应含 $expected）"
        } elseif ($redirects -notlike '*https://*') {
            Add-Result 'FAIL' 'BFF_REDIRECT_URIS' '生产回调地址必须是 https'
        } elseif ($redirects -notlike "*$webDomain*") {
            Add-Result 'WARN' 'BFF_REDIRECT_URIS' "未包含生产域名 $webDomain"
        } else {
            Add-Result 'PASS' 'BFF_REDIRECT_URIS' "含 $webDomain"
        }
        if ([string]::IsNullOrEmpty((Get-EnvValue 'SSO_DEFAULT_REDIRECT'))) {
            Add-Result 'WARN' 'SSO_DEFAULT_REDIRECT' '未设置：登录后默认跳 localhost:5173'
        } else {
            Add-Result 'PASS' 'SSO_DEFAULT_REDIRECT' (Get-EnvValue 'SSO_DEFAULT_REDIRECT')
        }
    }
    if ($ISSUER -like 'http://*' -and $ISSUER -notlike 'http://localhost*') {
        Add-Result 'WARN' 'JWT_ISSUER' "生产建议使用 https：$ISSUER"
    }
}

# ---------- 8. 本地端口 ----------
if ($Local) {
    foreach ($entry in @(
            @{ Name = 'MySQL'; Port = 3306 },
            @{ Name = 'Redis'; Port = 6379 },
            @{ Name = 'SeaweedFS'; Port = 8333 },
            @{ Name = 'Qdrant'; Port = 6333 })) {
        $ok = (Test-NetConnection -ComputerName 127.0.0.1 -Port $entry.Port -WarningAction SilentlyContinue).TcpTestSucceeded
        if ($ok) {
            Add-Result 'PASS' $entry.Name "127.0.0.1:$($entry.Port)"
        } else {
            Add-Result 'FAIL' $entry.Name "127.0.0.1:$($entry.Port) 不可达"
        }
    }
}

# ---------- 9. 输出 ----------
$color = @{ PASS = 'Green'; WARN = 'Yellow'; FAIL = 'Red'; INFO = 'DarkGray' }
Write-Host 'Result:' -ForegroundColor White
Write-Host ('=' * 68) -ForegroundColor DarkGray
foreach ($r in $script:results) {
    $line = "  [{0,-4}] {1}" -f $r.Level, $r.Name
    if ($r.Detail) { $line += " -- $($r.Detail)" }
    Write-Host $line -ForegroundColor $color[$r.Level]
}
Write-Host ('=' * 68) -ForegroundColor DarkGray

$pass = @($script:results | Where-Object Level -eq 'PASS').Count
$warn = @($script:results | Where-Object Level -eq 'WARN').Count
$fail = @($script:results | Where-Object Level -eq 'FAIL').Count

Write-Host ''
Write-Host "Pass: $pass | Warn: $warn | Fail: $fail" -ForegroundColor White
if ($fail -gt 0) {
    Write-Host ''
    Write-Host '  [FAIL] 存在阻断项，修好再上线。变量与拓扑见 deploy/README.md' -ForegroundColor Red
    Write-Host '         内容挂载见 deploy/content/README.md' -ForegroundColor Red
    exit 1
}
Write-Host '  [OK] 无阻断项。' -ForegroundColor Green
if ($warn -gt 0) {
    Write-Host '  [WARN] 有警告项，确认可接受后再上线。' -ForegroundColor Yellow
}
exit 0
