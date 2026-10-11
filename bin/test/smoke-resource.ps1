<#
smoke-resource.ps1 - unified resource service end-to-end (through the BFF).

Flow: OIDC login -> POST /api/resource/upload/policy -> upload bytes (presigned direct OR proxy)
      -> POST /api/resource/upload/callback -> GET /api/public/resource/{uuid} -> assert bytes.

Prereq:
  - account (:8090) + bff (:8080) + resource (:8093) + SeaweedFS (:8333) running;
    a test account exists (default smokeuser1 / Passw0rd!).
  - captcha: pass -Captcha, or -RedisCli to read it from Redis automatically, else it is prompted.

Usage:
  .\bin\test\smoke-resource.ps1
  .\bin\test\smoke-resource.ps1 -RedisCli 'C:\Redis\redis-cli.exe'
#>
param(
  [string]$Bff = 'http://localhost:8080',
  [string]$As = 'http://localhost:8090',
  [string]$Username = 'smokeuser1',
  [string]$Password = 'Passw0rd!',
  [string]$DeviceFp = 'smokeresourcedev0001',
  [string]$Captcha,
  [string]$RedisCli,
  [string]$CaptchaRedisPrefix = 'nitrowater:biz:verify:captcha:',
  [string]$App = 'smoke'
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
Invoke-WebRequest -Uri "$As/api/auth/captcha" -WebSession $session -OutFile (Join-Path $env:TEMP 'nitrowater_res_captcha.png') -UseBasicParsing | Out-Null
if (-not $Captcha) {
  if ($RedisCli) {
    $uuid = CookieVal "$As/api/auth/captcha" 'CAPTCHA_KEY'
    $Captcha = ((& $RedisCli -h localhost -p 6379 GET "$CaptchaRedisPrefix$uuid") -join '').Trim('"')
    Write-Host "captcha read from redis: $Captcha"
  } else {
    $img = Join-Path $env:TEMP 'nitrowater_res_captcha.png'; Start-Process $img
    $Captcha = Read-Host 'Enter the captcha from the image'
  }
}

Write-Host "=== [3] POST /login ===" -ForegroundColor Cyan
$body = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))&captcha=$([uri]::EscapeDataString($Captcha))&deviceFp=$([uri]::EscapeDataString($DeviceFp))"
$r3 = NoRedirect 'POST' "$As/login" $body
if ($r3.Status -ne 302) { Check 'login' $false "status=$($r3.Status) (check captcha/credentials)"; exit 1 }
$r4 = NoRedirect 'GET' (Abs $r3.Location) $null
if (-not ($r4.Status -eq 302 -and $r4.Location -match 'code=')) { Check 'authorize -> callback' $false "status=$($r4.Status)"; exit 1 }
NoRedirect 'GET' $r4.Location $null | Out-Null
$me = Invoke-RestMethod -Uri "$Bff/bff/me" -WebSession $session -UseBasicParsing
Check 'logged in (bff/me authenticated)' ($me.authenticated -eq $true) "uid=$($me.uid)"

$csrf = CookieVal $Bff 'XSRF-TOKEN'
$payload = "hello-nitrowater $(Get-Random)"
$bytes = [Text.Encoding]::UTF8.GetBytes($payload)
$fileName = 'smoke.txt'

Write-Host "=== [4] POST /api/resource/upload/policy ===" -ForegroundColor Cyan
$policyBody = @{ app = $App; fileName = $fileName; contentType = 'text/plain'; sizeBytes = $bytes.Length; visibility = 'public' } | ConvertTo-Json -Compress
$policy = Invoke-RestMethod -Uri "$Bff/api/resource/upload/policy" -Method Post -WebSession $session -Headers @{ 'X-XSRF-TOKEN' = $csrf } -ContentType 'application/json' -Body $policyBody
Check 'policy issued' ([bool]$policy.uuid) "uuid=$($policy.uuid) backend=$($policy.backend) direct=$([bool]$policy.uploadUrl)"

Write-Host "=== [5] Upload bytes ===" -ForegroundColor Cyan
if ($policy.uploadUrl) {
  Invoke-WebRequest -Uri $policy.uploadUrl -Method Put -Body $bytes -ContentType 'text/plain' -UseBasicParsing | Out-Null
  Check 'direct upload (presigned PUT)' $true "url=$($policy.uploadUrl)"
} else {
  Invoke-WebRequest -Uri "$Bff/api/resource/upload/$($policy.uuid)" -Method Put -WebSession $session -Headers @{ 'X-XSRF-TOKEN' = $csrf } -Body $bytes -ContentType 'text/plain' -UseBasicParsing | Out-Null
  Check 'proxy upload' $true "uuid=$($policy.uuid)"
}

Write-Host "=== [6] POST /api/resource/upload/callback ===" -ForegroundColor Cyan
$cb = Invoke-RestMethod -Uri "$Bff/api/resource/upload/callback" -Method Post -WebSession $session -Headers @{ 'X-XSRF-TOKEN' = $csrf } -ContentType 'application/json' -Body (@{ uuid = $policy.uuid } | ConvertTo-Json -Compress)
Check 'callback activated' ($cb.status -eq 'ACTIVE') "status=$($cb.status) type=$($cb.resourceType) size=$($cb.sizeBytes)"

Write-Host "=== [7] GET /api/public/resource/{uuid} (anonymous) ===" -ForegroundColor Cyan
$r7 = NoRedirect 'GET' "$Bff/api/public/resource/$($policy.uuid)" $null
$readUrl = if ($r7.Status -eq 302 -and $r7.Location) { $r7.Location } else { "$Bff/api/public/resource/$($policy.uuid)" }
Check 'public read resolves' ($r7.Status -eq 302 -or $r7.Status -eq 200) "status=$($r7.Status) url=$readUrl"
$content = (Invoke-WebRequest -Uri $readUrl -UseBasicParsing).Content
if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
Check 'public read content matches' ($content -match [regex]::Escape($payload)) "content=$content"

Remove-Item (Join-Path $env:TEMP 'nitrowater_res_captcha.png') -Force -ErrorAction SilentlyContinue
if ($script:fail -eq 0) { Write-Host "`n=== RESOURCE SMOKE PASSED ===" -ForegroundColor Green; exit 0 }
else { Write-Host "`n=== RESOURCE SMOKE: $($script:fail) FAILED ===" -ForegroundColor Red; exit 1 }
