# ============================================================
# NitroWater 上线环境自检 (PowerShell)
#
# 检查：
#   1) 读取 deploy/docker/.env（进程内已有变量优先，与 compose 语义一致）
#   2) 必需变量 / 占位值（123456、change-me、dev salt ...）
#   3) 条件变量（COMMUNICATION_MODE=real、RESOURCE_BACKEND=cos）
#   4) 内容资产：词表（必须双层且非空）、文档语料、外部 skills
#   5) 密钥文件与 JWT issuer
#   6) 本地端口（-Local）
#
# 用法：
#   .\deploy\bin\check-env.ps1                 # Docker 上线
#   .\deploy\bin\check-env.ps1 -Local          # 本地 dev（额外检查端口）
#   .\deploy\bin\check-env.ps1 -EnvFile .\deploy\docker\.env
#
# 退出码：0 = 无 FAIL；1 = 存在 FAIL
# ============================================================

[CmdletBinding()]
param(
    [switch]$Local,
    [string]$EnvFile
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

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line.Trim() -eq '' -or $line.Trim().StartsWith('#')) { continue }
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            $key = $matches[1]
            $value = $matches[2].Trim() -replace '^["'']|["'']$', ''
            if (-not (Get-EnvValue $key)) {
                [Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
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

$contentRoot = Get-EnvValue 'CONTENT_ROOT'
if ([string]::IsNullOrWhiteSpace($contentRoot)) { $contentRoot = Join-Path $root 'content' }

# Maps a container mount path (docker-compose.content.yml) back to the host dir, so the check
# validates the very file the operator is going to mount.
function Resolve-HostPath {
    param([string]$ContainerPath)
    $path = $ContainerPath -replace '^file:', ''
    $map = @(
        @{ Prefix = '/etc/nitrowater/moderation'; Host = (Join-Path $contentRoot 'moderation') },
        @{ Prefix = '/etc/nitrowater/docs'; Host = (Join-Path $contentRoot 'docs') },
        @{ Prefix = '/etc/nitrowater/skills'; Host = (Join-Path $contentRoot 'skills') }
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
$resolvedEnv = if ($EnvFile) { $EnvFile } else { Join-Path $root 'docker\.env' }
if (Import-DotEnv $resolvedEnv) {
    Add-Result 'INFO' '.env' "已加载 $resolvedEnv"
} else {
    Add-Result 'WARN' '.env' "未找到 $resolvedEnv（仅用进程环境变量；Docker 上线应存在）"
}

# ---------- 2. 必需变量 / 占位值 ----------
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

$bffSecret = Get-EnvValue 'BFF_CLIENT_SECRET'
if ([string]::IsNullOrEmpty($bffSecret)) {
    Add-Result 'WARN' 'BFF_CLIENT_SECRET' '未设置（用 dev 默认值）'
} elseif (-not $Local -and $bffSecret -eq 'nitrowater-bff-secret') {
    Add-Result 'FAIL' 'BFF_CLIENT_SECRET' '仍是 dev 默认值'
} else {
    Add-Result 'PASS' 'BFF_CLIENT_SECRET' '已设置'
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
        'ALIYUN_SMS_ACCESS_KEY_ID', 'ALIYUN_SMS_ACCESS_KEY_SECRET', 'ALIYUN_SMS_SIGN_NAME',
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
    $hostPath = Resolve-HostPath $skillsDir
    if ($hostPath -and (Test-Path -LiteralPath $hostPath -PathType Container)) {
        $skills = @(Get-ChildItem -LiteralPath $hostPath -Recurse -Filter 'SKILL.md' -File -ErrorAction SilentlyContinue)
        Add-Result 'INFO' 'AI_SKILLS_DIR' "$($skills.Count) 个外部技能（$hostPath）"
    } elseif ($hostPath) {
        Add-Result 'WARN' 'AI_SKILLS_DIR' "目录不存在：$hostPath（内置技能仍可用）"
    }
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
    Write-Host '  [FAIL] 存在阻断项，修好再上线。变量含义见 deploy/env-vars-reference.md' -ForegroundColor Red
    Write-Host '         内容挂载见 deploy/content/README.md' -ForegroundColor Red
    exit 1
}
Write-Host '  [OK] 无阻断项。' -ForegroundColor Green
if ($warn -gt 0) {
    Write-Host '  [WARN] 有警告项，确认可接受后再上线。' -ForegroundColor Yellow
}
exit 0
