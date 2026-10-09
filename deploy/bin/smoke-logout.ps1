<#
smoke-logout.ps1 - BFF logout end-to-end (RP-Initiated Logout).

Drives the full browser-style flow through the BFF: OIDC login -> POST /logout -> the BFF
redirects to the Authorization Server's end-session endpoint -> the AS destroys the SSO session
and redirects back. Then it asserts:

  1. the BFF session is destroyed                     -> GET /bff/me -> authenticated=false
  2. the Authorization Server SSO session is destroyed -> a fresh /oauth2/authorize bounces to /login
  3. the RP/logout redirect lands back on the BFF      -> /connect/logout answered 302
  4. the upstream refresh token is revoked             -> refresh_token grant answered 400 (optional)

Prereq:
  - account (:8090) + bff (:8080) running; a test account exists (default smokeuser1 / Passw0rd!)
  - captcha: pass -Captcha, or -RedisCli to read it from Redis automatically, else it is prompted.

Usage:
  .\deploy\bin\smoke-logout.ps1
  .\deploy\bin\smoke-logout.ps1 -RedisCli 'E:\Program Files\Redis\redis-cli.exe'
  .\deploy\bin\smoke-logout.ps1 -Mysql 'mysql.exe'    # also assert refresh-token revocation
#>
param(
  [string]$Bff = 'http://localhost:8080',
  [string]$As = 'http://localhost:8090',
  [string]$Username = 'smokeuser1',
  [string]$Password = 'Passw0rd!',
  [string]$DeviceFp = 'smokelogoutdev0001',
  [string]$Captcha,
  [string]$RedisCli,
  [string]$Mysql,
  [string]$CaptchaRedisPrefix = 'nitrowater:biz:verify:captcha:',
  [string]$ClientId = 'nitrowater-bff',
  [string]$ClientSecret = 'nitrowater-bff-secret'
)

$ErrorActionPreference = 'Stop'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:fail = 0
function Check($name, $ok, $detail) {
  if ($ok) { Write-Host "[PASS] $name  $detail" -ForegroundColor Green }
  else { Write-Host "[FAIL] $name  $detail" -ForegroundColor Red; $script:fail++ }
}
function Abs($loc) { if (-not $loc) { return $null }; if ($loc -match '^https?://') { return $loc }; return "$As$loc" }
function CookieVal($url, $name) {
  $c = $session.Cookies.GetCookies([uri]$url) | Where-Object { $_.Name -eq $name }
  if ($c) { return $c.Value } ; return $null
}
function NoRedirect($method, $url, $body) {
  $req = [System.Net.HttpWebRequest]::Create($url); $req.Method = $method; $req.AllowAutoRedirect = $false
  $req.CookieContainer = $session.Cookies; $req.UserAgent = 'nitrowater-smoke'
  if ($body) {
    $b = [Text.Encoding]::UTF8.GetBytes($body); $req.ContentType = 'application/x-www-form-urlencoded'; $req.ContentLength = $b.Length
    $s = $req.GetRequestStream(); $s.Write($b, 0, $b.Length); $s.Close()
  }
  try { $r = $req.GetResponse() } catch [System.Net.WebException] { $r = $_.Exception.Response; if (-not $r) { throw } }
  $out = @{ Status = [int]$r.StatusCode; Location = [string]$r.Headers['Location'] }; $r.Close(); return $out
}

Write-Host "`n=== [1] Start OIDC login at the BFF ===" -ForegroundColor Cyan
$authz = NoRedirect 'GET' "$Bff/oauth2/authorization/nitrowater" $null
$asAuthz = Abs $authz.Location
if (-not ($authz.Status -eq 302 -and $asAuthz -like "$As/oauth2/authorize*")) { Check 'bff authorize -> AS' $false "status=$($authz.Status)"; exit 1 }
NoRedirect 'GET' $asAuthz $null | Out-Null

Write-Host "=== [2] Captcha ===" -ForegroundColor Cyan
Invoke-WebRequest -Uri "$As/api/auth/captcha" -WebSession $session -OutFile (Join-Path $env:TEMP 'nitrowater_logout_captcha.png') -UseBasicParsing | Out-Null
if (-not $Captcha) {
  if ($RedisCli) {
    $uuid = CookieVal "$As/api/auth/captcha" 'CAPTCHA_KEY'
    $Captcha = ((& $RedisCli -h localhost -p 6379 GET "$CaptchaRedisPrefix$uuid") -join '').Trim('"')
    Write-Host "captcha read from redis: $Captcha"
  } else {
    $img = Join-Path $env:TEMP 'nitrowater_logout_captcha.png'; Start-Process $img
    $Captcha = Read-Host 'Enter the captcha from the image'
  }
}

Write-Host "=== [3] POST /login ===" -ForegroundColor Cyan
$body = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))&captcha=$([uri]::EscapeDataString($Captcha))&deviceFp=$([uri]::EscapeDataString($DeviceFp))"
$r3 = NoRedirect 'POST' "$As/login" $body
if ($r3.Status -ne 302) { Check 'login' $false "status=$($r3.Status) (check captcha/credentials)"; exit 1 }
$r4 = NoRedirect 'GET' (Abs $r3.Location) $null
$cb = $r4.Location
if (-not ($r4.Status -eq 302 -and $cb -match 'code=')) { Check 'authorize -> callback' $false "status=$($r4.Status) loc=$cb"; exit 1 }
NoRedirect 'GET' $cb $null | Out-Null

$me = Invoke-RestMethod -Uri "$Bff/bff/me" -WebSession $session -UseBasicParsing
Check 'logged in (bff/me authenticated)' ($me.authenticated -eq $true) "uid=$($me.uid)"
$rt = $null
if ($Mysql) {
  $rt = ((& $Mysql -uroot -p123456 -N -B -e "select refresh_token_value from nitrowater_account.oauth2_authorization where principal_name='$($me.uid)' order by access_token_issued_at desc limit 1;" 2>$null) -join '')
}

Write-Host "=== [4] POST /logout ===" -ForegroundColor Cyan
$csrf = CookieVal $Bff 'XSRF-TOKEN'
$r5 = NoRedirect 'POST' "$Bff/logout" "_csrf=$([uri]::EscapeDataString($csrf))"
$endUrl = $r5.Location
Check 'logout -> AS end_session' ($r5.Status -eq 302 -and $endUrl -match '/connect/logout' -and $endUrl -match 'id_token_hint=') "status=$($r5.Status)"

Write-Host "=== [5] Assert the sessions are gone ===" -ForegroundColor Cyan
$r6 = NoRedirect 'GET' $endUrl $null
Check 'AS RP-logout -> 302 back to BFF' ($r6.Status -eq 302) "status=$($r6.Status) loc=$($r6.Location)"

Start-Sleep -Milliseconds 400
$me2 = Invoke-RestMethod -Uri "$Bff/bff/me" -WebSession $session -UseBasicParsing
Check 'BFF session destroyed' ($me2.authenticated -eq $false) "authenticated=$($me2.authenticated)"

$reAuthz = NoRedirect 'GET' "$As/oauth2/authorize?response_type=code&client_id=$ClientId&redirect_uri=$([uri]::EscapeDataString("$Bff/login/oauth2/code/nitrowater"))&scope=openid" $null
Check 'AS SSO session destroyed' ($reAuthz.Status -eq 302 -and (Abs $reAuthz.Location) -notmatch 'code=') "loc=$(Abs $reAuthz.Location)"

if ($rt) {
  $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$ClientId`:$ClientSecret"))
  $tr = [System.Net.HttpWebRequest]::Create("$As/oauth2/token"); $tr.Method = 'POST'; $tr.Headers.Add('Authorization', "Basic $basic")
  $tb = [Text.Encoding]::UTF8.GetBytes("grant_type=refresh_token&refresh_token=$([uri]::EscapeDataString($rt))")
  $tr.ContentType = 'application/x-www-form-urlencoded'; $tr.ContentLength = $tb.Length
  $ts = $tr.GetRequestStream(); $ts.Write($tb, 0, $tb.Length); $ts.Close()
  $code = $null
  try { $resp = $tr.GetResponse(); $code = [int]$resp.StatusCode; $resp.Close() } catch [System.Net.WebException] { $code = [int]$_.Exception.Response.StatusCode }
  Check 'refresh token revoked (invalid_grant)' ($code -eq 400) "token endpoint status=$code"
}

Remove-Item (Join-Path $env:TEMP 'nitrowater_logout_captcha.png') -Force -ErrorAction SilentlyContinue
if ($script:fail -eq 0) { Write-Host "`n=== LOGOUT SMOKE PASSED ===" -ForegroundColor Green; exit 0 }
else { Write-Host "`n=== LOGOUT SMOKE: $($script:fail) FAILED ===" -ForegroundColor Red; exit 1 }
