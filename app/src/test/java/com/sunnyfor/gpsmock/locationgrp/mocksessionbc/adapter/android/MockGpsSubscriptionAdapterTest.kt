package com.sunnyfor.gpsmock.locationgrp.mocksessionbc.adapter.android

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

/**
 * GPS 订阅清理责任回归，使用平台操作替身覆盖失败与重试，不依赖 Android 运行时。
 * 此组只验证资源生命周期，不能作为微信位置稳定或真实平台注销成功的证据。
 */
class MockGpsSubscriptionAdapterTest {
    /** 未注册时释放和构造都不得触发平台调用。 */
    @Test fun noSubscriptionNeedsNoPlatformCleanup() {
        val calls = mutableListOf<String>()
        val subscription = MockGpsSubscriptionAdapter({ calls += "register" }, { calls += "remove" })
        assertTrue(subscription.release())
        assertTrue(calls.isEmpty())
    }

    /** 一次注册对应一次移除，重复释放无额外平台调用，清理后允许下一会话使用。 */
    @Test fun successfulCleanupIsIdempotentAndAllowsNextSession() {
        val calls = mutableListOf<String>()
        val subscription = MockGpsSubscriptionAdapter({ calls += "register" }, { calls += "remove" })
        subscription.acquire()
        assertTrue(subscription.release())
        assertTrue(subscription.release())
        subscription.acquire()
        assertTrue(subscription.release())
        assertEquals(listOf("register", "remove", "register", "remove"), calls)
    }

    /** 平台可能先注册再抛错；仍须承担移除责任，不能遗留监听器。 */
    @Test fun registrationFailureStillRequiresCleanup() {
        var removals = 0
        val subscription = MockGpsSubscriptionAdapter({ throw SecurityException("Registration rejected") }, { removals++ })
        assertThrows(SecurityException::class.java) { subscription.acquire() }
        assertTrue(subscription.release())
        assertEquals(1, removals)
    }

    /** 首次移除失败不得接受新订阅；重试成功后才释放原有责任。 */
    @Test fun failedRemovalBlocksReplacementUntilRetrySucceeds() {
        var registrations = 0
        var removals = 0
        val subscription = MockGpsSubscriptionAdapter({ registrations++ }, {
            removals++
            if (removals == 1) throw IllegalStateException("Transient cleanup failure")
        })
        subscription.acquire()
        assertFalse(subscription.release())
        assertThrows(IllegalStateException::class.java) { subscription.acquire() }
        assertEquals(1, registrations)
        assertTrue(subscription.release())
        subscription.acquire()
        assertTrue(subscription.release())
        assertEquals(2, registrations)
        assertEquals(3, removals)
    }

    /** 注册取消不能吞掉清理责任；释放取消同样传播且允许稍后重试。 */
    @Test fun cancellationPropagatesWithoutLosingRemovalResponsibility() {
        var removals = 0
        val subscription = MockGpsSubscriptionAdapter({ throw CancellationException("Cancelled registration") }, {
            removals++
            if (removals == 1) throw CancellationException("Cancelled removal")
        })
        assertThrows(CancellationException::class.java) { subscription.acquire() }
        assertThrows(CancellationException::class.java) { subscription.release() }
        assertTrue(subscription.release())
        assertEquals(2, removals)
    }
}
