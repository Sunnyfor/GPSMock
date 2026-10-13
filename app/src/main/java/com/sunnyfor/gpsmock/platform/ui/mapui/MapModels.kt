package com.sunnyfor.gpsmock.platform.ui.mapui

/** UI 与地图适配器之间使用的坐标系，不替代领域层的坐标值对象。 */
internal enum class MapCoordinateSystem {
    /** GPS 常用地理坐标系；绘制到高德地图时单独转换，原值不变。 */
    WGS84,

    /** 高德国内地图选点及 POI 返回的坐标系。 */
    GCJ02
}

/**
 * SDK 无关的地图 UI 坐标，纬度在前、经度在后，单位均为度。
 *
 * 仅用于选点页面的数据交换；启动模拟时须通过业务 contract 重新校验和转换。
 * @throws IllegalArgumentException 任一分量非有限数，或超出经纬度范围。
 */
internal data class MapCoordinate(
    /** 纬度，范围 [-90, 90] 度；零值合法。 */
    val latitude: Double,

    /** 经度，范围 [-180, 180] 度；零值合法。 */
    val longitude: Double,

    /** 数值所归属的坐标系，不因地图显示转换而改变。 */
    val system: MapCoordinateSystem
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/** 一条真实搜索结果的 UI 快照，不包含第三方 SDK 类型或可变对象。 */
internal data class MapPlace(
    /** 搜索返回的地点名称。 */
    val title: String,

    /** 搜索返回的地址；缺失时为空字符串，不伪造地址。 */
    val address: String,

    /** 搜索返回的 GCJ-02 坐标。 */
    val coordinate: MapCoordinate
)

/** 地图查询的成功或失败结果，不把异常信息、Key 或请求参数显示给用户。 */
internal sealed interface MapQueryResult<out T> {
    /** 查询完成；空列表或空地址也属于成功返回。 */
    data class Success<T>(
        /** SDK 数据转换后的不可变 UI 值。 */
        val value: T
    ) : MapQueryResult<T>

    /** 查询失败；保留安全的错误码，提示文本由适配器统一映射。 */
    data class Failure(
        /** SDK 错误码；本地初始化异常时为 null。 */
        val code: Int?,

        /** 不含凭据及坐标的中文提示。 */
        val message: String
    ) : MapQueryResult<Nothing>
}

/**
 * 在线地图查询端口，仅供选点 UI 使用，不是业务仓储或领域服务。
 *
 * 调用方必须先获得隐私同意并配置 Key。回调在主线程交付；SDK 不支持可靠取消，
 * 页面负责忽略过期结果，不能以忽略回调宣称请求未曾发送。
 */
internal interface MapQueryClient {
    /**
     * 按关键词和可选城市查询真实 POI，最多返回十条。
     * @param query 已由页面检查为非空的地点或地址关键词。
     * @param city 可选城市名称，空白表示不限城市。
     * @param callback 主线程结果回调；空列表表示查无结果，Failure 表示查询失败。
     */
    fun search(query: String, city: String, callback: (MapQueryResult<List<MapPlace>>) -> Unit)

    /**
     * 查询坐标附近的地址，不影响原始目标合法性。
     * @param coordinate 原始经纬度（度）及显式坐标系，实现按该坐标系传参。
     * @param callback 主线程结果回调；成功但地址缺失时值为 null。
     */
    fun reverseGeocode(coordinate: MapCoordinate, callback: (MapQueryResult<String?>) -> Unit)
}

/** 在线地图的前置条件；Ready 只表示允许初始化，不保证 Key 鉴权或地图加载成功。 */
internal enum class OnlineMapGate {
    /** 构建时未填写 Key；禁止创建地图及搜索 SDK 对象。 */
    MissingKey,

    /** 用户尚未开启在线地图，或已关闭页面的在线功能。 */
    Disabled,

    /** 用户要求开启，但尚未明确同意本次会话的隐私说明。 */
    ConsentRequired,

    /** 已配置且明确同意，允许尝试初始化真实 SDK。 */
    Ready
}

/** 按 Key、启用意愿和明确同意计算门禁；未配置 Key 的提示优先级最高。 */
internal fun resolveOnlineMapGate(keyConfigured: Boolean, enabled: Boolean, consent: Boolean): OnlineMapGate =
    when {
        !keyConfigured -> OnlineMapGate.MissingKey
        !enabled -> OnlineMapGate.Disabled
        !consent -> OnlineMapGate.ConsentRequired
        else -> OnlineMapGate.Ready
    }
