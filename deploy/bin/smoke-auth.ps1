# ============================================================
# smoke-auth.ps1 — SSO Phase 1 端到端冒烟（交互式）
# 覆盖：captcha -> send-code -> register -> /account -> refresh -> logout
#
# 前置：
#   - account 已启动（http://localhost:8090）
#   - MySQL / Redis 正常，JWT 密钥在 deploy/keys/，WATERFUN_KEK 已设
#   - 验证码为图形码，需人工识别；短信验证码由 ConsoleSmsCodeSender 打到 account 控制台
#
# 用法：
#   .\deploy\bin\smoke-auth.ps1
#   .\deploy\bin\smoke-auth.ps1 -Base http://localhost:8090 -Phone 13800000001 -Username smokeuser1
# ============================================================

param(
    [string]$Base = 'http://localhost:8090',
    [string]$Phone = '13800000001',
    [string]$Username = 'smokeuser1',
    [string]$Password = 'Passw0rd!',
    [string]$DeviceFp = 'smoketestdevice0001'
)

$ErrorActionPreference = 'Stop'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$captchaFile = Join-Path $env:TEMP 'nitrowater_captcha.png'

function Step($n, $msg) { Write-Host "`n=== [$n] $msg ===" -ForegroundColor Cyan }
function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    Remove-Item $captchaFile -Force -ErrorAction SilentlyContinue
    exit 1
}
function Ok($msg)   { Write-Host "[OK] $msg" -ForegroundColor Green }

# ---------- 1. captcha ----------
Step 1 'GET /api/auth/captcha'
try {
    Invoke-WebRequest -Uri "$Base/api/auth/captcha" -WebSession $session -OutFile $captchaFile | Out-Null
} catch { Fail "captcha request error: $_" }
Ok "captcha saved: $captchaFile"
Start-Process $captchaFile   # 自动打开图片
$captcha = Read-Host '请输入图片中的验证码'

# ---------- 2. send-code ----------
Step 2 'POST /api/auth/send-code (SMS / REGISTER)'
$sendBody = @{
    target    = $Phone
    channel   = 'SMS'
    scene     = 'REGISTER'
    captcha   = $captcha
    deviceFp  = $DeviceFp
    deviceInfo = @{ deviceFp = $DeviceFp; os = 'Windows'; browser = 'script' }
} | ConvertTo-Json -Depth 5
try {
    Invoke-RestMethod -Uri "$Base/api/auth/send-code" -Method Post -ContentType 'application/json' `
        -Body $sendBody -WebSession $session | Out-Null
} catch { Fail "send-code error: $_" }
Ok 'send-code 200（验证码已打印在 account 控制台，形如 [ConsoleCodeSender][SMS] ... code=123456）'
$code = Read-Host '请输入 account 控制台打印的短信验证码'

# ---------- 3. register ----------
Step 3 'POST /api/auth/register'
$regBody = @{
    phone    = $Phone
    username = $Username
    password = $Password
    verify   = @{
        channel    = 'SMS'
        target     = $Phone
        code       = $code
        scene      = 'REGISTER'
        deviceInfo = @{ deviceFp = $DeviceFp; os = 'Windows'; browser = 'script' }
    }
} | ConvertTo-Json -Depth 6
try {
    $reg = Invoke-RestMethod -Uri "$Base/api/auth/register" -Method Post -ContentType 'application/json' `
        -Body $regBody -WebSession $session
} catch { Fail "register error: $_" }
$at = $reg.data.accessToken
if (-not $at) { Fail "register 未返回 accessToken: $($reg | ConvertTo-Json -Compress)" }
Ok "register 成功，accessToken 长度=$($at.Length)；REFRESH_TOKEN cookie 已写入会话"

$authHeader = @{ Authorization = "Bearer $at" }

# ---------- 4. /account (需要 AT) ----------
Step 4 'GET /api/auth/account  (Authorization: Bearer AT)'
try {
    $acct = Invoke-RestMethod -Uri "$Base/api/auth/account" -Headers $authHeader -WebSession $session
} catch { Fail "/account error: $_" }
Ok "account 返回：$($acct | ConvertTo-Json -Compress)"

# ---------- 4b. 无 token 期望拒绝 ----------
Step '4b' 'GET /api/auth/account  (无 token，期望失败)'
$anonFailed = $false
try {
    Invoke-RestMethod -Uri "$Base/api/auth/account" -WebSession (New-Object Microsoft.PowerShell.Commands.WebRequestSession) | Out-Null
} catch { $anonFailed = $true }
if ($anonFailed) { Ok '无 token 被拒（预期）' } else { Write-Host '[WARN] 无 token 未被拒，需检查鉴权' -ForegroundColor Yellow }

# ---------- 5. refresh ----------
Step 5 "POST /api/auth/refresh?deviceFp=$DeviceFp"
try {
    $rf = Invoke-RestMethod -Uri "$Base/api/auth/refresh?deviceFp=$DeviceFp" -Method Post -WebSession $session
} catch { Fail "refresh error: $_" }
$at2 = $rf.data.accessToken
if (-not $at2) { Fail "refresh 未返回 accessToken: $($rf | ConvertTo-Json -Compress)" }
Ok "refresh 成功，新 accessToken 长度=$($at2.Length)"

# ---------- 6. logout ----------
Step 6 'POST /api/auth/logout'
$logoutBody = @{ deviceFp = $DeviceFp } | ConvertTo-Json
try {
    Invoke-RestMethod -Uri "$Base/api/auth/logout" -Method Post -ContentType 'application/json' `
        -Body $logoutBody -Headers @{ Authorization = "Bearer $at2" } -WebSession $session | Out-Null
} catch { Fail "logout error: $_" }
Ok 'logout 成功'

Remove-Item $captchaFile -Force -ErrorAction SilentlyContinue
Write-Host "`n=== SMOKE PASSED ===" -ForegroundColor Green
Write-Host "（临时验证码图片已清理）" -ForegroundColor DarkGray

pause
