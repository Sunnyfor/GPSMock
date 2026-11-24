package com.sunnyfor.gpsmock.locationprobe

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.view.WindowManager
import androidx.core.content.ContextCompat

/**
 * 独立接收端的前台观察宿主，让定位请求按普通应用的权限与前台状态接受系统检查。
 *
 * 页面只说明验收用途和申请精准定位权限；不自行定位、注入、保存坐标或发送网络请求。
 * 用户可拒绝授权或选择仅此一次，测试不得通过 shell 身份绕过这一选择。
 */
class ReceiverObservationActivity : Activity() {
    /** 系统权限选择已经完成；测试通过主线程读取，拒绝时不继续等待或重新申请。 */
    var permissionDecisionFinished: Boolean = false
        private set

    /** 说明权限用途与当前测试阶段的本机文本，不承载任何坐标。 */
    private lateinit var explanation: TextView

    /** 显示用途说明后按需发起标准权限请求，不修改定位助手的运行状态。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 仅本测试页面可见期间保持亮屏，不修改全局锁屏设置，也不能解锁已锁定的设备。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        explanation = TextView(this).apply {
            textSize = 18f
            val inset = (24 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset, inset, inset)
            text = "定位接收端验收\n\n用于检查系统是否把模拟坐标交给另一个前台应用。\n\n请允许精准定位，可选择“仅此一次”。坐标只在内存中比较，不保存、不上传，不启动或停止模拟。"
        }
        setContentView(explanation)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            permissionDecisionFinished = true
            explanation.text = "正在验证实时定位回调\n\n这是独立接收端，不会改变模拟目标。测试结束后自动关闭页面。"
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST)
        }
    }

    /** 尊重精准/大致/拒绝的系统选择；不自动授予权限，不将拒绝解释为模拟引擎失败。 */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            permissionDecisionFinished = true
            explanation.text = if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                "正在验证实时定位回调\n\n坐标不会保存或上传，测试结束后自动关闭页面。"
            } else {
                "未获得精准定位权限\n\n本次验收不能比较精确坐标，测试将停止。不会修改你的权限选择。"
            }
        }
    }

    /** 标准运行时权限请求的本地标识，不是业务命令或会话身份。 */
    private companion object {
        /** 当前页面唯一权限请求编号。 */
        const val PERMISSION_REQUEST = 100
    }
}
