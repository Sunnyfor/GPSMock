# 主题与设计稿适配

首轮落地通用 UI 基础能力；2026-10-12 后续接入高德地图与目标选择页面。
应用 ID 保持不变，已接入收藏与模拟控制；最新业务结果和待验项见 [验收清单](ACCEPTANCE.md)。
下方首轮结果保留为历史阶段记录；当前已增加前台服务、通知和限时唤醒锁权限，仍不读取真实设备位置。
代码由模板的 `ui.theme` 迁移到 `platform.ui.theme`；尺寸适配归属 `platform.ui.adaptation`。
这是 android-ddd 1.4.0 的通用 UI 目录约定，不是领域模型变更。

## 尺寸约定

用户选择严格按设计稿比例缩放，并为不同窗口设计不同布局。采用逻辑设计单位：

```text
适配密度 = 当前容器可用宽度 px / 当前布局的逻辑设计宽度
元素实际 px = 元素设计单位 × 适配密度
```

手机默认基准 360，采用 Android 新项目的逻辑设计宽度；通过 `DesignWidthPolicy` 可配置其他设计基准。
1080px 三倍稿按 360 逻辑宽度处理，300px 尺寸写成 100.dp；两种计算产生相同像素比例。
设计导出倍率必须明确，不能仅根据“1080p”自动推断为三倍稿。

`DesignScaledLayout` 接收父布局实际分配的像素约束，不读取物理屏幕宽度。
放在 Scaffold / 安全区域 padding 之后、滚动容器之外，并提供有限宽高。
父级 modifier 的 dp 使用父级密度，content 内的 dp/sp 使用适配密度。
字体保留系统偏好及 Android 的非线性 sp 转换；默认字体设置还原比例，用户放大字体时允许换行和增高。
因此内容面板避免固定文字高度，需要时滚动。

按原始系统密度判定容器宽度后选择基准，防止所有窗口都被归一化成 360 后无法分类：

| 窗口系统宽度 | 分类 | 暂定逻辑基准 |
| --- | --- | --- |
| < 600dp | Compact | 360 |
| 600–839dp | Medium | 600 |
| 840–1199dp | Expanded | 840 |
| 1200–1599dp | Large | 1200 |
| ≥ 1600dp | ExtraLarge | 1600 |

宽屏基准是可配置的工程起点，不代表已有对应的产品设计稿。后续实际设计稿提供后按其逻辑宽度更新。
`isCompactHeight` 单独表达系统可用高度 < 480dp，供横屏/低窗口选择布局。
分类不自动重排：运行选点页面明确采用窄窗口上下布局、宽窗口左右布局。
表单独立滚动，低高度窗口缩小地图分区比例；地图原生内容恢复系统密度。

## Compose、原生 View 与触控

覆盖 LocalDensity 仅影响该 Compose 子树的尺寸换算，不修改 Android resources.displayMetrics。
Material 默认尺寸仍使用逻辑 dp/sp，因此保持常规组件单位。
缩小时通过 LocalMinimumInteractiveComponentSize 为 Material 控件保留至少系统 48dp 的布局空间；
自定义 clickable 组件同样需要保留足够空间，不能强制用更小的固定外部约束压缩触控区域。

原生 View / 地图 SDK 内部仍使用系统密度。可通过 `SystemDensityContent` 恢复包装内容的系统密度；
地图自身 padding 等参数仍需按 SDK 的单位显式换算。系统权限弹窗、键盘不受适配影响。
安全区在适配外处理，避免对状态栏或导航栏尺寸重复缩放。

## 外观约定

首轮借鉴 Bidder 配色；2026-10-13 按审核通过的 A 原型更新品牌蓝为 #366BFA、冷白底色为 #F4F7FB、主要文字为 #1C2D48。
组件使用 MaterialTheme.colorScheme 的语义角色，成功/提醒使用 LocalStatusColors。
深浅配色分别配置，正文色与背景成对管理；自动测试检查静态主题的常规文字配对对比度 ≥ 4.5:1。
壁纸动态色默认关闭，可显式启用。错误使用 Material error 角色。
Typography、Shapes、AppSpacing 集中管理，不把尺寸换算混入主题。

## 验证入口

JVM 测试覆盖不同像素宽度的同比例换算、三倍稿等价、窗口分类边界、分屏、低高度、
非线性字体换算与静态配色对比度。设备测试覆盖实际 Compose 测量、窗口宽度变化、
安全区后的宽度与 Material 触控空间。

Android Studio 调试预览位于 `app/src/debug/java/com/sunnyfor/gpsmock/platform/ui/theme/ThemePreview.kt`，
包含小手机、360 手机、大字体、横屏、宽屏及深色模式，预览样例不进入 release 源码。

## 为什么适配不直接放在主题中

`GPSMockTheme` 负责外观；`DesignScaledLayout` 使用标准 `BoxWithConstraints` 读取父容器约束，
再通过 `CompositionLocalProvider(LocalDensity provides ...)` 给后代提供适配密度。
它没有自定义 Compose 测量或排列算法，页面内部仍使用标准 Column、Row、Box 和 Material 组件。
密度注入机制与旧项目放在主题中的做法相同，只是职责分开。

将尺寸换算放在安全区/Scaffold padding 之后，可以准确缩放可用内容区域；
后续地图与操作面板也能分别选择密度范围，而主题可以继续统一覆盖整页。
只调用主题不会自动启用尺寸缩放，运行入口与预览均明确包裹 `DesignScaledLayout`。
没有连接设备时只编译设备测试，不能声称实际设备或截图验收通过。

```powershell
$env:JAVA_HOME = 'D:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest
# 连接设备后：
.\gradlew.bat connectedDebugAndroidTest
```

依据：[Android 窗口分类](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)、
[Material 主题](https://developer.android.com/develop/ui/compose/designsystems/material3)、
[触控区域](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、
[文字对比度](https://developer.android.com/guide/topics/ui/accessibility/apps)。

## A 方案原生实施（2026-10-13）

用户在 A「地图优先」原型预览后确认“按照原型进行调整吧”。完整三版原型作为一手评审资料保存在本地草稿分支
`prototype/location-assistant-ui-reviewed-20261013`，提交 `a8ba63d`；尚未推送。生产实现不包含 HTML 切换器、演示地图、样例收藏或模拟授权。

- `feature.position.PositionPickerScreen` 保留生命周期观察、隐私门禁及真实地图槽位，展示由 `AtlasPositionScreen` 实现。
- AS-IS 为一列滚动的会话/坐标/收藏/搜索卡片；TO-BE 为地图＋目标面板、固定主操作，以及「选点 / 常用 / 设置」三个目的地。
- 手输与搜索使用可滚动对话框；手输校验成功或选中真实搜索结果后返回地图。收藏选用返回首页，新建/修改/删除仍使用原公开用例与二次确认。
- 活动会话优先展示冻结目标；后续选择单独标为待选。所有目的地保留启动/停止/重试清理，清理未完成不能重新启动。
- 通知权限仍只在用户点击启动时申请；模拟授权预检查、在线隐私及详细运行边界移入设置，不以 UI 状态替代真实引擎确认。
- 浅色配色借鉴 A；深色沿用同布局，跟随系统。360 基准、系统字体偏好、安全区和地图系统密度边界保持不变；当前尺寸与浮层结构尚未完成 1:1 标定，不视为视觉还原验收通过。
- 窄窗低高度采用有界地图与目标的整体滚动；低高度宽窗使用双栏、紧凑标题/底栏和横向地图提示。主操作始终在滚动区外；小空间省略首页细则但设置中保留说明。
- 自绘矢量图标位于 `platform.ui.theme.AppGlyph`，无图片或新依赖。地图版权/Logo 保留独立可见区域，不被目标面板遮挡。
- 收藏 BC、模拟 BC 的 contract/domain、数据库、服务组件与签名配置均未变；没有数据迁移或新增业务规则。

验证结果及截图范围见 [验收清单](ACCEPTANCE.md)。

## UI 基础首轮验证结果（2026-10-12，地图接入前）

- `testDebugUnitTest`：11 项通过，包括本次新增 10 项。
- `assembleDebug`：通过，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `assembleDebugAndroidTest`：通过，新增 Compose 测量与触控空间测试可编译。
- `lintDebug`：通过，0 错误；12 个警告均位于既有模板配置（重复 Activity label、依赖版本、未使用 XML 色值）。
- 目录与依赖审查：通用代码归属 platform.ui，只依赖 UI 框架与本平台包，无 BC、数据层或领域依赖。
- 实际设备测试、截图验收：未执行；adb 无连接设备。
- 宽屏实际业务页面、原生地图接入：本次不实现；分类与可配置基准供后续对应设计稿使用。

## 高德地图与选点接入（2026-10-12）

### 已实现及边界（地图接入阶段记录）

- 主入口已替换模板问候页，页面位于 `feature.position`，SDK 封装位于 `platform.ui.mapui`，装配位于 `wiring`。
- 地图点击与搜索结果选择显示唯一目标标记；提供关键词/可选城市 POI 搜索、逆地理编码及逐字段手输校验。
- 原始目标明确记录 WGS-84 或 GCJ-02。地图点击/国内 POI 使用 GCJ-02；手输 WGS-84 仅绘制时转换，原数值不变。地址查询按 GPS/AMAP 类型传参。
- 目标只是页面内存快照，不会自动启动模拟、保存收藏或写入领域聚合。Activity 配置变更保留页面状态，进程退出不恢复目标及隐私同意。
- 门禁 Ready 表示允许初始化，不表示 Key 已鉴权或地图已加载。SDK 初始化异常有回退；网络/鉴权查询失败和空结果均显示真实提示。
- 地图支持创建、恢复、暂停、相机状态保存和幂等释放；关闭在线功能后忽略未完成查询结果，不能宣称已取消发出的请求或停止 SDK 所有后台活动。
- 保留现有静态深浅主题、语义色、间距令牌及 360 窄窗口基准。原生地图的尺寸与版权/Logo 保持 SDK 规则。

### Key 配置

默认 `AMAP_API_KEY` 为空，空 Key 可以构建，并显示“地图 Key 未配置”；此时不会创建地图/搜索 SDK 对象，手输仍可用。
在项目根目录已被 Git 忽略的 `local.properties` 中添加下列一行，保留已有 `sdk.dir`：

```properties
AMAP_API_KEY=
```

拿到 Key 后填写等号右侧，再重新构建安装。也支持 Gradle 项目属性 `-PAMAP_API_KEY=...`，其优先级高于本地文件；注意终端历史/CI 日志风险，不提交真实凭据。
Manifest 使用 `com.amap.api.v2.apikey`，最终注入的是 Android 平台 Key，而不是 Web 服务 Key。
高德控制台需要匹配 `com.sunnyfor.gpsmock` 和安装包签名 SHA-1。按用户后续要求，当前 debug / release 已改为共用 `sunnyfor` 签名配置；本地密码填写完成后，两者 SHA-1 相同。
Android Key 最终存在安装包中，不能视作服务器秘密；须使用平台包名/签名约束，不展示或记录值。

### SDK、隐私与权限

使用 Maven Central 固定发布版 `com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1`，不使用动态版本。
这是官方地图/搜索/定位合包，不是地图单包；本轮不实例化定位客户端、不声明定位服务。
依赖仅在 `:app`，`:core` 保持纯 JVM 和原有领域边界。

用户显式开启在线功能时，先显示供应方、用途、查询关键词/城市/所选坐标及 SDK 可能处理的网络/设备/应用信息，并链接完整隐私政策。
只有点击“同意并开启”后，才在各自 SDK 对象构造之前调用地图与搜索 SDK 的 privacy-show/privacy-agree 接口。
同意仅存在本次会话内，不在备份中持久化。拒绝或缺 Key 时只使用本地手输功能。

新增权限只有 `INTERNET`、`ACCESS_NETWORK_STATE`；合并 Manifest 另含 AndroidX 的本应用签名级动态接收器权限。
没有设备定位、电话、Wi-Fi 状态、外部存储或前台定位服务权限；不启用“我的位置”。地图缓存目录指向应用的 no-backup 内部目录，不保证 SDK 的其他内部文件也在该目录。

当前 SDK 原生库只提供 `arm64-v8a` / `armeabi-v7a`，APK 已明确限制这两个 ABI。优先用 ARM 真机或 ARM 模拟器。
本次 MuMu x86_64 系统有 ARM native bridge，实际运行过地图；无此兼容层的纯 x86 系统仍不能验收，APK 没有原生 x86 库。
ARM64 原生库的 ELF LOAD 对齐静态检查为 16 KB；这不替代实际 16 KB 页设备验收。

### 验收范围

新增 JVM 测试覆盖门禁组合、输入非法值/零点/边界、坐标系保留、生命周期幂等、乱序搜索/地址响应、关闭在线及 ViewModel 销毁后的回调失效。
新增设备交互测试验证空 Key 手输和隐私同意门禁，地图槽位替身只验证 UI 行为，不验证高德在线能力。
需要真实 Key 与连接设备之后才能验证：地图渲染、签名鉴权、真实 POI/地址返回、前后台/旋转原生资源、大小字体与窗口截图，以及 16 KB 页设备运行。

本轮验证：`core:test` 39 项、`app:testDebugUnitTest` 29 项通过（其中新增 18 项）；Debug、Release 未签名 APK 和设备测试 APK 构建通过。
`lintDebug` 为 0 错误、16 个警告：既有配置/资源/版本提示，加新增 Lifecycle 版本提示与明确限制 ARM 后的 ChromeOS ABI 提醒，不宣称所有警告都是历史项。
Release 构建还有 AndroidX 可选类型及高德未使用的旧 SupportMapFragment 父类缺失等 R8 警告，未通过全局 `dontwarn` 隐藏；本轮没有使用该 Fragment，实际发布运行仍须真机验证。
架构词法预检覆盖 29 个生产/调试 Kotlin 文件、2 个 BC，0 违规/0 未覆盖项；人工核对 app→core 和 SDK 边界，预检不等同完整符号/运行验证。
Debug 与 Release APK 的 `zipalign -c -P 16 4` 检查通过。没有 adb 连接设备，因此设备测试仅编译，真实地图/截图验收未执行。

### 共享 Debug / Release 签名配置

项目根目录的 `sunnyfor` 是用户创建的签名文件（实际 PKCS12 格式，没有 `.jks` 扩展名），别名为 `tool`；不重命名、不生成或覆盖该文件。
`app/build.gradle.kts` 创建 `sunnyfor` signingConfig，debug、release 及关联设备测试打包均使用这份证书，不保留默认 debug 签名回退。

在 Git 忽略的 `keystore.properties` 本地填写 `storePassword` 和 `keyPassword`。如果创建时两种密码相同，两项填写相同值；不要把密码贴到聊天、命令行参数或提交记录。
可复制无凭据模板 `keystore.properties.example`；`storeFile` 相对项目根目录解析，Windows 路径使用正斜杠。Properties 中的反斜杠需要转义，密码含反斜杠时须写为 `\\`；首位空格须写为 `\ `。
CI 可用 `GPSMOCK_STORE_PASSWORD` / `GPSMOCK_KEY_PASSWORD` 环境变量覆盖本地密码；密码不进行 trim，不打印。保管好本地 Gradle 缓存及 CI 环境，不上传包含凭据的缓存。

密码缺失时源码编译、Gradle 同步和 JVM 测试仍可执行，但实际签名打包会明确失败，不会生成新的默认签名 APK。
证书报告任务也检查缺失字段，避免未填写密码时出现底层 provider 错误。已验证 debug / release 校验均明确阻止空密码，并兼容 configuration cache；真实签名与 SHA-1 验证待用户填写本地密码。
填写后执行 `./gradlew.bat :app:signingReport` 获取证书 SHA-1；debug 和 release 两个条目应指向同一文件、别名及指纹，可用于高德两个安全码字段。
地图接入首轮生成的 unsigned release / 默认 debug APK 属于历史产物，不代表新配置已经签名；需要重新构建 `:app:assembleDebug :app:assembleRelease` 后验证最终 APK 证书。

若设备已安装旧默认调试证书签名的同包名 APK，新证书不能覆盖安装；用户可选择先卸载再安装，卸载会清除应用数据，本轮不自动执行。
共用证书也意味着 debug 包具有该签名的身份；不要对外发布 debuggable 包。签名文件及密码应另行安全备份，避免丢失后无法更新安装包。
Google Play 若使用托管应用签名，商店最终安装包可能使用不同证书，应以实际分发证书另配高德指纹。

依据：[Android 官方签名与本地凭据配置](https://developer.android.com/studio/publish/app-signing)。

### 收藏与模拟控制补全（当前状态）

页面保留现有主题与 360 逻辑基准，新增会话状态/控制、收藏列表、编辑及删除确认；地图和表单可分别滚动/布置。
运行目标来自 Application 唯一协调器，与页面可随时更换的待选目标独立；界面不能根据按钮点击自行显示 Running。
离线手输/收藏/模拟控制不依赖地图门禁。在线同意仍为会话级，不因收藏或前台服务启动而默认同意高德隐私。
新增 FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS、WAKE_LOCK，不新增真实设备定位权限。
ACCESS_MOCK_LOCATION 仅作系统模拟应用候选声明，不静默授予 AppOps；真实授权仍需用户在开发者选项选择。
allowBackup=false，旧/新备份规则同时排除收藏、收据、日志和内部文件；不会将模拟目标恢复为开机或进程自动运行。
本地 Key/密码已由用户填写并完成共享证书签名构建；不在源码、文档、日志中展示凭据。
最新测试数字、真实设备限制与验收步骤统一见 [ACCEPTANCE.md](ACCEPTANCE.md)，不以早期“设备未连接”记录代表当前结果。

依据：[官方 Android Studio 集成](https://lbs.amap.com/api/android-sdk/guide/create-project/android-studio-create-project)、
[Maven 发布版本](https://repo.maven.apache.org/maven2/com/amap/api/3dmap-location-search/maven-metadata.xml)、
[MapView 生命周期](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/3D/com/amap/api/maps/MapView.html)、
[地图隐私初始化](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/3D/com/amap/api/maps/MapsInitializer.html)、
[POI V2 查询](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/poisearch/PoiSearchV2.html)、
[高德完整隐私政策](https://lbs.amap.com/pages/privacy/)。
