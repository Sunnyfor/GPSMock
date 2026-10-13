# 常用位置边界

owner 为 com.sunnyfor.gpsmock.locationgrp.placecatalogbc，复杂度 L2，本地拥有收藏规则。

本次领域实现：SavedLocationAggregateRootEntity、LocationName、GeographicCoordinate 和 CoordinateSystem。
名称规范化为 1–30 个 Unicode 码点，坐标有限且范围有效；身份稳定、revision 正向递增。
没有子实体、运行状态字段、数据库依赖或跨 BC 内部模型引用。

公开 PositionCatalog 提供保存/修改/删除/清空/列表/解析；UI 只读取不可变 contract 投影。
PositionCatalogCoordinator 在同一 CatalogStoragePort 事务内比较 expectedRevision、执行领域校验、写入内容与命令收据。
SQLiteCatalogAdapter 是 app 内本 BC 的真实持久化实现；GpsMockDatabase 只提供无业务语义的数据库宿主。
收据使用 24 小时墙钟有效期；同 commandId、同参数返回原结果，不同参数返回 CommandConflict。
清空只删除收藏，不删除未过期收据；提交或写入失败返回 StorageFailure，不能误报成功或版本冲突。
模拟会话只通过公开 contract 查询精确版本快照。收藏更新不修改运行目标。

测试覆盖值校验、恢复、版本递增、身份稳定、快照冻结与失败原子性；
应用测试覆盖版本冲突、重放、收据过期、回滚及删除运行来源后的目标冻结；
MuMu 设备测试覆盖真实 SQLite 事务、数据库重开、提交响应丢失后的重试及收藏 UI 的保存/编辑/确认删除。

见 [领域模型](../../DOMAIN-MODEL.md)；词汇见 [CONTEXT.md](CONTEXT.md)。
