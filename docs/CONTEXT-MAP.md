# 定位助手上下文地图

代码基准：android-ddd 1.4.0；模块方向 `:app → :core`，basePackage 为 com.sunnyfor.gpsmock。
项目命名约定（2026-10-12 用户确认）：业务模块使用 `:core`，替代技能默认的 `:domain`；
BC 内部领域层仍使用 `domain` package，业务 owner 与领域模型不变。
locationgrp 仅组织目录，不拥有公共业务对象或状态。

| 上下文 | 唯一 owner | 当前实现 | 后续对外能力 |
| --- | --- | --- | --- |
| 常用位置 | com.sunnyfor.gpsmock.locationgrp.placecatalogbc | 名称/坐标 VO 与收藏聚合 | 保存、编辑、删除、观察、按 ID/revision 解析只读快照 |
| 模拟定位会话 | com.sunnyfor.gpsmock.locationgrp.mocksessionbc | 目标 VO、会话状态机与恢复清理状态 | 启动、停止、重试清理、观察当前会话 |

未来模拟会话在启动收藏时，通过位置库公开 contract 解析快照，由本方 ACL 转换成目标值。
当前两个 BC 的领域代码互不导入，也没有建立假的查询接口或内存存储来冒充持久化实现。
公开 contract 与 application 下一步按上述真实用例创建；UI 只引用 contract。

单 BC UI 位于 app 的对应 grp/bc/presentation；跨 BC 页面在 feature；
通用主题、适配、地图 UI 在 platform.ui；导航与对象装配在 wiring。
core 模块不依赖 app；业务层不依赖 presentation。

运行中的目标是会话自己的冻结值。收藏编辑/删除与会话不需要跨 BC 事务。
新启动遇 CleanupRequired 必须由应用协调器拒绝；唯一活动槽位同样属于协调器责任。

详情：[领域模型](DOMAIN-MODEL.md)、
[位置库边界](contexts/placecatalogbc/BOUNDARY.md)、
[会话边界](contexts/mocksessionbc/BOUNDARY.md)。
