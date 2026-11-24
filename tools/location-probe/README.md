# 独立定位接收端

这是仅供设备验收的 `com.android.test` 自插桩 APK，不是生产功能模块。
生成的 APK 不进入定位助手安装包，运行它不会重启定位助手，也不能访问其私有数据。
生产依赖仍只有 `:app → :core`。新增测试模块的理由是需要真实的独立 UID/进程，
而普通 `app/androidTest` 会插桩主应用，无法充当外部接收端。

## 验证边界

- 用户须先主动启动模拟。本工具只读取公开系统定位接口，不自行注入或启停模拟。
- 以 GPS 的当前模拟目标为跨通道参照，订阅选定通道，等待订阅开始之后的新模拟回调。默认 gps，可选 network/passive/fused 或独立的 Google FLP。按参考 APK 实现的主应用直接写入 gps 和 network，融合通道用于额外观察。
- 比较位置只在内存中进行，不输出经纬度、不保存轨迹、不读取收藏或模拟数据库。
- 系统通道通过只证明系统接口实时接收一致；googleflp 通过才证明 Google FLP 的该次实时接收一致。两者都不证明坐标转换正确或微信、飞书及任意第三方 SDK 接受位置。
- 由普通前台页面申请精准定位，用户可拒绝或选择仅此一次；定位读取始终使用接收端自身 UID，不采用 shell 定位权限，也不改变模拟应用选择。
- 测试仅通过 shell 等价的 `am start` 打开本方导出的普通页面，规避测试进程从后台启动页面被 OEM 拒绝的问题；不修改系统安全设置，不自动批准权限。页面也可从桌面图标打开。
- 诊断 APK 安装要求 API 24+（Google Play 定位 SDK 21.4.0 要求）；不改变生产应用最低系统版本。系统 fused 验收还要求该提供者存在，googleflp 验收要求 Google Play 服务可用。开始订阅前检查 GPS 模拟缓存的单调时间年龄不超过 5 秒，过期表明主写入当前不活跃，不能误报融合接收失败。

自插桩与独立进程依据 [AOSP 自插桩说明](https://source.android.com/docs/core/tests/development/instr-self-e2e)。
Android 系统 fused 与 Google Play 服务 FLP 是不同 API，详见
[LocationManager](https://developer.android.com/reference/android/location/LocationManager#FUSED_PROVIDER) 和
[FusedLocationProviderClient](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#setMockMode(boolean))。

## 构建与手动会话验收

在仓库根目录运行，按当前连接设备更新 serial。安装若被手机拒绝，交给用户确认或手动安装；不绕过安全设置。

```powershell
.\gradlew.bat :location-probe:assembleDebug
$probeSerial = '<adb devices 中当前连接的设备序列号>'
$probeAdb = 'D:/Android/Sdk/platform-tools/adb.exe'
& $probeAdb -s $probeSerial install -t -r tools/location-probe/build/outputs/apk/debug/location-probe-debug.apk
./tools/location-probe/run-receiver-check.ps1 -Serial $probeSerial -AdbPath $probeAdb
# 对照外部应用实际请求的系统通道，不改变模拟目标或提供者。
./tools/location-probe/run-receiver-check.ps1 -Serial $probeSerial -AdbPath $probeAdb -Provider network
./tools/location-probe/run-receiver-check.ps1 -Serial $probeSerial -AdbPath $probeAdb -Provider passive
# 检查一轮写入后 passive 缓存持续保留的来源，而不只检查坐标。
./tools/location-probe/run-receiver-check.ps1 -Serial $probeSerial -Provider passive -ExpectedPassiveSource gps
# 只读 Google FLP，独立于系统 fused；不调用其 setMockMode/setMockLocation。
./tools/location-probe/run-receiver-check.ps1 -Serial $probeSerial -Provider googleflp
```

包名与插桩 targetPackage 均为 `com.sunnyfor.gpsmock.locationprobe`，避免误将主应用作为插桩目标。
此模块使用内部测试签名；用户指定的主应用 Debug/Release 签名配置不变。
出现权限弹窗时由用户选择精准位置；拒绝、大致定位或超时属于验收前置条件未满足，不是引擎故障。
脚本要求实际 `OK (1 test)`、成功插桩结束码和主进程 PID 不变；不能仅根据 adb 退出码判定通过。
前台页面仅可见期间保持亮屏，不解锁手机；JUnit 超时为 60 秒，权限等待为 40 秒，实时回调等待为 8 秒。
同一设备的接收端命令须串行执行：插桩启动会强制结束该测试包的上一次运行；并行运行导致的取消/崩溃不是定位引擎证据。
读取前还等待权限决定完成且测试窗口取得前台焦点；仅凭 onCreate 不能证明前台位置权限已可用。

## 微信实际页面对照

由用户先打开微信“发送位置”并保持解锁。不点击发送、不读取聊天、不启动模拟。
使用 Windows 内置中文 OCR，不安装额外依赖或上传图片；截图只在忽略的 `app/build/ui-review` 中保存。
前台不是发送位置页或设备锁屏时停止；识别不确定不能记作通过。区域文字匹配还须人工核对地图和选点。

```powershell
# 传入本次测试的目标区域匹配式。可用 Unicode 转义避免终端代码页问题。
./tools/location-probe/verify-wechat-switch.ps1 -Serial $probeSerial -ExpectedPattern '<目标区域匹配式>' -Rounds 3
# 不切换应用，独立观察“过几秒又回真实位置”，不能用频繁切换掩盖这个问题。
./tools/location-probe/verify-wechat-switch.ps1 -Serial $probeSerial -ExpectedPattern '<目标区域匹配式>' -Rounds 0 -StillSamples 8 -StillIntervalSeconds 5
```

`check-location-image.ps1` 需 Windows PowerShell 5 的 WinRT，观察脚本显式按 UTF-8 加载它。
可传入 `-KnownWrongPattern` 识别本次测试已知的错误区域。省略该参数时，未匹配到目标文字的截图会记为不确定。
OCR 只证明画布/候选地点中目标区域的可见情况，不证明微信内部真实位置、距离计算或实际发送内容；本轮不发送。

仅 Debug APK 包含 `MockSessionDiagnosticReceiver`，Manifest 以系统 DUMP 权限限制调用方，Release 已验证不包含组件。
升级会终止本应用原会话；测试时显式保存用户当前冻结的输入，再通过公开启动契约恢复同一目标。
不是开机/进程恢复自动模拟，不抢占已有会话、不改收藏或权限。恢复协议只返回状态，不打印坐标；不对普通应用开放。

## 已知零点的跨进程回归

仅在专用测试设备、主应用没有活动会话、用户已选择定位助手为模拟应用且允许测试安装时运行。
不能在用户正在使用的会话中启动普通主应用插桩；它会重启主应用。

构建并安装主应用、app/androidTest 与接收端后，在一个终端运行：

```powershell
& $probeAdb -s $probeSerial shell am instrument -w -r -e externalReceiver true -e class 'com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.MockSessionDeviceTest#holdZeroSessionForExternalReceiver' com.sunnyfor.gpsmock.test/androidx.test.runner.AndroidJUnitRunner
```

该测试通过公开业务接口启动 WGS-84 `(0,0)`，退后台保持 30 秒，然后在 finally 停止清理。
等模拟进入 Running 后，在另一终端于这 30 秒内执行接收端命令，两个终端的结果都必须通过。
普通测试集不会主动执行这段等待；不带 externalReceiver=true 时该场景跳过。

设备插桩可单独检查两路系统通道同轮样本和能力声明：

```powershell
& $probeAdb -s $probeSerial shell am instrument -w -r -e class 'com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.MockSessionDeviceTest#actualReferenceChannelsShareMockSample' com.sunnyfor.gpsmock.test/androidx.test.runner.AndroidJUnitRunner
```

## 当前证据

2026-10-14 真机记录：

- 用户完成安装；初包缺少 Runner 导致插桩崩溃，补齐显式 Runner 依赖后消除。
- 测试进程启动页面被小米系统以 `Abort background activity starts` 拒绝；增加普通页面入口，并通过 `am start` 启动后页面与授权检查可以执行。
- 普通精准位置权限已授予。在模拟主进程长时间后台状态，8 秒接收 0 条回调；系统模拟缓存停留在约 27 分钟前，前台服务存在但本方唤醒锁显示 `DISABLED`。
- 只把定位助手返回前台、不重启或启停会话，同一 PID 恢复周期写入。接收端连续两次 `OK (1 test)`，获得订阅之后的新模拟回调，且与 GPS 模拟目标一致。
- 再次退后台期间，约 90 秒采样显示三个模拟通道时间戳停止推进。本轮新增 5 秒新鲜度前置检查并构建、安装，实际失败信息为 `GPS age=97183ms`；不是融合接收断言失败。
- 用户将本应用省电策略改为“无限制”后，同一 PID 在后台恢复写入，本方唤醒锁不再 DISABLED，UID 出现在省电白名单。新增新鲜度门禁与实时 fused 回调验收立即通过；未自动修改任何省电或安全设置。
- 随后约 186 秒、13 次后台采样中，三个模拟通道时间戳持续推进；结束后 fused 复验 `OK (1 test)`。再串行执行 gps、passive、network，各自 `OK (1 test)`，所有测试期间主进程 PID 不变。该结果支持省电限制是本次后台停顿的原因，不代表锁屏/数小时后台已通过。
- 微信重新进入发送位置页面后仍显示原地点。只读核对活动会话仍为原先冻结的 GCJ-02 目标，状态为 Running，显示地点与目标不一致。系统记录其重新订阅 GPS/passive。
- 用户补充：先前在定位助手与微信之间来回切换，偶尔显示设定位置。这与切回前台后恢复写入的对照一致，但不能因此排除独立的缓存/定位来源竞争问题。

当前系统四通道的短时跨进程接收通过，后台停顿通过用户省电配置解除；微信原问题仍在。
飞书本轮未重验。不能把这些测试或 JVM 测试数量当成“微信/飞书已兼容”的证明。

### 后续兼容性试验（尚未解决原问题）

- 用户明确补充：在助手与微信间频繁切换可暂时显示目标，停留几秒后又回到真实位置。
- 来源检查实际红测为 `expected=gps, actual=fused`。候选调整为 network → fused → gps；新包安装并恢复同一冻结目标，passive 来源稳定性与四个系统通道实时接收均通过。这个结果只证明本方来源顺序改变，不证明微信兼容。
- 微信对照命令退出 1，四次截图依次为 PASS/PASS/FAIL/PASS，失败时三个模拟时间戳年龄均约 0.2 秒。人工核对发现目标区域和原区域交替出现。
- 为固定目标添加 GPS 的显式静止速度 0m/s，仅作为受控试验；不虚构未知海拔、航向或卫星，不隐藏 mock 标记。原包速度字段存在性断言失败，新测试包的该断言及实时 GPS 回调通过。微信停留对照因手机锁屏/页面不在前台未执行完成，不能称速度参数解决了问题。
- APK 编译和 App 33 项 JVM 测试通过；Release 合并 Manifest 不包含 Debug 恢复入口。未提交或推送。
- 用户随后反馈点击微信定位图标又回到原位置；该入口不能算通过。已人工核对前述失败截图确实位于原区域。16:53 的不切换连续观察再次为 FAIL/FAIL/FAIL/FAIL，三个系统模拟通道年龄均为 0.22–0.31 秒，截图确实停在原区域；不能再把此失败归因为本方停止周期写入。源码撤回 GPS-last 顺序和静止速度两项未解决原问题的试验，保留已接通的系统 fused 通道。
- Google FLP 只读接收入口已编译并覆盖安装到独立测试 APK；依赖只在诊断模块中使用，不进入主应用，不注入 Google 模拟模式。最初因锁屏未执行；设备解锁后串行执行 `-Provider googleflp`，实际 `OK (1 test)`、`INSTRUMENTATION_CODE: -1`，收到订阅之后与 GPS 目标相同的模拟回调，主进程 PID 不变。没有发现该次 Google FLP 接收缺口，故不向生产引擎盲目添加 Google 模拟依赖。此结果不是微信定位按钮验收；其时微信已退出发送位置页。GMS 服务快照也未见微信定位服务绑定的直接证据。
- 撤回参数尝试后的 Debug 构建和 App 33 项 JVM 测试再次通过；源码边界词法预检 52 个 Kotlin 文件、0 违规。已先只读核对当前 Running 目标与先前用户输入完全一致，再覆盖安装撤回后的主包并显式恢复同一目标，返回 RUNNING；不修改收藏或其他应用。安装后的接收端复验因再次锁屏、窗口未取得焦点而未完成，不算通道失败或通过。脚本增加启动前锁屏门禁，避免重复无意义超时；没有解锁或修改系统休眠设置。
