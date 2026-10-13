package com.sunnyfor.gpsmock.platform.ui.mapui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import java.io.File

/**
 * 高德原生地图的 Compose 边界；调用方只在门禁 Ready 时组合此组件。
 *
 * MapView 使用系统 Resources，不修改全局密度。选点返回 GCJ-02；手输 WGS-84 仅在
 * 绘制时转换。关闭在线功能或退出组合时释放资源，Activity 保存时保留 SDK 相机状态。
 * @param apiKey 非空 Android Key，不表示已通过服务器鉴权。
 * @param owner 地图所依附的 Activity 生命周期与 saved-state owner。
 * @param selected 原始目标，可为 null；不把默认地图中心当作已选目标。
 * @param onPick 真正点击地图后返回的 GCJ-02 坐标。
 * @param onFailure 初始化、绘制或生命周期操作异常的安全提示。
 * @param modifier 地图实际分配区域；建议在 SystemDensityContent 内使用。
 */
@Composable
internal fun AMapPickerMap(
    apiKey: String,
    owner: SavedStateRegistryOwner,
    selected: MapCoordinate?,
    onPick: (MapCoordinate) -> Unit,
    onFailure: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context -> AMapSurface(context, apiKey, owner, onPick, onFailure) },
        modifier = modifier,
        onReset = null,
        onRelease = { it.close() },
        update = { it.update(selected, onPick, onFailure) }
    )
}

/**
 * 独占一个 MapView 的宿主，负责 SDK 初始化、事件桥接与最终释放。
 *
 * 初始化失败时保留空宿主并报告错误，页面显示手输回退；不创建仿真地图。
 * 一个 Activity 同时只组合一个实例，saved-state key 因此固定。
 * 此私有宿主只由 AndroidView 工厂构造，不支持 XML/布局编辑器反射构造，避免绕过门禁。
 * @param context 原生 View 的 Activity 上下文，生命周期结束时随宿主释放。
 * @param apiKey 初始化阶段使用的非空 Key，不作为成员保留或出现在错误文案中。
 * @param onPick 初始点击回调，重组后由 update 替换。
 * @param onFailure 初始安全错误回调，重组后由 update 替换。
 */
@SuppressLint("ViewConstructor")
private class AMapSurface(
    context: Context,

    apiKey: String,

    /** 保存相机状态并提供前后台生命周期的宿主。 */
    private val owner: SavedStateRegistryOwner,

    onPick: (MapCoordinate) -> Unit,
    onFailure: (String) -> Unit
) : FrameLayout(context), DefaultLifecycleObserver {
    /** 当前点击回调；重组时更新，避免使用过期的页面闭包。 */
    private var pickCallback = onPick

    /** 当前安全错误回调，不传出 SDK 异常原文。 */
    private var failureCallback = onFailure

    /** 已成功创建或正在初始化的 SDK 视图，关闭后清空。 */
    private var mapView: MapView? = null

    /** 当前唯一目标标记；替换选点时移除旧标记。 */
    private var marker: Marker? = null

    /** 最近绘制的原坐标；重组不重复移动相机，也不改写该坐标系。 */
    private var drawnCoordinate: MapCoordinate? = null

    /** 已注册状态保存提供器，保证关闭时只注销自己的注册。 */
    private var stateRegistered = false

    /** 保证恢复、暂停与释放只按有效状态转换发送给 SDK。 */
    private val lifecycle = MapLifecycle(
        resumeMap = { safely { mapView?.onResume() } },
        pauseMap = { safely { mapView?.onPause() } },
        destroyMap = { safely { mapView?.onDestroy() }; mapView = null }
    )

    init {
        safely {
            check(apiKey.isNotBlank())
            // 必须先由页面真实披露并获得同意，再在 MapView 构造之前报告给 SDK。
            MapsInitializer.updatePrivacyShow(context, true, true)
            MapsInitializer.updatePrivacyAgree(context, true)
            MapsInitializer.setApiKey(apiKey)
            MapsInitializer.setProtocol(MapsInitializer.HTTPS)
            MapsInitializer.sdcardDir = File(context.noBackupFilesDir, "amap-map-cache").apply { mkdirs() }.path
            MapsInitializer.initialize(context.applicationContext)
            val restored = owner.savedStateRegistry.consumeRestoredStateForKey(SAVED_STATE_KEY)
            val view = MapView(context)
            mapView = view
            view.onCreate(restored)
            addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            view.map.apply {
                // 不启用“我的位置”，不申请设备定位权限；默认视野不是已选目标。
                isMyLocationEnabled = false
                uiSettings.isMyLocationButtonEnabled = false
                uiSettings.isZoomControlsEnabled = true
                setOnMapClickListener { point ->
                    pickCallback(MapCoordinate(point.latitude, point.longitude, MapCoordinateSystem.GCJ02))
                }
                if (restored == null) {
                    moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.0, 105.0), 4f))
                }
            }
            owner.savedStateRegistry.registerSavedStateProvider(SAVED_STATE_KEY) {
                Bundle().also { bundle -> safely { mapView?.onSaveInstanceState(bundle) } }
            }
            stateRegistered = true
            owner.lifecycle.addObserver(this)
        }
    }

    /**
     * 仅在目标变化时绘制并移动相机，重组不重复抢占用户视野。
     * @param coordinate 原坐标；WGS-84 转换值只交给地图，不回写原目标；null 移除标记。
     * @param onPick 当前点击回调，始终替换旧闭包。
     * @param onFailure 当前安全错误回调，不接收 SDK 异常原文。
     */
    fun update(coordinate: MapCoordinate?, onPick: (MapCoordinate) -> Unit, onFailure: (String) -> Unit) {
        pickCallback = onPick
        failureCallback = onFailure
        if (coordinate == drawnCoordinate) return
        safely {
            val map = mapView?.map ?: return@safely
            marker?.remove()
            marker = null
            if (coordinate != null) {
                val original = LatLng(coordinate.latitude, coordinate.longitude)
                val displayed = if (coordinate.system == MapCoordinateSystem.WGS84) {
                    CoordinateConverter(context).from(CoordinateConverter.CoordType.GPS).coord(original).convert()
                } else original
                marker = map.addMarker(MarkerOptions().position(displayed)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(displayed, 16f))
            }
            drawnCoordinate = coordinate
        }
    }

    /** Activity 恢复后激活地图；生命周期已恢复时注册观察者也会收到该事件。 */
    override fun onResume(owner: LifecycleOwner) = lifecycle.resume()

    /** Activity 离开前台时暂停地图渲染。 */
    override fun onPause(owner: LifecycleOwner) = lifecycle.pause()

    /** Activity 销毁与 Compose 释放走同一幂等关闭路径。 */
    override fun onDestroy(owner: LifecycleOwner) = close()

    /** 注销宿主注册并释放原生视图；可安全重复调用。 */
    fun close() {
        owner.lifecycle.removeObserver(this)
        if (stateRegistered) {
            owner.savedStateRegistry.unregisterSavedStateProvider(SAVED_STATE_KEY)
            stateRegistered = false
        }
        marker = null
        lifecycle.close()
        removeAllViews()
    }

    /** SDK 操作失败时报告安全提示；初始化后的部分资源由释放路径继续清理。 */
    private fun safely(action: () -> Unit) {
        try {
            action()
        } catch (_: UnsatisfiedLinkError) {
            failureCallback("地图原生库不支持此设备，请使用 ARM 真机；仍可手输坐标。")
        } catch (_: Exception) {
            failureCallback("地图操作失败，请检查 Key、签名和网络；仍可手输坐标。")
        }
    }

    /** 此单实例地图的状态保存命名空间，不用于保存业务目标或模拟会话。 */
    private companion object {
        /** 与 Activity 的其他状态提供器隔离，避免覆盖它们的 Bundle。 */
        const val SAVED_STATE_KEY = "position-picker-amap-camera"
    }
}
