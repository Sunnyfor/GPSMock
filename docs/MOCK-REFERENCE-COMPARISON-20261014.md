# 参考 APK 定位实现对照

## 2026-11-24 固定位置模拟复刻

新版按参考 APK 的 network → gps 顺序注册及写入，每轮共用一个以 GPS 为来源的模拟样本。
样本含模拟精度 1m、海拔 30m、方位 1°、卫星数 7，以及新生成的墙钟和单调时间。速度字段保持未设置。
两路 provider 能力声明与参考 APK 一致，系统 fused 只用于清理旧版本账本。Android 15 AOSP 在 network 接收
GPS 来源样本时会记录来源差异事件，随后继续通过测试 provider 发布带 mock 标记的样本。设备厂商行为仍需实测。
[Android 15 Provider 管理源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/services/core/java/com/android/server/location/provider/LocationProviderManager.java#L1623)

GPS 请求沿用原版的 100ms/100m，并使用已有的大致或精准位置授权。GPS 订阅注销、资源账本清理、前台服务与
400ms 协调器循环沿用助手的生命周期。GCJ-02 输入在准备阶段转换一次，WGS-84 输入原样使用。
设备插桩核对两路同轮样本的时间、来源和字段，以及 Android 12 起可读取的能力声明。
微信验收需要在同一台手机上停留、重新进入“发送位置”并点定位按钮，观察是否再次回到原区域。

本轮 Debug APK、设备测试 APK 和独立接收端构建通过。APK 签名校验通过，App 38 项和 core 65 项 JVM
测试结果零失败，Lint 0 错误、19 警告。手机原无线地址连接超时，MuMu 原端口拒绝连接，设备插桩与微信验收待执行。

## 参考证据

参考为用户提供的 `base(1).apk`，应用“模拟定位神器”，包名 `com.yuxi.fakergps`，版本 `2023.12.26`。
实测 SHA-256 为 `97BA9EAB6BCB7866196BC6BCBBA265B4190A63CE7280A839DC0DE21282993E61`，
与交接 ZIP 的 `apk-analysis/metadata.json` 相同。
交接记录明确要求基于其核心行为重新开发独立应用，并非恢复原工程或逐字复制代码。

直接读取交接包中 `apk-analysis/business-source.zip` 的业务反编译文件：

- `com/yuxi/fakergps/f/c.java`：后台服务、GPS 订阅与坐标转换调用。
- `com/yuxi/fakergps/f/d.java`：400ms 消息循环。
- `com/yuxi/fakergps/j/f.java`：测试 provider 注册、Location 字段及逐通道写入。

这些文件是静态证据，不是原 APK 在当前手机/微信版本上已稳定通过的运行证据。
未执行样本内网络地址、插件或其他指令，不复用其 Key、广告代码和资源。

## 已确认差异

| 项目 | 参考 APK | 本轮前的助手 |
| --- | --- | --- |
| GPS 请求 | 服务先注册本方 GPS 监听，100ms/100m，回调为空 | 模拟引擎没有本方 GPS 订阅 |
| 写入通道 | network → gps | gps → network → 设备已有的系统 fused |
| Location 来源 | 同一个 `Location("gps")` 写入两个通道 | 每个通道单独构造 `Location(provider)` |
| GPS 能力声明 | 要求卫星/小区，支持海拔、速度、方向；高功耗、COARSE 能力常量 | 仅要求卫星；低功耗、FINE 能力常量 |
| 输出字段 | 精度 1m，海拔 30m、方向 1°、卫星数 7 | 明确精度注入参数 5m，不填未知附加字段 |
| 调度 | AsyncTask 每约 400ms 向 HandlerThread 发消息 | 可取消协调器任务，目标间隔 400ms |
| 坐标处理 | 每轮无条件 GCJ-02 → WGS-84 | 明确输入参考系，准备时转换一次 |

差异不自动等于根因。此前 GPS-last 顺序与显式静止速度试验没有解决实际症状，已撤回。
不能因为系统接收端或 Google FLP 测试通过，就声称微信接受位置。

## 2026-10-14 首轮 GPS 订阅试验

仅增加第一行行为，不同时更改通道顺序、来源身份、能力声明、附加字段或坐标转换。

实现位于既有 mocksessionbc 的 Android Adapter，未改变公开契约、领域状态机或数据库结构：

1. 全部本方测试 provider 注册并启用后才发出 GPS 请求；不订阅真实 provider 后再覆盖。
2. 只使用用户已有精准权限，不新增权限申请、后台位置权限或更改模拟应用选择；没有权限时保留纯模拟流程。
3. 回调全部丢弃，不解析、保存或输出位置，不把回调当作本方写入或微信采用的确认。
4. 注册前记录进程内清理责任；平台部分注册后抛错仍须移除本监听器。
5. 停止先移除本订阅，再移除测试 provider；订阅移除失败保留责任并使协调器保持待清理，不继续恢复真实 provider。
6. 进程终止会使 Binder 监听消失，恢复仍按既有持久化 provider 账本清理，不自动重启模拟。

仍有明确平台边界：当前前台服务类型未改，订阅后台活跃程度受权限与系统策略约束。
依据 [LocationManager](https://developer.android.com/reference/android/location/LocationManager) 和
[前台服务类型说明](https://developer.android.com/develop/background-work/services/fgs/service-types#location)，
注册成功不能替代后台定位权限/服务条件验证；不绕过权限或照搬旧 target SDK 的假设。

## 验证结果

- `:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。
- `:app:testDebugUnitTest`：38 项，0 失败/错误；新增 5 项仅覆盖订阅责任、部分注册失败、取消传播、移除失败重试与幂等释放。
- `:app:lintDebug`：0 errors、20 warnings。
- 边界词法预检：53 个 Kotlin 文件，0 观察到的违规；不是完整符号或运行隔离证明。
- 中文注释已按技能模板核对本次新增/修改声明；没有类级 @property 代替成员就地说明。
- ADB 当前无设备；尝试原无线地址连接被拒绝。未覆盖安装本候选、未运行平台订阅/停止验收、未通过微信验收；未提交、未推送。

## 首轮 GPS 订阅试验的验收说明

升级前重新确认用户当前冻结目标与运行意图，不盲目重放旧目标。
安装后先核对本方 GPS 请求在助手退后台后是否仍活跃；如果没有活跃，本候选的假设即未成立。
再运行现有 `tools/location-probe/verify-wechat-switch.ps1` 的不切换停留观察，
并重复进入发送位置及点击定位图标；不发送位置、不打卡。
最后通过公开停止入口核对本方监听器和测试 provider 均被移除。
微信仍覆盖时撤回本候选，保持一次只验证一项差异，不累积未证实的兼容参数。
