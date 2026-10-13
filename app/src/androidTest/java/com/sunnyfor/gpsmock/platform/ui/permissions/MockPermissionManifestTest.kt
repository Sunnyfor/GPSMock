package com.sunnyfor.gpsmock.platform.ui.permissions

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** 检查已安装 APK 的候选声明；不静默授权或修改系统模拟应用设置。 */
class MockPermissionManifestTest {
    /** 系统开发者选项需按 ACCESS_MOCK_LOCATION 声明列出定位助手，真实授权仍由用户选择。 */
    @Test fun declaresMockLocationPickerMarker() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.ACCESS_MOCK_LOCATION"))
    }
}
