# 常用位置边界

owner 为 com.sunnyfor.gpsmock.locationgrp.placecatalogbc，复杂度 L2，本地拥有收藏规则。

本次领域实现：SavedLocationAggregateRootEntity、LocationName、GeographicCoordinate 和 CoordinateSystem。
名称规范化为 1–30 个 Unicode 码点，坐标有限且范围有效；身份稳定、revision 正向递增。
没有子实体、运行状态字段、数据库依赖或跨 BC 内部模型引用。

未来应用操作：保存/修改/删除/解析；写入权威为本地存储事务，UI 只读取 contract 投影。
expectedRevision 比较、命令去重与写入同事务由后续 application/storage Adapter 保证，当前未实现。
模拟会话只能通过未来公开 contract 查询快照。收藏更新不修改运行目标。

测试覆盖值校验、恢复、版本递增、身份稳定、快照冻结与失败原子性；
持久化并发冲突、重复命令及删除运行目标的集成行为待后续用例/Adapter 测试。

见 [领域模型](../../DOMAIN-MODEL.md)；词汇见 [CONTEXT.md](CONTEXT.md)。
