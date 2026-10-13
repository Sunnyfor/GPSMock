package com.sunnyfor.gpsmock.platform.ui.mapui

/**
 * 原生地图生命周期的幂等协调器，可脱离 Android 用回调测试。
 *
 * 所有事件须在主线程串行传入；销毁后不再恢复。关闭时先暂停，finally 保证执行销毁。
 */
internal class MapLifecycle(
    /** SDK 的恢复动作，仅从暂停状态进入活跃状态时调用。 */
    private val resumeMap: () -> Unit,

    /** SDK 的暂停动作，仅在活跃状态退出时调用。 */
    private val pauseMap: () -> Unit,

    /** SDK 的释放动作，一生最多调用一次。 */
    private val destroyMap: () -> Unit
) {
    /** 当前是否已经向 SDK 发出恢复事件。 */
    private var resumed = false

    /** 是否已经关闭；阻止 Activity 销毁与 Compose 释放重复释放资源。 */
    private var destroyed = false

    /** 激活已创建的地图；重复事件与关闭后的事件无效。 */
    fun resume() {
        if (destroyed || resumed) return
        resumed = true
        resumeMap()
    }

    /** 暂停活跃地图；重复事件与关闭后的事件无效。 */
    fun pause() {
        if (destroyed || !resumed) return
        resumed = false
        pauseMap()
    }

    /** 永久关闭地图；即使暂停失败，也尝试一次销毁。 */
    fun close() {
        if (destroyed) return
        try {
            pause()
        } finally {
            destroyed = true
            destroyMap()
        }
    }
}
