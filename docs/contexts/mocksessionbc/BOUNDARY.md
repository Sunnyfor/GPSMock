# 模拟会话边界

owner 为 com.sunnyfor.gpsmock.locationgrp.mocksessionbc，复杂度 L3。
MockLocationSessionAggregateRootEntity 拥有单会话状态转换、确认时间、失败原因和冻结目标。
Android Adapter 拥有实际写入结果的技术权威；聚合不自行操作 provider 或 Service。

本次实现 Starting/Running/Stopping/CleanupRequired/Stopped、回调身份校验、重复事实处理、
停止代次失效、清理重试与旧日志恢复为清理状态。根没有 Idle，也没有再次启动方法。

MockLocationSessionCoordinator 已实现唯一活动槽位、Mutex 串行保护、进程内 commandId 去重、Busy 和恢复清理。
日志持久化与 AndroidMockLocationAdapter 已接通：Starting 落盘后才准备前台服务与 gps/network 测试 provider；
全部必要 provider 的首次写入成功才确认 Running。默认 400ms 是目标间隔，实际间隔包含平台调用与调度耗时。
停止换代、取消并等待旧资源任务，再清理账本中的本方 provider；平台或日志清理失败保留 CleanupRequired 并禁止新启动。
页面调用者取消也不能中断已受理的停止清理；通知使用独立会话 PendingIntent，过期通知不能停止新会话。
前台服务为 specialUse、非导出、START_NOT_STICKY；不读取真实位置，不开机启动，不隐藏 mock 标记。

收藏来源仅保留 ID/revision 值；不导入位置库的聚合或 VO。应用边界只消费位置库公开 contract。
进程恢复不自动启动引擎；必须完成清理确认后由协调器释放活动槽位。

单元测试覆盖正常/非法状态、过期回调、重复停止、清理失败和重试、输入快照冻结与恢复校验。
新增协调器端口测试覆盖并发排他、启动中停止、不可取消准备晚返回、写入失败、日志失败和只清理不重启的恢复。
这些端口替身测试不是实际 provider 证据：2026-10-13 用户完成 MuMu 的模拟应用和通知授权后，实际 provider 写入/后台/页面停止/通知停止设备测试已通过。
真机权限撤销、进程杀死后的实际残留清理及长期后台稳定性仍需验收，详见 [验收清单](../../ACCEPTANCE.md)。

见 [领域模型](../../DOMAIN-MODEL.md)；词汇见 [CONTEXT.md](CONTEXT.md)。
