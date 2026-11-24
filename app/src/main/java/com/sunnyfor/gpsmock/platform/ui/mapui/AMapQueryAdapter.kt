package com.sunnyfor.gpsmock.platform.ui.mapui

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.VisualSearchResult

/**
 * 将高德搜索和逆地理编码转换为 SDK 无关的 UI 快照。
 *
 * 构造时不初始化 SDK；只有门禁通过后的实际调用才报告隐私同意并创建查询对象。
 * 仅持有 applicationContext，避免异步请求保留 Activity。未创建定位客户端。
 */
internal class AMapQueryAdapter(
    /** 应用级上下文；调用方不得传入需要长期保留的 Activity 引用。 */
    private val context: Context,

    /** 构建时注入的 Android Key，仅传给 SDK，不记录或显示。 */
    private val apiKey: String
) : MapQueryClient {
    /** 把 SDK 结果统一交付到页面主线程。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 是否已实际报告查询服务同意；未初始化前撤回不得触碰 SDK。 */
    private var prepared = false

    /** 同步撤回查询 SDK 合规状态；已有异步请求不可撤销，页面令牌负责拒绝迟到结果。 */
    override fun revokePrivacy() {
        if (!prepared) return
        try { ServiceSettings.updatePrivacyAgree(context, false) }
        finally { prepared = false }
    }

    /** 查询第一页 POI，并过滤没有有效经纬度的数据；不补造搜索结果。 */
    override fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit) {
        execute(callback) {
            prepare()
            val request = PoiSearchV2(context, PoiSearchV2.Query(query.trim(), "", city.trim()).apply {
                pageSize = 10
                pageNum = 0
            })
            request.setOnPoiSearchListener(object : PoiSearchV2.OnPoiSearchListener {
                /** 只接收关键词查询结果；SDK 错误与空结果分别处理。 */
                override fun onPoiSearched(result: PoiResultV2?, code: Int) {
                    deliver(callback, if (code == 1000 && result != null) {
                        MapQueryResult.Success(result.pois.orEmpty().mapNotNull { item ->
                            val point = item.latLonPoint ?: return@mapNotNull null
                            if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
                                point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0
                            ) return@mapNotNull null
                            MapPlace(item.title.orEmpty(), item.snippet.orEmpty(),
                                MapCoordinate(point.latitude, point.longitude, MapCoordinateSystem.GCJ02))
                        })
                    } else queryFailure(code))
                }

                /** 本页面不发起 POI ID 详情查询，因此不消费此类回调。 */
                override fun onPoiItemSearched(item: PoiItemV2?, code: Int) = Unit

                /** 本页面不发起视觉搜索，因此不消费此类回调。 */
                override fun onVisualSearched(result: VisualSearchResult?, code: Int) = Unit
            })
            request.searchPOIAsyn()
        }
    }

    /** 使用 GPS 或 AMAP 类型传递原坐标；不在 UI 中近似反算 WGS-84。 */
    override fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit) {
        execute(callback) {
            prepare()
            val request = GeocodeSearch(context)
            request.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                /** 返回真实地址；成功但没有地址时交付 null。 */
                override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) {
                    deliver(callback, if (code == 1000) {
                        MapQueryResult.Success(result?.regeocodeAddress?.formatAddress?.takeIf { it.isNotBlank() })
                    } else queryFailure(code))
                }

                /** 本页面使用 POI 搜索，不发起地址转坐标查询。 */
                override fun onGeocodeSearched(result: GeocodeResult?, code: Int) = Unit
            })
            val type = if (coordinate.system == MapCoordinateSystem.WGS84) GeocodeSearch.GPS else GeocodeSearch.AMAP
            request.getFromLocationAsyn(RegeocodeQuery(
                LatLonPoint(coordinate.latitude, coordinate.longitude), 200f, type
            ))
        }
    }

    /** 仅在上层明确同意后调用；在创建任何搜索对象前设置隐私声明、Key 和 HTTPS。 */
    private fun prepare() {
        check(apiKey.isNotBlank())
        ServiceSettings.updatePrivacyShow(context, true, true)
        ServiceSettings.updatePrivacyAgree(context, true)
        prepared = true
        ServiceSettings.getInstance().apply {
            setApiKey(apiKey)
            setProtocol(ServiceSettings.HTTPS)
        }
    }

    /** 将初始化或请求异常转为可展示失败；不吞掉致命 VM 错误。 */
    private fun <T> execute(callback: (MapQueryResult<T>) -> Unit, block: () -> Unit) {
        try {
            block()
        } catch (error: AMapException) {
            deliver(callback, queryFailure(error.errorCode))
        } catch (_: RuntimeException) {
            deliver(callback, queryFailure(null))
        } catch (_: UnsatisfiedLinkError) {
            deliver(callback, MapQueryResult.Failure(null, "设备不支持当前高德 SDK 的原生库，请使用 ARM 真机。"))
        }
    }

    /** 即使 SDK 在后台线程完成，也只在主线程更新页面状态。 */
    private fun <T> deliver(callback: (MapQueryResult<T>) -> Unit, result: MapQueryResult<T>) {
        mainHandler.post { callback(result) }
    }
}

/** 将官方错误码映射为安全中文提示；未知码保留供配置排查，不输出异常原文。 */
internal fun queryFailure(code: Int?): MapQueryResult.Failure = MapQueryResult.Failure(code, when (code) {
    1001, 1002, 1008, 1009 -> "高德鉴权失败，请检查 Android Key、包名和签名 SHA-1。"
    1003, 1004, 1005, 1010 -> "高德服务额度或访问频率受限，请稍后重试并检查控制台。"
    1801 -> "查询参数不符合服务要求，请检查关键词或坐标后重试。"
    1802, 1804, 1806 -> "查询未完成，请检查网络后重试。"
    else -> "高德查询失败，请检查配置和网络后重试。"
})
