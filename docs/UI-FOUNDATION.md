# 主题与设计稿适配

本次只落地通用 UI 基础能力，不实现定位业务，也不改变应用 ID、SDK 或依赖。
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
分类不自动重排：页面明确选择手机底部面板、宽屏侧面板等布局。调试预览提供纵向/左右两种排列；运行入口仍为模板示例。

## Compose、原生 View 与触控

覆盖 LocalDensity 仅影响该 Compose 子树的尺寸换算，不修改 Android resources.displayMetrics。
Material 默认尺寸仍使用逻辑 dp/sp，因此保持常规组件单位。
缩小时通过 LocalMinimumInteractiveComponentSize 为 Material 控件保留至少系统 48dp 的布局空间；
自定义 clickable 组件同样需要保留足够空间，不能强制用更小的固定外部约束压缩触控区域。

原生 View / 地图 SDK 内部仍使用系统密度。可通过 `SystemDensityContent` 恢复包装内容的系统密度；
地图自身 padding 等参数仍需按 SDK 的单位显式换算。系统权限弹窗、键盘不受适配影响。
安全区在适配外处理，避免对状态栏或导航栏尺寸重复缩放。

## 外观约定

保留 Bidder 的品牌蓝 #3461FF、页面底色 #F6F8FC 和中性文字层级。
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

## 本次验证结果（2026-10-12）

- `testDebugUnitTest`：11 项通过，包括本次新增 10 项。
- `assembleDebug`：通过，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `assembleDebugAndroidTest`：通过，新增 Compose 测量与触控空间测试可编译。
- `lintDebug`：通过，0 错误；12 个警告均位于既有模板配置（重复 Activity label、依赖版本、未使用 XML 色值）。
- 目录与依赖审查：通用代码归属 platform.ui，只依赖 UI 框架与本平台包，无 BC、数据层或领域依赖。
- 实际设备测试、截图验收：未执行；adb 无连接设备。
- 宽屏实际业务页面、原生地图接入：本次不实现；分类与可配置基准供后续对应设计稿使用。
