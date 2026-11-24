package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import kotlinx.coroutines.CancellationException

/**
 * 管理本方 GPS 订阅的进程内清理责任，不持有坐标或发布业务运行状态。
 *
 * 注册开始前即记录移除责任，平台部分注册后抛错也必须执行清理；
 * 移除失败保留责任供重试。调用方须串行执行，不能跨会话复用未清理的订阅。
 * 构造只保存平台操作，不立即注册；进程终止时 Binder 订阅由系统释放，无需持久化监听器。
 */
internal class MockGpsSubscriptionAdapter(
    /** 注册本方监听器的实际平台调用；成功返回不保证后台请求活跃或第三方采用位置。 */
    private val register: () -> Unit,

    /** 移除同一个本方监听器的实际平台调用，不操作其他应用的监听器。 */
    private val unregister: () -> Unit
) {
    /** 已开始注册但尚未确认移除；注册异常也不能提前清空此责任。 */
    private var needsRemoval = false

    /**
     * 为一次会话登记订阅；先取得清理责任再调用平台，异常由调用方走启动补偿。
     *
     * @throws IllegalStateException 上次订阅仍未完成移除，禁止覆盖清理责任。
     */
    fun acquire() {
        check(!needsRemoval) { "Previous GPS subscription needs cleanup" }
        needsRemoval = true
        register()
    }

    /**
     * 幂等移除本方订阅；失败返回 false 并保留责任，取消信号继续传播。
     * 返回 true 只确认本监听器已移除或从未注册，不代表其他 provider 已清理。
     */
    fun release(): Boolean {
        if (!needsRemoval) return true
        return try {
            unregister()
            needsRemoval = false
            true
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { false }
    }
}
