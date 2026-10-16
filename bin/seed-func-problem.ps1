# ============================================================
# seed-func-problem.ps1 - Create a LeetCode(function)-mode "Two Sum" problem + 2 JSON cases.
#
# Companion to seed-demo-problem.ps1 (which is ACM/stdin): this one exercises the
# function-mode harness path (ioMode=LEETCODE + judgeConfig.signature), where each case's
# `stdin` carries a JSON parameter object (e.g. {"nums":[2,7,11,15],"target":9}) and the
# expected output is a JSON value.
#
# Usage:
#   .\seed-func-problem.ps1
#   .\seed-func-problem.ps1 -Force      # (re)create the test cases even if the problem exists
#
# Mints an admin RS256 JWT and calls the admin API directly on the server (:8081). No SSO needed.
# ============================================================
[CmdletBinding()]
param(
    [string]$Server = 'http://localhost:8081',
    [switch]$Force
)
. (Join-Path $PSScriptRoot '_common.ps1')

$adminJwt = New-ServiceJwt @{
    sub                = '999001'
    uid                = '999001'
    preferred_username = 'demo-admin'
    name               = 'Demo Admin'
    roles              = @('ROLE_ADMIN')
    scope              = 'openid'
}
$headers = @{ Authorization = "Bearer $adminJwt" }

$slug = 'two-sum-func'
$existing = Invoke-RestMethod -Uri "$Server/api/public/problems"
$found = @($existing | Where-Object { $_.slug -eq $slug })

if ($found.Count -ge 1 -and -not $Force) {
    Write-Step "$slug already exists (id=$($found[0].id)) - nothing to do (use -Force to re-seed test cases)"
    exit 0
}

$problemId = if ($found.Count -ge 1) { [long]$found[0].id } else { 0 }

if ($problemId -le 0) {
    Write-Step "Creating problem $slug (LEETCODE mode)"
    $judgeConfig = @{
        signature = @{
            method     = 'twoSum'
            returnType = 'int[]'
            params     = @(
                @{ name = 'nums';   type = 'int[]' },
                @{ name = 'target'; type = 'int' }
            )
        }
    }
    $body = @{
        slug          = $slug
        title         = '两数之和（函数模式）'
        titleEn       = 'Two Sum (function mode)'
        difficulty    = 'EASY'
        description   = "给定一个整数数组 nums 和一个目标值 target，返回和为 target 的两个下标（从 0 开始）。`n`n函数模式：实现 class Solution 的 twoSum(nums, target)。"
        descriptionEn = "Return the two 0-based indices that add up to target. LeetCode-style: implement Solution.twoSum(nums, target)."
        ioMode        = 'LEETCODE'
        judgeConfig   = $judgeConfig
        timeLimitMs   = 5000
        memoryLimitKb = 262144
        rewardPoints  = 10
        enabled       = $true
    } | ConvertTo-Json -Depth 10
    $created = Invoke-RestMethod -Uri "$Server/api/problems" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    $problemId = [long]$created.id
    Write-Ok "created problem id=$problemId"
} else {
    Write-Step "Problem $slug exists (id=$problemId)"
}

Write-Step 'Replacing test cases (JSON parameter objects)'
$cases = @(
    @{ idx = 0; stdin = '{"nums":[2,7,11,15],"target":9}'; expectedOutput = '[0,1]'; isSample = $true; score = 5 }
    @{ idx = 1; stdin = '{"nums":[3,2,4],"target":6}';     expectedOutput = '[1,2]'; isSample = $true; score = 5 }
) | ConvertTo-Json -Depth 6
Invoke-RestMethod -Uri "$Server/api/problems/$problemId/testcases" -Method Post -Headers $headers -ContentType 'application/json' -Body $cases | Out-Null
Write-Ok "2 test cases set on problem $problemId"

Write-Step 'Done'
