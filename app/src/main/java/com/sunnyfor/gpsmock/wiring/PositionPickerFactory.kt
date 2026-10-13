package com.sunnyfor.gpsmock.wiring

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sunnyfor.gpsmock.feature.position.PositionPickerViewModel
import com.sunnyfor.gpsmock.platform.ui.mapui.AMapQueryAdapter

/** 从合并后的 Manifest 读取构建时 Key；空值按未配置处理，不输出凭据。 */
internal fun readAMapKey(context: Context): String = context.packageManager
    .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    .metaData?.getString("com.amap.api.v2.apikey").orEmpty().trim()

/**
 * Activity 作用域的页面对象装配，构造阶段不初始化在线 SDK。
 *
 * 查询适配器只保留应用上下文，领域模块不依赖此装配及任何 SDK 类型。
 */
internal class PositionPickerFactory(
    /** 长期使用的应用上下文，不保留 Activity。 */
    private val context: Context,

    /** 构建时 Key；空值仍可创建离线手输页面。 */
    private val apiKey: String
) : ViewModelProvider.Factory {
    /** 仅创建选点协调器；遇到未知 ViewModel 类型立即拒绝。 */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == PositionPickerViewModel::class.java)
        val application = context.applicationContext as GpsMockApplication
        return modelClass.cast(PositionPickerViewModel(AMapQueryAdapter(context, apiKey), apiKey.isNotBlank(),
            application.catalog, application.session))!!
    }
}
