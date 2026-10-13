# 定位助手第一版验收清单

更新时间：2026-10-13。范围依据交接包第一版需求；本轮按用户要求完成缺失功能开发与可执行测试。
本文件区分“代码已接通”“替身测试通过”和“真实设备通过”，不以测试数量替代端到端验收。

## 已接通的功能

- 在线地图、地图选点、真实 POI 搜索与逆地理编码；在线功能默认关闭，需要本次会话显式同意。
- 离线手输坐标，显式 WGS-84 / GCJ-02；零点有效，NaN/Infinity/超范围拒绝。
- 常用位置保存、选用、编辑、确认删除、确认清空；名称去首尾空格且为 1–30 个 Unicode 码点，同名允许。
- 私有 SQLite 持久化；版本冲突明确返回；收藏写入与 24 小时命令收据同事务提交。失败保留编辑草稿。
- 固定位置模拟启动/停止/重试清理、冻结目标、唯一活动会话、实际首次写入后才显示 Running。
- gps/network 测试 provider、每轮新时间字段、目标 400ms 间隔、前台服务和带会话身份的通知停止。
- 启动失败/周期写入失败的补偿清理、停止等待旧任务、清理失败阻止新启动。
- 会话日志与 provider 注册意图账本；进程恢复只清理、不自动重启。
- 开发者选项引导、模拟授权预检查、通知权限提示、默认不备份收藏/日志/收据。

不在第一版范围：轨迹、账号、云同步、广告/推广/分析、悬浮窗、开机启动、隐藏 mock 标记。

## 2026-10-12 基线验证结果

| 项目 | 实际结果 | 证据边界 |
| --- | --- | --- |
| core JVM | 65 项通过，0 失败/错误 | 39 项原有领域测试 + 26 项应用/转换测试；引擎端口为替身 |
| app JVM | 29 项通过，0 失败/错误 | 输入、门禁、异步查询失效、生命周期与 UI 基础规则 |
| MuMu Android 15 / API 35 | 14 项通过、2 项跳过 | 原有 5 项 + 收藏 UI 2 项 + 真实 SQLite 5 项 + 模拟候选 Manifest 1 项 + 未授权实际拒绝 1 项；原生成功链路因未授权跳过 |
| Debug / Release / 测试 APK | 构建通过，使用用户共享证书 | 构建不能证明所有 API/机型的运行兼容性 |
| 安装包检查 | Debug/Release 签名校验及 16KB zipalign 检查通过 | 同一 SHA-1；静态对齐不替代实际 16KB 页设备测试 |
| lintDebug | 0 错误、19 警告 | 包含依赖升级、既有资源/配置、ARM ABI、兼容/风格提示；非零警告未隐藏 |
| 架构词法预检 | 46 个 Kotlin 文件、2 BC，0 违规、0 未覆盖项 | 仅 import/限定引用扫描，不是完整符号或运行隔离证明 |
| 高德在线实测 | MuMu 已观察到地图、POI、点选地址、WGS 目标绘制、前后台返回 | 用户先前明确点击同意；不是坐标地面真值/所有生命周期场景的证明 |

10 月 12 日的 Runner `OK (16 tests)` 包含两项 assumption skip；当时原生成功链路未计为通过。10 月 13 日授权后的新结果见下节。
数据库/界面测试使用独立随机名称的测试库并清理自身文件，没有清空生产收藏，也没有操作手机。

Release 构建仍有 AndroidX 可选类型、高德未使用 SupportMapFragment 与协程调试代理等 R8 警告；
本轮没有增加全局 dontwarn 掩盖它们，尚未完成 Release 真机运行与商店发布审核。

## 2026-10-13 已授权后的原生补测

- MuMu 的 MOCK_LOCATION 已为 allow；通知权限开始时仍为 ignore / granted=false。
- 单独执行 `MockSessionDeviceTest#actualZeroInjectionContinuesInBackgroundAndStops`：1 项通过，Runner 为 `OK (1 test)`。
- 用实际 LocationManager 读取 gps/network：合法 `(0,0)` 与 mock 标记断言通过；退后台后收到新的写入确认，前台服务存在；停止后回 Idle 且服务已移除。
- 系统 location 日志观察到两种 provider 的添加、首轮/后续写入和停止时的 `removed mock provider override`；没有把测试端口替身当作这些结果。
- 最初组合测试被系统 STOP APP 中断，Runner 显示 Process crashed，但 AndroidRuntime 无 Java 崩溃栈、退出原因是 USER REQUESTED；该次不计为通过。最小场景重新运行成功，未据此修改生产代码。
- 正常页面手输零点、提交、点击启动，系统通知权限弹窗已出现。自动测试直接调用会话端口，不经过按钮，不自动申请/授予通知权限。
- 用户确认系统通知授权后，完整设备回归实际结束：15 项通过、1 项按条件跳过，0 失败；Runner `OK (16 tests)`，耗时 57.909 秒。
- `notificationStopCleansTheSameSession` 通过：读取真实前台通知的“停止模拟” PendingIntent 并执行，同一会话回到 Idle。通知权限申请与通知停止不是端口替身。
- `actualZeroInjectionContinuesInBackgroundAndStops` 在完整回归中再次通过，gps/network 实际零点、mock 标记、后台确认、前台服务及页面停止断言均执行。
- 唯一跳过的是 `knownDeniedAuthorizationNeverReportsRunning`：其前提是未授权，而本轮已授权；该负向场景已在 10 月 12 日通过，不为跑它撤销用户授权。
- 测试后系统服务查询为 `(nothing)`；location 日志再次确认 gps/network 的 mock provider override 已移除。历史/被动位置缓存不等同于仍有活动测试 provider。
- 本轮未修改生产代码，没有卸载或清空应用，没有修改模拟授权/通知设置，也未操作真机。

本轮命令（只操作 MuMu）：

```powershell
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16544 shell am instrument -w -r -e class 'com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android.MockSessionDeviceTest#actualZeroInjectionContinuesInBackgroundAndStops' com.sunnyfor.gpsmock.test/androidx.test.runner.AndroidJUnitRunner
# 用户完成通知授权后，运行完整设备回归：
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16544 shell am instrument -w -r com.sunnyfor.gpsmock.test/androidx.test.runner.AndroidJUnitRunner
```

短时原生链路通过不代表长期后台稳定性、权限撤销、实际进程恢复、真机或其他应用必然接受模拟位置。

## 2026-10-13 A「地图优先」UI 实施验收

本轮仅重排界面与调整主题，业务契约、领域、数据库、模拟引擎、签名和 Key 配置未修改。
设计来源、目录归属及原型不入库的约定见 [UI-FOUNDATION.md](UI-FOUNDATION.md)。

- `core:test` 65 项、`app:testDebugUnitTest` 29 项通过；Debug、设备测试 APK、Release 构建通过。
- `lintDebug`：0 错误、19 个警告。Release 仍保留既有可选类型 R8 警告；没有扩大屏蔽范围或升级依赖。
- 词法边界检查覆盖 48 个生产/调试 Kotlin 文件、2 个 BC，0 违规、0 未覆盖项；仍不等同编译器级隔离。
- MuMu 完整设备回归 `OK (19 tests)`，84.3 秒：18 项通过、1 项按条件跳过、0 失败。
- 唯一跳过仍为未授权负向场景；模拟授权/通知许可保持用户设置，不为测试撤销授权。
- 新增 UI 场景覆盖：运行目标冻结、跨三个目的地停止可见、清理失败不能启动、无目标禁用启动、两倍字体手输零点、280dp 低窗口滚动与固定主操作。
- 收藏流程按新导航更新交互测试：手输成功返回地图，再到「常用」新增、编辑及确认删除；真实 SQLite 存储断言保持。
- 最后调整横向紧凑地图提示后，单独重跑 `PositionPickerScreenTest`：`OK (5 tests)`，13.097 秒，5 项均通过。
- 生产入口手工验证：离线手输 `(0,0)`、点击启动、真实 Running/冻结目标/写入确认展示；切「设置」直接点击停止，回 Idle，服务查询为 `(nothing)`。没有用替身代替此按钮链路。
- 实际截图检查 720×1280 深/浅色及 1280×720 双栏低高度窗口。首次发现地图开启按钮被裁切后修正，末次宽屏截图确认开启入口、手输、常用及固定主操作完整可见。
- 尺寸测试只临时改变 MuMu 显示大小和深浅模式；结束恢复物理 720×1280、无尺寸覆盖、Night mode=yes、font_scale=1.0。未卸载、清空生产收藏或操作真机。
- 本次新增/修改的手写类型、成员、函数、枚举项与测试场景已人工核对中文 KDoc；注释覆盖不由边界脚本或编译保证。

截图位于本机临时目录（为 Android 实际 UI，不是 HTML）：

- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-light.png`：浅色首页。
- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-ready.png`：离线合法零点待选。
- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-running.png`：真实运行、冻结目标及停止按钮。
- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-settings-running.png`：设置页运行时停止入口。
- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-wide.png`：修正后的低高度双栏首页。
- `C:/Users/ZhangYe/AppData/Local/Temp/gpsmock-ui-atlas-dark.png`：系统深色首页。

运行截图来自本轮实际测试中间状态，后续只做了紧凑布局与同目标提示去重，不表示当前仍在运行。
本轮未重新代表用户同意高德隐私，在线地图保持关闭；真实在线渲染、搜索弹窗与 SDK 返回页视觉尚未在新布局下复验。
自动门禁测试使用无 SDK 地图槽位，不替代这项在线视觉验收。长期后台/锁屏、权限撤销、专项进程恢复和其他真机限制仍按前文保留。
本次生产 UI 作为阶段性实现提交，未推送远程；提交不包含原型文件。按用户后续要求，本地原型草稿分支已移除，HTML 评审文件仅保留在本机未跟踪目录，不进入生产提交或 Android 入口。

### 已知视觉还原偏差（本次提交保留，尚未修复）

用户反馈与原型并非 1:1 后，静态对照确认：原型 Logo 为 30×30，当前实现为 34×34；搜索字号由原型 13 改为 14；搜索与离线提示没有保持原型的地图浮层结构。
手机布局仍保留「实际可用宽度 px / 360」设计密度换算，但中间区域另用 52% / 48% 分配地图和目标面板，并不是按原型标定的整屏区域比例。
现有功能、输入与布局可达性测试不证明逐像素还原通过；后续须重新校准尺寸、浮层与纵向布局规则，再做同画布截图对照。
提交时 MuMu 已断开，无法重新进行设备或在线视觉复验；上面的设备结果为本轮开发期间已经执行的记录，不是提交时重新运行的结果。

## 优先验收顺序

1. 打开定位助手，保持在线地图关闭，手输 `0 / 0`、选 WGS-84 并提交。验证非法输入保留原目标。
2. 收藏目标；取消删除应保留数据。编辑后版本递增；重新打开应用应能读到收藏。
3. 开发者选项 → 选择模拟位置信息应用 → 定位助手。系统设置属于用户授权，程序不绕过。
4. 选用收藏并启动。只有实际 gps/network 首次写入全部成功后才显示“模拟中”，查看冻结目标和写入确认时间。
5. 修改待选目标或编辑/删除来源收藏，当前会话目标仍应保持原值；运行期间批量清空不可用。
6. 回桌面观察写入继续；允许通知后，从通知点“停止模拟”。停止后应回 Idle，服务/本方 provider 清理完成。
7. 启动中快速停止、重复点击启动/停止：最多一个活动会话，不允许迟到回调重新 Running。
8. 运行中撤销模拟授权：下一轮实际写入失败应停止；若清理也被系统拒绝，应显示待清理并禁止新启动。
9. 重新授权并“重试清理”，成功后才能重新启动。不要将 CleanupRequired 视为已经全部释放。
10. 在**自己的测试会话**运行时强制结束进程再打开：只恢复清理，绝不自动模拟；需检查实际 provider 残留。
11. 显式开启地图并同意隐私，搜索中文城市名或区号（如北京/010）；地图/搜索坐标是 GCJ-02，手输参考系由用户明确选择。

尚待设备验证：权限撤销、专项进程终止恢复、长时间后台/锁屏、API 23 旧分支、不同 OEM、实际 16KB 页系统，以及 Release 运行；短时真实写入/后台/页面停止/通知停止已按上节通过。
当前 MuMu 有 ARM native bridge，已经运行过本项目 ARM 高德库；这不代表 APK 自带 x86 库或所有 x86 模拟器都兼容。

## 坐标与平台限制

领域与持久化始终保留原坐标和参考系；Android 引擎准备时才将 GCJ-02 转为 WGS-84 一次，WGS-84 原样使用。
转换模型参考 MIT 授权的 [wandergis/coordtransform](https://github.com/wandergis/coordtransform)，许可保留在 APK 的 `assets/LICENSE-COORDTRANSFORM.txt`。
迭代逆解的数值残差测试不代表官方测绘精度；该模型是近似转换，矩形境内判定也不是精确国界。
注入 accuracy=5m 是固定参数，不是实际测量精度；Running 只表示本方 provider 写入成功，不保证其他应用接受模拟位置。

前台服务使用 [Android specialUse 类型](https://developer.android.com/develop/background-work/services/fgs/service-types)，不是读取真实位置的 location 类型。
无真实定位/电话/外部存储/开机权限；仅新增前台服务、specialUse、通知和运行期间限时 CPU 唤醒锁。
specialUse 的商店声明与审核尚未完成；省电/系统强杀也不承诺可无限运行。

Manifest 同时声明 ACCESS_MOCK_LOCATION，使应用能进入系统模拟应用候选列表；这不是运行时权限申请，也不自动授权。
用户仍须在开发者选项选择定位助手，最终以实际 provider 结果为准；依据 [AOSP 模拟应用控制器](https://android.googlesource.com/platform/packages/apps/Settings/+/594a5c71f56bb850fbc0a0a1e92809a73c3f9a16/src/com/android/settings/development/MockLocationAppPreferenceController.java)。
引擎对明确的授权拒绝提前中止，并在每轮写入前后复核；避免部分旧系统静默忽略未授权 void API 时误报 Running。

## 重跑命令

只针对 MuMu，避免未指定设备而误操作真机：

```powershell
$env:JAVA_HOME = 'D:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --console=plain
node 'C:\Users\ZhangYe\.codex\skills\android-ddd\scripts\check-boundaries.mjs' --root . --config architecture/boundaries.json
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16544 install -r app/build/outputs/apk/debug/app-debug.apk
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16544 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& 'D:\Android\Sdk\platform-tools\adb.exe' -s 127.0.0.1:16544 shell am instrument -w -r com.sunnyfor.gpsmock.test/androidx.test.runner.AndroidJUnitRunner
```

`MockSessionDeviceTest` 的成功写入/后台场景要求先由用户选中模拟应用；通知停止还要求允许通知，否则明确跳过。
未授权拒绝场景在 Denied 状态执行，已授权后该负向场景会跳过；它不替代成功链路。
测试只有临时读位置权限用于检查写入结果，不改变 mock 授权；finally 中停止自己的测试会话。
不要卸载、清空应用数据或提交本地地图 Key/证书密码以重跑测试。
