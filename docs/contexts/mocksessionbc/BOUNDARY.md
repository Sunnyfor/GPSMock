# 模拟会话边界

owner 为 com.sunnyfor.gpsmock.locationgrp.mocksessionbc，复杂度 L3。
MockLocationSessionAggregateRootEntity 拥有单会话状态转换、确认时间、失败原因和冻结目标。
Android Adapter 拥有实际写入结果的技术权威；聚合不自行操作 provider 或 Service。

本次实现 Starting/Running/Stopping/CleanupRequired/Stopped、回调身份校验、重复事实处理、
停止代次失效、清理重试与旧日志恢复为清理状态。根没有 Idle，也没有再次启动方法。

MockLocationSessionCoordinator 已实现唯一活动槽位、Mutex 串行保护、进程内 commandId 去重、Busy 和恢复清理。
Starting 落盘后，AndroidMockLocationAdapter 准备前台服务并按参考 APK 的 network → gps 顺序注册和写入。
两个通道共用同一轮带 GPS 来源的模拟样本。系统 fused 仅保留旧版本的账本清理支持。
全部必要 provider 的首次写入成功才确认 Running。默认 400ms 是目标间隔，实际间隔包含平台调用与调度耗时。
停止换代、取消并等待旧资源任务，再清理账本中的本方 provider；平台或日志清理失败保留 CleanupRequired 并禁止新启动。
页面调用者取消也不能中断已受理的停止清理；通知使用独立会话 PendingIntent，过期通知不能停止新会话。
前台服务为 specialUse、非导出、START_NOT_STICKY；不读取真实位置，不开机启动，不隐藏 mock 标记。

参考 APK 定位路径在用户已有大致或精准位置权限时，
由 Android Adapter 于测试 GPS 注册后持有本方 GPS 订阅；回调不解析或保存位置。
订阅清理责任为进程内技术状态，不属于领域运行权威；注册抛错也须补偿清理，
停止先取消订阅，取消失败保留责任且不移除测试 provider，以免残留请求激活真实 GPS。
当前使用 Android 前台服务类型 specialUse。后台请求活跃和微信效果尚待验证。
见 [参考包对照与验收](../../MOCK-REFERENCE-COMPARISON-20261014.md)。

收藏来源仅保留 ID/revision 值；不导入位置库的聚合或 VO。应用边界只消费位置库公开 contract。
进程恢复不自动启动引擎；必须完成清理确认后由协调器释放活动槽位。

单元测试覆盖正常/非法状态、过期回调、重复停止、清理失败和重试、输入快照冻结与恢复校验。
新增协调器端口测试覆盖并发排他、启动中停止、不可取消准备晚返回、写入失败、日志失败和只清理不重启的恢复。
这些端口替身测试不是实际 provider 证据：2026-10-13 用户完成 MuMu 的模拟应用和通知授权后，实际 provider 写入/后台/页面停止/通知停止设备测试已通过。
真机权限撤销、进程杀死后的实际残留清理及长期后台稳定性仍需验收，详见 [验收清单](../../ACCEPTANCE.md)。

独立接收端位于 `tools/location-probe`，为仅测试的自插桩 APK；不进入生产 APK，不依赖 core，
不持有业务状态。它通过公开 LocationManager 订阅实时回调，不以融合通道旧缓存或 Running 代替接收成功。
主应用插桩覆盖同轮 network/gps 样本与零点会话，仍需设备验证。以下融合通道结果来自 2026-10-14 旧版。
独立接收端在返回定位助手前台、主 PID 不变的条件下，两次实时 fused 回调验收通过；
长期后台时实际写入时间戳停滞，即使前台服务仍存在也不能承诺实时有效。新增 GPS 新鲜度门禁已经捕获该状态，
用户改为本应用省电“无限制”后，同一 PID 的后台写入恢复；约 186 秒采样持续更新，
新鲜度门禁和 gps/network/fused/passive 四通道跨进程实时接收均通过。
数小时/锁屏仍待验收，微信重新打开页面仍未采用目标，飞书本轮未重验；这些结果不证明第三方兼容。
详见接收端 README 和验收清单。省电设置只由用户调整，未自动改变系统配置。
历史版本的 fused 账本项仍由当前适配器清理。降级前应先完成当前会话清理。

见 [领域模型](../../DOMAIN-MODEL.md)；词汇见 [CONTEXT.md](CONTEXT.md)。
