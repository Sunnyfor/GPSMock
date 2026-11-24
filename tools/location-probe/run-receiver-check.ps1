# 通过独立 APK 的公开插桩入口验收；Process crashed 的 adb 退出码可能仍为 0，必须校验真实测试结果。
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$AdbPath = 'D:/Android/Sdk/platform-tools/adb.exe',
    [ValidateSet('gps', 'network', 'fused', 'passive', 'googleflp')]
    [string]$Provider = 'gps',
    [ValidateSet('', 'gps', 'network', 'fused')]
    [string]$ExpectedPassiveSource = '',
    [switch]$ExpectedStationarySpeed
)
$ErrorActionPreference = 'Stop'

# 锁屏不允许普通定位窗口获得前台权限；提前终止，避免等待超时后误把前置条件当引擎失败。
$receiverWindowPolicy = (& $AdbPath -s $Serial shell dumpsys window policy) -join "`n"
if ($LASTEXITCODE -ne 0 -or $receiverWindowPolicy -notmatch 'mIsShowing=false') {
    throw 'Unlock the device before receiver observation; no location test started.'
}
$receiverPath = & $AdbPath -s $Serial shell pm path com.sunnyfor.gpsmock.locationprobe
if ($LASTEXITCODE -ne 0 -or -not ($receiverPath -match '^package:')) {
    throw 'Independent receiver APK is not installed on the specified device.'
}
$mainPidBefore = (& $AdbPath -s $Serial shell pidof com.sunnyfor.gpsmock) -join ' '
if ([string]::IsNullOrWhiteSpace($mainPidBefore)) {
    throw 'Start the main application and a user-approved mock session before receiver observation.'
}
$receiverArguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'provider', $Provider)
if ($ExpectedPassiveSource) { $receiverArguments += @('-e', 'expectedPassiveSource', $ExpectedPassiveSource) }
if ($ExpectedStationarySpeed) { $receiverArguments += @('-e', 'expectedStationarySpeed', 'true') }
$receiverArguments += @('-e', 'class', 'com.sunnyfor.gpsmock.locationprobe.SystemLocationConsumerTest#runningSessionIsVisibleToSystemConsumer',
    'com.sunnyfor.gpsmock.locationprobe/androidx.test.runner.AndroidJUnitRunner')
$receiverOutput = & $AdbPath -s $Serial @receiverArguments
$receiverOutput | Write-Output
$mainPidAfter = (& $AdbPath -s $Serial shell pidof com.sunnyfor.gpsmock) -join ' '
if ($mainPidBefore -ne $mainPidAfter) {
    throw 'Main application process changed during receiver observation; result is inconclusive.'
}
$receiverText = $receiverOutput -join "`n"
if ($receiverText -notmatch 'OK \(1 test\)' -or $receiverText -notmatch 'INSTRUMENTATION_CODE: -1') {
    throw 'Independent receiver did not complete its real-time location assertion successfully.'
}
Write-Output "PASS: independent receiver obtained a fresh matching $Provider mock location; main app process unchanged."
