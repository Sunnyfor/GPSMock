# 微信发送位置的设备验收闭环：任务切换、截图与本机 OCR，不发送位置、不修改模拟目标。
# 须先由用户打开微信发送位置页；中文区域匹配由调用方指定，OCR 不确定时不得伪报通过。
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$ExpectedPattern,
    [ValidateRange(0, 10)][int]$Rounds = 3,
    [ValidateRange(0, 30)][int]$StillSamples = 0,
    [ValidateRange(1, 20)][int]$StillIntervalSeconds = 5,
    [string]$AdbPath = 'D:/Android/Sdk/platform-tools/adb.exe',
    [string]$KnownWrongPattern = ''
)
$ErrorActionPreference = 'Stop'
$probeRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$probeOutput = Join-Path $probeRepo 'app/build/ui-review'
New-Item -ItemType Directory -Force -Path $probeOutput | Out-Null
$probeRun = Get-Date -Format 'yyyyMMdd-HHmmss'

# 只调用当前明确序列号的设备；原生命令失败立即停止，不能继续解释失败后的空输出。
function Invoke-SwitchAdb([string[]]$AdbArguments) {
    $probeReply = & $AdbPath -s $Serial @AdbArguments
    if ($LASTEXITCODE -ne 0) { throw 'Device command failed; switching observation is inconclusive.' }
    $probeReply
}

# 要求发送位置页真实在前台且没有锁屏，防止采集聊天页、通知或其他应用界面。
function Assert-WechatLocationPage {
    $probeTop = (Invoke-SwitchAdb @('shell', 'dumpsys', 'activity', 'activities') | Select-String 'topResumedActivity=') -join ' '
    $probeWindow = (Invoke-SwitchAdb @('shell', 'dumpsys', 'window', 'policy')) -join "`n"
    if ($probeTop -notmatch 'u0 com.tencent.mm/\.plugin.location_soso.SoSoProxyUI t(\d+)' -or
        $probeWindow -notmatch 'mIsShowing=false') {
        throw 'Open the unlocked WeChat send-location page and leave it visible; no screenshot captured.'
    }
}

# 编码本地 PowerShell 字符串字面量，不把路径或匹配式作为可执行命令拼接进去。
function Convert-SwitchLiteral([string]$Value) { "'" + $Value.Replace("'", "''") + "'" }

# 只输出模拟通道的时间年龄，不输出经纬度或系统位置日志全文。
function Get-SwitchMockAges {
    $probeLocationLines = Invoke-SwitchAdb @('shell', 'dumpsys', 'location') | Select-Object -First 95
    # 在位置快照之后取当前时钟，避免读取期间的新写入产生表面上的负年龄。
    $probeNow = [double](((Invoke-SwitchAdb @('shell', 'cat', '/proc/uptime')) -join ' ').Split(' ')[0])
    $probeProviders = @{}
    $probeProvider = ''
    foreach ($probeLine in $probeLocationLines) {
        if ($probeLine -match '^    (gps|network|fused) provider') { $probeProvider = $Matches[1] }
        if ($probeLine -match 'last mock location=.*et=\+(?:(?<d>\d+)d)?(?:(?<h>\d+)h)?(?:(?<m>\d+)m)?(?:(?<s>\d+)s)?(?:(?<ms>\d+)ms)?') {
            $probeFix = [double]$Matches.d * 86400 + [double]$Matches.h * 3600 + [double]$Matches.m * 60 +
                [double]$Matches.s + [double]$Matches.ms / 1000
            $probeProviders[$probeProvider] = [math]::Round($probeNow - $probeFix, 2)
        }
    }
    $probeProviders
}

# 采集当前已校验页面，使用系统自带 OCR 分类，不读取微信私有数据或聊天记录。
function Get-SwitchObservation([int]$Round, [string]$Phase = 'initial') {
    Assert-WechatLocationPage
    $probeImage = Join-Path $probeOutput "wechat-switch-$probeRun-$Round.png"
    try {
        Invoke-SwitchAdb @('shell', 'screencap', '-p', '/data/local/tmp/gpsmock-wechat-switch.png') | Out-Null
        Invoke-SwitchAdb @('pull', '/data/local/tmp/gpsmock-wechat-switch.png', $probeImage) | Out-Null
    } finally {
        Invoke-SwitchAdb @('shell', 'rm', '/data/local/tmp/gpsmock-wechat-switch.png') | Out-Null
    }
    try { Assert-WechatLocationPage }
    catch { Remove-Item -LiteralPath $probeImage -ErrorAction SilentlyContinue; throw }
    $probeOcrPath = Join-Path $PSScriptRoot 'check-location-image.ps1'
    # Windows PowerShell 5 需要 WinRT，按 UTF-8 显式加载脚本，避免中文注释被系统代码页误解析。
    $probeOcrCommand = '& ([scriptblock]::Create([IO.File]::ReadAllText(' + (Convert-SwitchLiteral $probeOcrPath) +
        ',[Text.Encoding]::UTF8))) -Path ' + (Convert-SwitchLiteral $probeImage) +
        ' -ExpectedPattern ' + (Convert-SwitchLiteral $ExpectedPattern) +
        ' -KnownWrongPattern ' + (Convert-SwitchLiteral $KnownWrongPattern)
    $probeOcrJson = & powershell.exe -NoProfile -NonInteractive -Command $probeOcrCommand
    if ($LASTEXITCODE -ne 0) { throw 'Local OCR failed; visual observation is inconclusive.' }
    $probeOcr = ($probeOcrJson -join ' ') | ConvertFrom-Json
    $probeVerdict = 'INCONCLUSIVE'
    if ($probeOcr.expectedTextVisible -and -not $probeOcr.knownWrongRegionVisible) { $probeVerdict = 'PASS' }
    if (-not $probeOcr.expectedTextVisible -and $probeOcr.knownWrongRegionVisible) { $probeVerdict = 'FAIL' }
    Write-Host "Observed round=$Round phase=$Phase visual=$probeVerdict"
    [pscustomobject]@{
        round = $Round
        phase = $Phase
        visualVerdict = $probeVerdict
        mockAgeSeconds = Get-SwitchMockAges
        image = $probeImage
    }
}

Assert-WechatLocationPage
$probeActivities = (Invoke-SwitchAdb @('shell', 'dumpsys', 'activity', 'activities')) -join "`n"
$probeWechatMatch = [regex]::Match($probeActivities, 'topResumedActivity=.*u0 com.tencent.mm/\.plugin.location_soso.SoSoProxyUI t(\d+)')
$probeMainMatch = [regex]::Match($probeActivities, 'ActivityRecord\{[^\r\n]*u0 com.sunnyfor.gpsmock/\.MainActivity t(\d+)')
if (-not $probeWechatMatch.Success -or -not $probeMainMatch.Success) { throw 'Both existing app tasks are required; observation is inconclusive.' }
$probeWechatTask = [int]$probeWechatMatch.Groups[1].Value
$probeMainTask = [int]$probeMainMatch.Groups[1].Value
$probePidBefore = (Invoke-SwitchAdb @('shell', 'pidof', 'com.sunnyfor.gpsmock')) -join ' '
$probeResults = @(Get-SwitchObservation 0)
for ($probeRound = 1; $probeRound -le $Rounds; $probeRound++) {
    # 任务聚焦保持微信原页面；重新发 launcher Intent 会使微信退回聊天页，不能用于该复现。
    Invoke-SwitchAdb @('shell', 'am', 'task', 'focus', "$probeMainTask") | Out-Null
    Start-Sleep -Seconds 2
    Invoke-SwitchAdb @('shell', 'am', 'task', 'focus', "$probeWechatTask") | Out-Null
    Start-Sleep -Seconds 3
    $probeResults += Get-SwitchObservation $probeRound 'switch'
}
for ($probeStill = 1; $probeStill -le $StillSamples; $probeStill++) {
    # 不切回助手；独立验证停留几秒后是否被其他定位结果覆盖。
    Start-Sleep -Seconds $StillIntervalSeconds
    $probeResults += Get-SwitchObservation ($Rounds + $probeStill) 'hold'
}
$probeResults | ConvertTo-Json -Depth 4
$probePidAfter = (Invoke-SwitchAdb @('shell', 'pidof', 'com.sunnyfor.gpsmock')) -join ' '
if ($probePidBefore -ne $probePidAfter) { throw 'Main process changed; switching observation is inconclusive.' }
if (@($probeResults | Where-Object { $_.visualVerdict -ne 'PASS' }).Count -gt 0) {
    throw 'WeChat did not consistently display the expected region; inspect captured images, not just provider success.'
}
