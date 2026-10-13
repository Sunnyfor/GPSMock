# 定位助手上下文地图

代码基准：android-ddd 1.5.0；模块方向 `:app → :core`，basePackage 为 com.sunnyfor.gpsmock。
项目命名约定（2026-10-12 用户确认）：业务模块使用 `:core`，替代技能默认的 `:domain`；
BC 内部领域层仍使用 `domain` package，业务 owner 与领域模型不变。
locationgrp 仅组织目录，不拥有公共业务对象或状态。

| 上下文 | 唯一 owner | 当前实现 | 对外能力 |
| --- | --- | --- | --- |
| 常用位置 | com.sunnyfor.gpsmock.locationgrp.placecatalogbc | 聚合、应用协调器、SQLite 原子事务及命令收据 | PositionCatalog：保存、编辑、删除、清空、列表、按 ID/revision 解析 |
| 模拟定位会话 | com.sunnyfor.gpsmock.locationgrp.mocksessionbc | 聚合、唯一协调器、日志/资源账本、Android 写入与前台宿主 | MockSession：启动、停止、重试清理、StateFlow 状态 |

模拟会话启动收藏时通过位置库公开 contract 按精确版本解析快照，由本方应用边界映射并重新校验目标值。
两个 BC 的领域代码互不导入；跨 BC 页面只调用公开 contract，不直接操作聚合、SQLite 或 provider。
生产存储是真实 SQLite；测试内存端口替身仅验证协调语义，不代替数据库与设备验收。

单 BC UI 位于 app 的对应 grp/bc/presentation；跨 BC 页面在 feature；
通用主题、适配、地图 UI 在 platform.ui；导航与对象装配在 wiring。
core 模块不依赖 app；业务层不依赖 presentation。

2026-10-12 地图选点页面位于 `feature.position`，已接通“选点→收藏/编辑/删除→启动/停止”的跨 BC UI 旅程。
在线地图可关闭；离线手输、收藏与模拟控制不依赖地图 Key 或在线隐私同意。
SDK MapView、POI 与地址查询归属 `platform.ui.mapui`；UI/SDK 中转坐标不替代 BC 的 VO，
真正调用收藏/模拟用例时在公开 contract/应用边界转换并再次校验。
地图/搜索和 Android/数据库依赖只在 app；core 的 contract/application 新增协程依赖，domain 仍只用标准库。
Key、隐私门禁与验收说明见 [UI 基础与地图接入](UI-FOUNDATION.md)、[验收清单](ACCEPTANCE.md)。

运行中的目标是会话自己的冻结值。收藏编辑/删除与会话不需要跨 BC 事务。
新启动遇 CleanupRequired 必须由应用协调器拒绝；唯一活动槽位同样属于协调器责任。

详情：[领域模型](DOMAIN-MODEL.md)、
[位置库边界](contexts/placecatalogbc/BOUNDARY.md)、
[会话边界](contexts/mocksessionbc/BOUNDARY.md)。
