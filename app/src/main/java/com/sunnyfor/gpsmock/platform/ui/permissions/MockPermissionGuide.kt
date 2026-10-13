package com.sunnyfor.gpsmock.platform.ui.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 模拟位置授权预检查；最终以实际 provider 操作为准。 */
internal enum class MockAuthorization {

    /** 当前 AppOps 允许，但随后可能撤销。 */
    Allowed,

    /** 当前未允许本应用模拟位置。 */
    Denied,

    /** 系统不支持可靠预检查，待实际启动验证。 */
    Unknown
}

/** 读取本应用 AppOps，不申请真实定位权限，也不在代码中绕过开发者选项。 */
internal fun readMockAuthorization(context: Context): MockAuthorization = try {
    val manager = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= 29) manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
    else manager.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
    if (mode == AppOpsManager.MODE_ALLOWED) MockAuthorization.Allowed else MockAuthorization.Denied
} catch (_: Exception) { MockAuthorization.Unknown }

/** 每次页面回到前台重新检查，不把用户打开设置等同于已授权。 */
@Composable
internal fun rememberMockAuthorization(): MockAuthorization {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var authorization by remember { mutableStateOf(readMockAuthorization(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) authorization = readMockAuthorization(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return authorization
}

/** 打开开发者选项；部分系统未暴露入口时回退到设置并保留文字引导。 */
internal fun openDeveloperOptions(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}
