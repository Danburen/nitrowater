# ============================================================
# seed-test-users.ps1 - Ensure the standard test users exist with the right roles (idempotent).
#
# Lives in bin/ (tracked) alongside the rest of the dev-data tooling; helpers come from
# bin/_common.ps1. Promoted from the git-ignored .ai/testkit tree on 2026-10-15.
#
# Usage:
#   .\bin\dev\seed-test-users.ps1
#   .\bin\dev\seed-test-users.ps1 -Only testadmin
#   .\bin\dev\seed-test-users.ps1 -Password 'Passw0rd123'
#
# For a missing user the full automated register flow runs:
#   GET /api/auth/captcha (read code from Redis) -> POST /api/auth/send-code
#   (read SMS code from Redis) -> POST /api/auth/register -> grant role via SQL.
# Then the role is granted via:
#   INSERT IGNORE INTO user_role(uid, role_id) SELECT u.uid, r.id FROM `user` u JOIN `role` r ...
#
# Notes:
#   - account (8090) and Redis must be up.
#   - register is rate-limited to 3 / 300s per IP: keep batches small.
#   - password must satisfy the strong-password rule (default Passw0rd123).
# ============================================================
[CmdletBinding()]
param(
    [string]$Base = 'http://localhost:8090',
    [string]$Password = 'Passw0rd123',
    [string[]]$Only
)
. (Join-Path $PSScriptRoot '..\_common.ps1')

# When invoked via `pwsh -File ... -Only a,b` the comma list arrives as ONE token; split it.
if ($Only) { $Only = @($Only | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ }) }

$users = @(
    [pscustomobject]@{ username='testadmin'; phone='13800000002'; role='ADMIN' }
    [pscustomobject]@{ username='testuser1'; phone='13800000003'; role='USER' }
    [pscustomobject]@{ username='testuser';  phone='13800000004'; role='USER' }
)

function Get-CookieValue($session, [string]$CookieName) {
    $c = $session.Cookies.GetCookies([uri]"$Base/") | Where-Object { $_.Name -eq $CookieName } | Select-Object -First 1
    if ($c) { return $c.Value.Trim('"') }
    return $null
}

function Get-Uid([string]$Username) {
    $sql = 'SELECT uid FROM nitrowater_account.`user` WHERE username=''{0}'';' -f $Username
    $out = Invoke-Mysql $sql
    $uid = ($out | Where-Object { $_ -match '^\d+$' } | Select-Object -First 1)
    if ($uid) { return [long]$uid }
    return $null
}

function Grant-Role([string]$Username, [string]$Role) {
    $sql = 'INSERT IGNORE INTO nitrowater_account.`user_role`(uid, role_id) SELECT u.uid, r.id FROM nitrowater_account.`user` u JOIN nitrowater_account.`role` r ON r.code=''{0}'' WHERE u.username=''{1}'';' -f $Role, $Username
    Invoke-Mysql $sql | Out-Null
}

function Register-User([string]$Username, [string]$Phone) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $captchaFile = Join-Path $env:TEMP "nitrowater_captcha_$Username.png"

    # 1. captcha -> read code from Redis
    Invoke-WebRequest -Uri "$Base/api/auth/captcha" -WebSession $session -OutFile $captchaFile -UseBasicParsing | Out-Null
    $captchaUuid = Get-CookieValue $session 'CAPTCHA_KEY'
    if (-not $captchaUuid) { throw 'CAPTCHA_KEY cookie missing' }
    $captcha = Get-RedisValue "nitrowater:biz:verify:captcha:$captchaUuid"
    if (-not $captcha) { throw "captcha code not found in Redis ($captchaUuid)" }

    # 2. send-code -> read SMS code from Redis
    $sendBody = @{
        target     = $Phone
        channel    = 'SMS'
        scene      = 'REGISTER'
        captcha    = $captcha
        deviceFp   = $DeviceFp
        deviceInfo = @{ deviceFp = $DeviceFp; os = 'Windows'; browser = 'script' }
    } | ConvertTo-Json -Depth 5
    Invoke-RestMethod -Uri "$Base/api/auth/send-code" -Method Post -ContentType 'application/json' -Body $sendBody -WebSession $session | Out-Null
    $smsUuid = Get-CookieValue $session 'SMS_CODE_KEY'
    if (-not $smsUuid) { throw 'SMS_CODE_KEY cookie missing' }
    $code = Get-RedisValue "nitrowater:biz:verify:sms:${Phone}:register:$smsUuid"
    if (-not $code) { throw "SMS code not found in Redis ($Phone/$smsUuid)" }

    # 3. register
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
    Invoke-RestMethod -Uri "$Base/api/auth/register" -Method Post -ContentType 'application/json' -Body $regBody -WebSession $session | Out-Null

    Remove-Item -LiteralPath $captchaFile -Force -ErrorAction SilentlyContinue
}

$selected = if ($Only) { @($users | Where-Object { $Only -contains $_.username }) } else { $users }
if (-not $selected) { Write-Err 'no matching username in -Only'; exit 1 }

foreach ($u in $selected) {
    Write-Step "$($u.username) ($($u.role))"
    $uid = Get-Uid $u.username
    if ($uid) {
        Write-Ok "exists uid=$uid"
    } else {
        Write-Host '  registering...'
        Register-User $u.username $u.phone
        $uid = Get-Uid $u.username
        if (-not $uid) { Write-Err "registration failed for $($u.username)"; continue }
        Write-Ok "registered uid=$uid"
    }
    Grant-Role $u.username $u.role
    Write-Ok "role=$($u.role) ensured"
}

Write-Step 'Done'
