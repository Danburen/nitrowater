<#
smoke-oidc.ps1 - OIDC authorization-code flow end-to-end (interactive).

Flow: /oauth2/authorize -> /login (captcha, human) -> code -> /oauth2/token -> verify claims.

Prereq:
  - account running at http://localhost:8090 (rebuilt)
  - MySQL / Redis up, deploy/keys present, WATERFUN_KEK set
  - a test account exists (default smokeuser1 / Passw0rd!)

Usage (Windows PowerShell 5.1 or PowerShell 7):
  .\deploy\bin\smoke-oidc.ps1
  .\deploy\bin\smoke-oidc.ps1 -Base http://localhost:8090 -Username smokeuser1 -Password 'Passw0rd!'
#>
param(
    [string]$Base = 'http://localhost:8090',
    [string]$ClientId = 'nitrowater-web',
    [string]$Redirect = 'http://localhost:5173/auth/callback',
    [string]$Username = 'smokeuser1',
    [string]$Password = 'Passw0rd!',
    [string]$DeviceFp = 'smoketestdevice0001'
)

$ErrorActionPreference = 'Stop'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$captchaFile = Join-Path $env:TEMP 'nitrowater_oidc_captcha.png'

function Step($n, $msg) { Write-Host "`n=== [$n] $msg ===" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "[OK] $msg" -ForegroundColor Green }
function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    Remove-Item $captchaFile -Force -ErrorAction SilentlyContinue
    exit 1
}

# Issues a request without auto-following redirects; returns @{ Status; Location }.
# Uses HttpWebRequest directly so it works on Windows PowerShell 5.1.
function Invoke-NoRedirect($method, $url, $body) {
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = $method
    $req.AllowAutoRedirect = $false
    $req.CookieContainer = $session.Cookies
    $req.UserAgent = 'nitrowater-smoke'
    if ($body) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($body)
        $req.ContentType = 'application/x-www-form-urlencoded'
        $req.ContentLength = $bytes.Length
        $s = $req.GetRequestStream()
        $s.Write($bytes, 0, $bytes.Length)
        $s.Close()
    }
    try {
        $resp = $req.GetResponse()
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
    }
    $result = @{ Status = [int]$resp.StatusCode; Location = [string]$resp.Headers['Location'] }
    $resp.Close()
    return $result
}

# Decodes a JWT payload without verifying the signature.
function Decode-Jwt($jwt) {
    $parts = $jwt.Split('.')
    if ($parts.Length -lt 2) { return $null }
    $payload = $parts[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) { 2 { $payload += '==' } 3 { $payload += '=' } }
    return ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json)
}

# PKCE (S256)
$verifier = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object { [char]$_ })
$sha = [System.Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($verifier))
$challenge = [Convert]::ToBase64String($sha).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$authzUrl = "$Base/oauth2/authorize?response_type=code&client_id=$ClientId&redirect_uri=$([uri]::EscapeDataString($Redirect))&scope=openid%20profile&code_challenge=$challenge&code_challenge_method=S256"

# 1. Seed the saved request.
Step 1 'GET /oauth2/authorize (expect 302 -> /login)'
$r1 = Invoke-NoRedirect 'GET' $authzUrl $null
Ok "status=$($r1.Status) location=$($r1.Location)"
if ($r1.Status -ne 302) { Fail 'authorize did not redirect to login' }

# 2. Captcha.
Step 2 'GET /api/auth/captcha'
try { Invoke-WebRequest -Uri "$Base/api/auth/captcha" -WebSession $session -OutFile $captchaFile -UseBasicParsing | Out-Null }
catch { Fail "captcha error: $_" }
Ok "captcha saved: $captchaFile"
Start-Process $captchaFile
$captcha = Read-Host 'Enter the captcha from the image'

# 3. Form login -> 302 to the saved authorize request.
Step 3 'POST /login'
$loginBody = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))&captcha=$([uri]::EscapeDataString($captcha))&deviceFp=$DeviceFp"
$r3 = Invoke-NoRedirect 'POST' "$Base/login" $loginBody
Ok "status=$($r3.Status) location=$($r3.Location)"
if ($r3.Status -ne 302) { Fail "login failed (status=$($r3.Status)); check captcha/credentials" }

# 4. Replay the saved authorize request -> 302 to redirect_uri?code=...
Step 4 'GET saved /oauth2/authorize'
$authzFollow = if ($r3.Location -match '^https?://') { $r3.Location } else { "$Base$($r3.Location)" }
$r4 = Invoke-NoRedirect 'GET' $authzFollow $null
Ok "status=$($r4.Status) location=$($r4.Location)"
$code = $null
if ($r4.Location -match '[?&]code=([^&]+)') { $code = $Matches[1] }
if (-not $code) { Fail "no code in redirect: $($r4.Location)" }
Ok "code=$($code.Substring(0, [Math]::Min(12, $code.Length)))..."

# 5. Token exchange.
Step 5 'POST /oauth2/token'
$tokenBody = "grant_type=authorization_code&code=$([uri]::EscapeDataString($code))&redirect_uri=$([uri]::EscapeDataString($Redirect))&client_id=$ClientId&code_verifier=$verifier"
try { $tok = Invoke-RestMethod -Uri "$Base/oauth2/token" -Method Post -ContentType 'application/x-www-form-urlencoded' -Body $tokenBody -WebSession $session }
catch { Fail "token error: $_" }
Ok "token_type=$($tok.token_type) expires_in=$($tok.expires_in) id_token=$([bool]$tok.id_token)"

# 6. Decode and verify claims.
Step 6 'Verify claims'
$at = Decode-Jwt $tok.access_token
$id = Decode-Jwt $tok.id_token
Write-Host "access_token: $($at | ConvertTo-Json -Compress)"
Write-Host "id_token    : $($id | ConvertTo-Json -Compress)"
$checks = @(
    @{ n = 'sub == uid'; ok = ($at.sub -and $at.sub -eq $at.uid) },
    @{ n = 'uid present'; ok = [bool]$at.uid },
    @{ n = 'preferred_username'; ok = [bool]$at.preferred_username },
    @{ n = 'roles'; ok = ($at.roles -and $at.roles.Count -ge 1) },
    @{ n = 'did'; ok = [bool]$at.did }
)
$allOk = $true
foreach ($c in $checks) { if ($c.ok) { Ok $c.n } else { Write-Host "[MISS] $($c.n)" -ForegroundColor Yellow; $allOk = $false } }

Remove-Item $captchaFile -Force -ErrorAction SilentlyContinue
if ($allOk) { Write-Host "`n=== OIDC SMOKE PASSED ===" -ForegroundColor Green }
else { Write-Host "`n=== OIDC SMOKE INCOMPLETE ===" -ForegroundColor Yellow }
exit ([int](-not $allOk))
