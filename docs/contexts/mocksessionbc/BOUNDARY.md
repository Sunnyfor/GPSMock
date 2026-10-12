# 模拟会话边界

owner 为 com.sunnyfor.gpsmock.locationgrp.mocksessionbc，复杂度 L3。
MockLocationSessionAggregateRootEntity 拥有单会话状态转换、确认时间、失败原因和冻结目标。
Android Adapter 拥有实际写入结果的技术权威；聚合不自行操作 provider 或 Service。

本次实现 Starting/Running/Stopping/CleanupRequired/Stopped、回调身份校验、重复事实处理、
停止代次失效、清理重试与旧日志恢复为清理状态。根没有 Idle，也没有再次启动方法。

唯一活动槽位、并发排他、commandId 去重、Busy、日志持久化、任务串行处理均归属后续
MockLocationSessionCoordinator；当前未实现这些全局能力。
引擎 prepare/inject/stop、通知和前台服务仍未接入，运行意图不能显示为实际 Running。

收藏来源仅保留 ID/revision 值；不导入位置库的聚合或 VO。后续 ACL 只消费位置库公开 contract。
进程恢复不自动启动引擎；必须完成清理确认后由协调器释放活动槽位。

单元测试覆盖正常/非法状态、过期回调、重复停止、清理失败和重试、输入快照冻结与恢复校验。
并发启动、持久化恢复与实际 provider 清理仍需应用/Adapter 测试和 Android 真机验收。

见 [领域模型](../../DOMAIN-MODEL.md)；词汇见 [CONTEXT.md](CONTEXT.md)。
