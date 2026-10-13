package com.sunnyfor.gpsmock.feature.position

import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.SavedPosition
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionView
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase

/** 目标选择来源，便于说明坐标的来源而非模拟运行状态。 */
internal enum class PositionSource {
    /** 用户真正点击地图得到的 GCJ-02 目标。 */
    Map,

    /** 用户选择真实 POI 搜索结果得到的 GCJ-02 目标。 */
    Search,

    /** 用户提交并校验通过的手输坐标，保留显式坐标系。 */
    Manual,

    /** 从本地收藏公开投影选择，启动前仍须重新解析版本。 */
    Saved
}

/** 本轮仅存在于页面内存中的目标快照；不代表已收藏或正在模拟。 */
internal data class SelectedPosition(
    /** 原始坐标及其坐标系。 */
    val coordinate: MapCoordinate,

    /** 地图、搜索、手输或收藏来源。 */
    val source: PositionSource,

    /** 搜索或收藏名称；地图或手输没有名称时为 null。 */
    val title: String? = null,

    /** 实际返回的地址；未查询或查无地址时为 null。 */
    val address: String? = null,

    /** 收藏来源身份；非收藏目标为 null。 */
    val savedId: String? = null,

    /** 选中时收藏版本；与来源身份配对。 */
    val savedRevision: Long? = null
)

/** 手输表单的校验结果；两个字段独立反馈，不用 0 代替无效输入。 */
internal data class CoordinateInputResult(
    /** 校验通过的原始坐标；任一字段错误时为 null。 */
    val coordinate: MapCoordinate?,

    /** 纬度字段提示；合法时为 null。 */
    val latitudeError: String?,

    /** 经度字段提示；合法时为 null。 */
    val longitudeError: String?
)

/**
 * 校验十进制度输入并保留指定坐标系；接受首尾空白、负数和零值。
 *
 * @param latitude 纬度文本，不接受空值、NaN、无穷或超出 [-90, 90] 的值。
 * @param longitude 经度文本，不接受空值、NaN、无穷或超出 [-180, 180] 的值。
 * @param system 用户选择的坐标系；此函数不执行坐标转换。
 * @return 字段错误或完整坐标，不抛出格式解析异常。
 */
internal fun validateCoordinateInput(
    latitude: String,
    longitude: String,
    system: MapCoordinateSystem
): CoordinateInputResult {
    val lat = latitude.trim().toDoubleOrNull()
    val lon = longitude.trim().toDoubleOrNull()
    val latError = if (lat == null || !lat.isFinite() || lat !in -90.0..90.0) "请输入 -90～90 的有限纬度" else null
    val lonError = if (lon == null || !lon.isFinite() || lon !in -180.0..180.0) "请输入 -180～180 的有限经度" else null
    return CoordinateInputResult(
        coordinate = if (latError == null && lonError == null) MapCoordinate(lat!!, lon!!, system) else null,
        latitudeError = latError,
        longitudeError = lonError
    )
}

/** 页面单向数据流快照；异步请求只更新当前仍有效的请求对应状态。 */
internal data class PositionPickerState(
    /** 在线功能门禁；Ready 不意味着 Key 已通过在线鉴权。 */
    val gate: OnlineMapGate,

    /** 地图初始化或绘制失败提示；存在时用清晰回退替代地图。 */
    val mapError: String? = null,

    /** 当前纬度输入文本，不自动跟随地图选点覆盖用户草稿。 */
    val latitude: String = "",

    /** 当前经度输入文本。 */
    val longitude: String = "",

    /** 下一次手输提交使用的坐标系；不修改已经选中的目标。 */
    val inputSystem: MapCoordinateSystem = MapCoordinateSystem.WGS84,

    /** 纬度校验失败提示。 */
    val latitudeError: String? = null,

    /** 经度校验失败提示。 */
    val longitudeError: String? = null,

    /** 搜索关键词草稿。 */
    val query: String = "",

    /** 可选城市；空白时查询范围不限城市。 */
    val city: String = "",

    /** 是否正在等待当前关键词请求。 */
    val searching: Boolean = false,

    /** 当前搜索的真实候选，不包含缓存伪造条目。 */
    val places: List<MapPlace> = emptyList(),

    /** 搜索进度、空结果或失败的安全提示。 */
    val searchMessage: String? = null,

    /** 当前已经选择的原始目标；未选择时为 null。 */
    val selected: SelectedPosition? = null,

    /** 当前目标的地址查询提示，与搜索提示相互独立。 */
    val addressMessage: String? = null,

    /** 唯一业务协调器的会话投影，不由选点事件设为运行。 */
    val session: SessionView = SessionView(SessionPhase.Idle),

    /** 已从持久化公开能力读取的收藏列表。 */
    val savedPositions: List<SavedPosition> = emptyList(),

    /** 收藏/会话命令正在执行时防止页面重复提交。 */
    val businessBusy: Boolean = false,

    /** 业务命令的成功、错误或加载提示。 */
    val businessMessage: String? = null,

    /** 新增/编辑对话框草稿；null 表示关闭。 */
    val editor: SavedPositionEditor? = null,

    /** 待用户明确确认删除的投影。 */
    val pendingDelete: SavedPosition? = null,

    /** 清空全部收藏的确认提示是否打开。 */
    val confirmClear: Boolean = false
)

/** 收藏编辑草稿；原投影不可变，字段更改形成新的命令意图。 */
internal data class SavedPositionEditor(

    /** 编辑的原始投影；null 表示新增。 */
    val original: SavedPosition?,

    /** 本次草稿的稳定命令身份，重试不重新生成。 */
    val commandId: String,

    /** 名称草稿。 */
    val name: String,

    /** 纬度文本。 */
    val latitude: String,

    /** 经度文本。 */
    val longitude: String,

    /** 明确坐标系。 */
    val system: MapCoordinateSystem,

    /** 验证或存储错误；失败时草稿不丢失。 */
    val error: String? = null
)
