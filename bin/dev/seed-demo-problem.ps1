# ============================================================
# seed-demo-problem.ps1 - Create the "Two Sum" demo problem + 2 test cases.
#
# Usage:
#   .\bin\dev\seed-demo-problem.ps1
#   .\bin\dev\seed-demo-problem.ps1 -Force      # (re)create the test cases even if the problem exists
#
# Mints an admin RS256 JWT (deploy/keys/private.key, kid=nitrowater-key-1) and calls the
# admin API directly on the resource server (server :8081). No SSO login required.
# Run after a biz reset so playwright-smoke.ps1 has a problem to judge.
# ============================================================
[CmdletBinding()]
param(
    [string]$Server = 'http://localhost:8081',
    [switch]$Force
)
. (Join-Path $PSScriptRoot '..\_common.ps1')

$adminJwt = New-ServiceJwt @{
    sub                = '999001'
    uid                = '999001'
    preferred_username = 'demo-admin'
    name               = 'Demo Admin'
    roles              = @('ROLE_ADMIN')
    scope              = 'openid'
}
$headers = @{ Authorization = "Bearer $adminJwt" }

$existing = Invoke-RestMethod -Uri "$Server/api/public/problems"
$found = @($existing | Where-Object { $_.slug -eq 'two-sum' })

if ($found.Count -ge 1 -and -not $Force) {
    Write-Step "two-sum already exists (id=$($found[0].id)) - nothing to do (use -Force to re-seed test cases)"
    exit 0
}

$problemId = if ($found.Count -ge 1) { [long]$found[0].id } else { 0 }

if ($problemId -le 0) {
    Write-Step 'Creating problem two-sum'
    $body = @{
        slug          = 'two-sum'
        title         = '两数之和'
        titleEn       = 'Two Sum'
        difficulty    = 'EASY'
        description   = "给定一个整数数组 nums 和一个目标值 target，返回和为 target 的两个数的下标（从 0 开始）。`n`n输入：第一行是若干以空格分隔的整数，第二行是 target。`n输出：两个下标，以空格分隔。"
        descriptionEn = "Given an integer array nums and a target, print the two indices (0-based) that add up to target.`n`nInput: line 1 = space-separated integers; line 2 = target.`nOutput: the two indices separated by a space."
        ioMode        = 'ACM'
        timeLimitMs   = 5000
        memoryLimitKb = 262144
        rewardPoints  = 10
        enabled       = $true
    } | ConvertTo-Json -Depth 6
    $created = Invoke-RestMethod -Uri "$Server/api/problems" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    $problemId = [long]$created.id
    Write-Ok "created problem id=$problemId"
} else {
    Write-Step "Problem two-sum exists (id=$problemId)"
}

Write-Step 'Replacing test cases'
$cases = @(
    @{ idx = 0; stdin = "2 7 11 15`n9"; expectedOutput = '0 1'; isSample = $true; score = 5 }
    @{ idx = 1; stdin = "3 2 4`n6";     expectedOutput = '1 2'; isSample = $true; score = 5 }
) | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "$Server/api/problems/$problemId/testcases" -Method Post -Headers $headers -ContentType 'application/json' -Body $cases | Out-Null
Write-Ok "2 test cases set on problem $problemId"

Write-Step 'Done'
