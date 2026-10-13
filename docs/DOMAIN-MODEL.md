# 定位助手领域层

> 本文下方保留领域搭建与改名时的阶段记录，并非最新功能缺失清单。
> 2026-10-12 已接通公开契约、应用协调器、SQLite 收藏/收据、会话日志、Android 引擎与前台服务。
> 最新实现边界见 [上下文地图](CONTEXT-MAP.md)，设备结果与待验项见 [验收清单](ACCEPTANCE.md)。
> core 的 domain 仍无框架/I/O 依赖；contract/application 使用 kotlinx-coroutines-core 1.9.0。

本次根据 `location-assistant-handoff-20261010.zip` 的 HANDOFF、DOMAIN-MODEL、CONTEXT-MAP 与
REQUIREMENTS 创建可编译的纯 Kotlin 领域层。以交接时 android-ddd 1.4.0 为目录与命名基准。
旧资料中的 `:business` 模块和 `.business.` 前缀不继续使用；当前结构为 `:app → :core`。
2026-10-12 按用户要求将业务模块由 `:domain` 改名为 `:core`，同步移动源码与测试目录。
这是本项目对技能默认模块名的明确覆盖；BC owner、内部 `domain` package 与业务行为保持不变。

本次只创建聚合与值对象，没有定位注入、存储、服务或可调用的 UI 业务接口。
聚合及内部类型使用 `internal`，后续 contract/application 在同一 core 模块内发布公开业务能力。
不存在独立子实体，也没有空的 Service、Repository、UseCase 或预建空层。

## 实际目录与角色

```text
core/src/main/kotlin/com/sunnyfor/gpsmock/locationgrp/
  placecatalogbc/domain/savedlocationaggr/
    SavedLocationAggregateRootEntity.kt
    valueobject/
      LocationName.kt
      GeographicCoordinate.kt
      CoordinateSystem.kt
  mocksessionbc/domain/mocklocationsessionaggr/
    MockLocationSessionAggregateRootEntity.kt
    SessionFactResult.kt
    valueobject/
      MockLocationTarget.kt
      MockLocationCoordinate.kt
      CoordinateSystem.kt
      MockLocationSessionState.kt
      SessionCallbackIdentity.kt
      SessionFailureReason.kt
```

| 中文术语 | 角色 | 完整代码名 |
| --- | --- | --- |
| 常用位置 | 聚合根实体 | com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.SavedLocationAggregateRootEntity |
| 位置名称 | VO | com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.LocationName |
| 地理坐标 | VO | com.sunnyfor.gpsmock.locationgrp.placecatalogbc.domain.savedlocationaggr.valueobject.GeographicCoordinate |
| 模拟定位会话 | 聚合根实体 | com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.MockLocationSessionAggregateRootEntity |
| 冻结目标 | VO | com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationTarget |
| 模拟目标坐标 | VO | com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.MockLocationCoordinate |
| 回调身份 | VO | com.sunnyfor.gpsmock.locationgrp.mocksessionbc.domain.mocklocationsessionaggr.valueobject.SessionCallbackIdentity |

坐标系、会话状态和失败原因是不可变枚举；SessionFactResult 表达事实处理结果。
两个上下文拥有各自的坐标 VO 和坐标系，不共享聚合或直接引用对方内部模型。
根的 Snapshot 是模块内部不可变领域快照，后续由 application 映射为公开 contract 投影。
这里的 `core` Gradle 模块承载业务能力；内部 `domain` 层只承载领域规则。
本次暂时只实现各 BC 的 domain package，不因模块改名而将领域层也改为 core。

## 收藏规则（L2）

- LocationName 去首尾空格，长度为 1–30 个 Unicode 码点；30 个双 UTF-16 单元的 emoji 也允许。
- 坐标包含显式 WGS84/GCJ02，必须有限且纬度在 [-90,90]、经度在 [-180,180]；(0,0) 有效。
- create 分配调用方提供的非空 ID，revision 从 1 开始；身份生成的全局唯一性由后续应用/存储层负责。
- revise 同时更新有效名称与完整坐标，保持 ID，revision 递增；溢出或参数校验失败不产生部分修改。
- restore 校验 ID、revision；重建 VO 仍通过其受控校验路径。
- 名称和坐标可以重复。删除归属存储用例，根没有假造删除或运行状态。
- 旧 Snapshot 不受后续 revise 影响。

## 会话规则（L3）

```text
Starting → Running → Stopping → Stopped
Starting → Stopping
Stopping → CleanupRequired → Stopping
```

新根只处于 Starting。confirmStarted 只能接受匹配任务身份的实际准备/首次写入确认；
根不自行验证 Android 操作，未来 Engine Adapter 必须保证该事实代表全部必要步骤成功。
confirmWrite 只在 Running 更新非递减的单调时钟确认时间，不通过墙上时间判断运行事实。

目标由不可变 MockLocationTarget 持有，包括原坐标系和可选来源 ID/revision；二者必须一起出现。
手输目标允许没有地址/名称。收藏的后续修改或删除不影响会话已经持有的值。
本次不实现位置库 contract 或 ACL，不把这个 VO 单元测试称为跨 BC 存储集成验收。

每个回调携带 sessionId/generation。进入停止与重试清理时 generation 递增，使此前任务回调失效。
调用方收到返回身份后，以新身份启动清理任务；它仍须追踪同一 session 历代实际创建的所有资源。
重复停止/正在执行的清理重试沿用当前身份，不重复推进代次。

confirmStopped 只接受 Stopping 的当前任务确认。清理失败进入 CleanupRequired；
普通停止、旧清理结果或未经重试的成功回调都不能把它清空。补偿清理保留原启动/引擎失败原因。
Stopped 是根终态，不允许同一个根重新启动；UI Idle 应由“协调器没有活动根”映射得到。

recoverForCleanup 验证旧快照。所有非终态记录恢复为新的 Stopping 代次与 Interrupted 原因，
清空旧写入时间，不能恢复 Running；已 Stopped 的记录保持终态。
该方法仅生成领域恢复状态，后续应用层和 Android Adapter 负责实际清理与日志落盘。

## 边界与尚未实现的责任

两个聚合没有 Android、Compose、Room、地图、DI、CoroutineScope、线程或 I/O 依赖。
构造/命令的非法调用使用标准参数/状态异常；事实处理返回类型化结果，不更改非法状态。
未来 application 将异常映射为公开契约错误，而非把内部异常直接显示在 UI。

MockLocationSessionCoordinator 尚未实现。全局唯一活动槽位、Busy、命令去重、串行访问、
运行日志、启动补偿任务与实际清理，都不能由单根单元测试证明。
位置库 expectedRevision 比较、写入与去重同事务、NotFound/Conflict/StorageFailure 也尚未实现。
GCJ02 → WGS84 转换一次属于后续 Adapter，不在领域 VO 内猜测或转换坐标。

## 验证入口

```powershell
$env:JAVA_HOME = 'D:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
node 'C:\Users\ZhangYe\.codex\skills\android-ddd\scripts\check-boundaries.mjs' --root . --config architecture/boundaries.json
```

架构扫描配置覆盖 app 生产/调试 UI 和 core 生产 Kotlin；它是 import/限定引用词法预检，
不替代符号分析、传递依赖分析或 DDD 语义审查。测试位于 core/src/test/kotlin。
Gradle 沿用项目 Kotlin 版本，core 的 Java/Kotlin 字节码目标均为 11，生产依赖只有 Kotlin 标准库。

模型边界见 [CONTEXT-MAP.md](CONTEXT-MAP.md)。

## 验证结果（2026-10-12）

- 领域层首次搭建：39 项领域测试通过；app：既有 11 项单元测试通过。
- Debug APK 构建与 Android 设备测试代码编译通过。此次未执行真机定位或设备测试。
- lintDebug：0 错误、13 个警告，均为模板的重复 label、未使用 XML 色值及依赖/插件升级提示。
- 词法边界预检：覆盖 21 个生产/调试 Kotlin 文件、2 个 BC，0 违规，0 未覆盖项。
- 首次运行依赖检查：业务模块只有 Kotlin stdlib 2.2.10 及其 annotations 依赖，无 Android/Compose/数据库/网络框架。
- 人工审查：聚合根使用完整 AggregateRootEntity 名称，VO 属于各自 aggr/valueobject；
  app → 业务模块单向依赖，BC 之间没有内部对象引用，没有空的业务层或子实体。
- 未实现：公开 contract/application、唯一活动协调器、持久化事务/去重、引擎 Adapter、实际定位与后台运行验收。

## core 改名与中文注释样稿验证（2026-10-12）

- `:domain → :core` 为目录与 Gradle 模块机械迁移，package、应用 ID 和业务行为不变。
- 聚合根、名称值对象、会话状态枚举各补充一份中文 KDoc 样稿，完整规范见
  [中文注释样式提案](COMMENT-STYLE-PROPOSAL.txt)。样式尚待用户确认，技能尚未修改。
- `:core:test`：实际执行 39 项测试，0 失败、0 错误。
- `:app:testDebugUnitTest`：实际执行 11 项测试，0 失败、0 错误。
- Debug APK、设备测试 APK 构建及 lintDebug 通过；未执行设备测试或真机定位。
- core 运行依赖仍只有 Kotlin stdlib 2.2.10 与 annotations 13.0。
- 更新扫描根后，词法边界预检覆盖 21 个生产/调试 Kotlin 文件、2 个 BC，0 违规，0 未覆盖项。
  此结果不代表完整的符号、传递依赖或运行时隔离验证。
