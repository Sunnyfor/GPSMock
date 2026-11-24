package com.sunnyfor.gpsmock.platform.ui.privacy

import org.junit.Assert.*
import org.junit.Test

/** 版本匹配不等于同意：验证恢复门禁的纯值逻辑，不加载 Android 或 SDK。 */
class PrivacyConsentRecordTest {
    /** 当前版本的拒绝记录也是有效选择，但不允许地图或设备定位。 */
    @Test fun currentRejectionIsNotAnAgreement() {
        val record = PrivacyConsentRecord(decidedAtMillis = 1)
        assertTrue(record.isCurrent())
        assertFalse(record.mapAccepted)
        assertFalse(record.deviceAccepted)
    }

    /** App 或高德任一政策版本变化均使旧记录失效，普通 APK 版本不参与此判断。 */
    @Test fun changedDisclosureOrProviderPolicyInvalidatesConsent() {
        val accepted = PrivacyConsentRecord(mapAccepted = true, decidedAtMillis = 1)
        assertTrue(accepted.isCurrent())
        assertFalse(accepted.copy(appVersion = "old-app").isCurrent())
        assertFalse(accepted.copy(amapVersion = "old-amap").isCurrent())
        assertFalse(accepted.copy(decidedAtMillis = 0).isCurrent())
    }

    /** 地图同意不隐含设备位置用途同意，二者可以独立拒绝。 */
    @Test fun mapConsentDoesNotGrantDeviceLocation() {
        val record = PrivacyConsentRecord(mapAccepted = true, decidedAtMillis = 1)
        assertTrue(record.mapAccepted)
        assertFalse(record.deviceAccepted)
    }

    /** 拒绝定位用途仍保留有效地图授权，可在冷启动恢复这一明确选择而不是再次强制询问。 */
    @Test fun deviceRejectionCanBeRememberedWithoutRevokingMap() {
        val record = PrivacyConsentRecord(mapAccepted = true, deviceDecided = true, decidedAtMillis = 1)
        assertTrue(record.isCurrent())
        assertTrue(record.mapAccepted)
        assertTrue(record.deviceDecided)
        assertFalse(record.deviceAccepted)
    }
}
