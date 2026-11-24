package com.sunnyfor.gpsmock.feature.position

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.MockSession
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinate
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.StartTarget
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCode
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinate
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogCoordinateSystem
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.CatalogMutation
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.PositionCatalog
import com.sunnyfor.gpsmock.locationgrp.placecatalogbc.contract.SavedPosition
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.sunnyfor.gpsmock.platform.ui.mapui.DeviceLocationClient
import com.sunnyfor.gpsmock.platform.ui.mapui.DeviceLocationResult
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCameraRequest
import com.sunnyfor.gpsmock.platform.ui.privacy.PrivacyConsentRecord
import com.sunnyfor.gpsmock.platform.ui.privacy.PrivacyConsentStore
import java.util.UUID
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinate
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.MapPlace
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryClient
import com.sunnyfor.gpsmock.platform.ui.mapui.MapQueryResult
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.platform.ui.mapui.resolveOnlineMapGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 跨 BC 的选点、收藏与模拟旅程状态协调器，只调用公开契约，不直接操作领域聚合。
 *
 * ## 状态规则
 * Key 与当前版本有效隐私同意同时满足后才允许在线请求；明确同意记录可在本机恢复。
 * 配置变更由 Activity ViewModel 保留状态；进程退出后不恢复待选目标或自动启动模拟。
 * 所有方法和查询回调在主线程串行执行。每次新意图使用独立令牌，过期回调无效；
 * 关闭在线功能无法撤销已经发送的请求，不宣称 SDK 后台完全停止采集。
 */
internal class PositionPickerViewModel(
    /** 无 SDK 类型的查询端口；构造适配器本身不得初始化在线 SDK。 */
    private val client: MapQueryClient,

    /** 本次构建是否配置非空 Key，不等同于鉴权成功。 */
    private val keyConfigured: Boolean,

    /** 真实收藏公开能力；纯选点测试可不提供此端口。 */
    private val catalog: PositionCatalog? = null,

    /** Application 唯一会话公开能力，不随 Activity ViewModel 销毁。 */
    private val sessions: MockSession? = null,

    /** 本机版本化授权存储；纯选点测试可省略，生产由 wiring 提供。 */
    private val privacyStore: PrivacyConsentStore? = null,

    /** 前台一次性设备定位端口；缺省时不获取任何设备位置。 */
    private val deviceLocation: DeviceLocationClient? = null
) : ViewModel() {
    /** 仅读取匹配当前告知版本的记录；旧版不迁移为新功能的授权。 */
    private val privacyRecord = privacyStore?.read()?.takeIf { it.isCurrent() }

    /** 是否要求开启在线地图；缺 Key 时不能改变为开启。 */
    private var onlineEnabled = privacyRecord?.mapAccepted == true

    /** 当前告知版本的地图同意；拒绝或撤回后立即关闭 SDK 门禁。 */
    private var consent = privacyRecord?.mapAccepted == true

    /** 当前一次定位任务；新请求、选点、后台、关闭在线及模拟启动均使它失效。 */
    private var locationJob: Job? = null

    /** 本轮页面是否尝试自动居中，防止重组、重返前台反复抢占用户视野。 */
    private var automaticLocationAttempted = false

    /** 相机请求的页面内递增身份，相同坐标再次居中也产生新意图。 */
    private var cameraSequence = 0L

    /** 当前搜索请求身份；修改输入、关闭地图或销毁时置空。 */
    private var searchToken: Any? = null

    /** 当前地址请求身份；替换目标或关闭地图时置空。 */
    private var addressToken: Any? = null

    /** 页面已结束后拒绝任何异步状态更新。 */
    private var disposed = false

    /** 当前列表读取令牌，较旧的列表响应不能覆盖较新刷新。 */
    private var catalogToken: Any? = null

    /** 删除确认对应的稳定命令，失败重试沿用，重新选择目标才换号。 */
    private var deleteCommandId: String? = null

    /** 批量删除确认对应的稳定命令。 */
    private var clearCommandId: String? = null

    /** 内部可写快照，外部只能读取。 */
    private val mutableState = MutableStateFlow(PositionPickerState(
        gate = resolveOnlineMapGate(keyConfigured, onlineEnabled, consent),
        privacyRequired = privacyStore != null && privacyRecord == null,
        mapConsentAccepted = consent,
        deviceConsentAccepted = privacyRecord?.deviceAccepted == true,
        deviceConsentDecided = privacyRecord?.deviceDecided == true
    ))

    /** 提供 Compose 观察的只读状态流。 */
    val state = mutableState.asStateFlow()

    init {
        sessions?.let { port -> viewModelScope.launch { port.state.collect { view ->
            if (view.phase != SessionPhase.Idle) cancelDeviceLocation()
            mutableState.update { it.copy(session = view) }
        } } }
        if (catalog != null) reloadSaved()
    }

    /** 将当前选择作为临时坐标或精确版本收藏启动，不伪造 Running。 */
    fun startSimulation() {
        val selected = state.value.selected ?: return
        val port = sessions ?: return
        cancelDeviceLocation()
        val target = if (selected.savedId != null && selected.savedRevision != null) StartTarget.Saved(selected.savedId, selected.savedRevision)
        else StartTarget.Inline(SessionCoordinate(selected.coordinate.latitude, selected.coordinate.longitude,
            SessionCoordinateSystem.valueOf(selected.coordinate.system.name)), selected.title.orEmpty())
        businessCommand { sessionErrorText(port.start(UUID.randomUUID().toString(), target).error) ?: "启动已受理，等待真实写入确认。" }
    }

    /** 页面停止明确会话；状态变化只来自业务公开流。 */
    fun stopSimulation() {
        val id = state.value.session.sessionId ?: return
        val port = sessions ?: return
        businessCommand { sessionErrorText(port.stop(UUID.randomUUID().toString(), id).error) ?: "正在停止并清理本方资源。" }
    }

    /** 显式重试待清理状态，不通过重新启动绕过清理。 */
    fun retryCleanup() {
        val port = sessions ?: return
        businessCommand { sessionErrorText(port.retryCleanup(UUID.randomUUID().toString()).error) ?: "已请求重新清理。" }
    }

    /** 后台读取收藏；失败保留原列表并显示错误。 */
    fun reloadSaved() {
        val port = catalog ?: return
        val token = Any()
        catalogToken = token
        viewModelScope.launch {
            val result = port.list()
            if (disposed || catalogToken !== token) return@launch
            mutableState.update { if (result.code == CatalogCode.Success) it.copy(savedPositions = result.positions)
                else it.copy(businessMessage = catalogErrorText(result.code)) }
        }
    }

    /** 选择当前列表投影，带来源身份和版本，运行中的目标保持独立冻结。 */
    fun pickSaved(position: SavedPosition) {
        if (position !in state.value.savedPositions) return
        val coordinate = position.coordinate
        select(SelectedPosition(MapCoordinate(coordinate.latitude, coordinate.longitude, MapCoordinateSystem.valueOf(coordinate.system.name)),
            PositionSource.Saved, position.name, savedId = position.id, savedRevision = position.revision))
    }

    /** 打开保存目标或编辑收藏草稿，原坐标系不隐含转换。 */
    fun editSaved(position: SavedPosition? = null) {
        if (state.value.businessBusy) return
        val selected = state.value.selected ?: if (position == null) return else SelectedPosition(
            MapCoordinate(position.coordinate.latitude, position.coordinate.longitude, MapCoordinateSystem.valueOf(position.coordinate.system.name)), PositionSource.Saved)
        val coordinate = position?.coordinate?.let { MapCoordinate(it.latitude, it.longitude, MapCoordinateSystem.valueOf(it.system.name)) } ?: selected.coordinate
        mutableState.update { it.copy(editor = SavedPositionEditor(position, UUID.randomUUID().toString(), position?.name ?: selected.title.orEmpty(),
            coordinate.latitude.toString(), coordinate.longitude.toString(), coordinate.system)) }
    }

    /** 更新一个草稿字段，视为新的命令参数；存储失败后未改草稿的重试沿用原命令。 */
    fun changeEditor(field: String, value: String) {
        if (state.value.businessBusy) return
        mutableState.update { state -> state.copy(editor = state.editor?.let { editor ->
            when (field) {
                "name" -> editor.copy(name = value)
                "latitude" -> editor.copy(latitude = value)
                "longitude" -> editor.copy(longitude = value)
                "system" -> editor.copy(system = MapCoordinateSystem.valueOf(value))
                else -> editor
            }.copy(commandId = UUID.randomUUID().toString(), error = null)
        }) }
    }

    /** 关闭编辑对话框；提交过程中不丢弃仍可能完成的草稿。 */
    fun closeEditor() { if (!state.value.businessBusy) mutableState.update { it.copy(editor = null) } }

    /** 完整校验草稿后调用原子用例；失败保留输入，成功才关闭并刷新列表。 */
    fun saveEditor() {
        val editor = state.value.editor ?: return
        val port = catalog ?: return
        if (state.value.businessBusy) return
        val parsed = validateCoordinateInput(editor.latitude, editor.longitude, editor.system)
        val name = editor.name.trim()
        if (name.codePointCount(0, name.length) !in 1..30 || parsed.coordinate == null) {
            mutableState.update { it.copy(editor = editor.copy(error = "名称须为 1–30 字；经纬度必须有效且有限。")) }
            return
        }
        val coordinate = CatalogCoordinate(parsed.coordinate.latitude, parsed.coordinate.longitude, CatalogCoordinateSystem.valueOf(editor.system.name))
        val mutation = editor.original?.let { CatalogMutation.Edit(it.id, it.revision, editor.name, coordinate) }
            ?: CatalogMutation.Create(editor.name, coordinate)
        mutableState.update { it.copy(businessBusy = true) }
        viewModelScope.launch {
            try {
                val result = port.execute(editor.commandId, mutation)
                mutableState.update { if (result.code == CatalogCode.Success) it.copy(editor = null, businessMessage = "收藏已保存。")
                    else it.copy(editor = editor.copy(error = catalogErrorText(result.code)), businessMessage = catalogErrorText(result.code)) }
                reloadSaved()
            } finally { mutableState.update { it.copy(businessBusy = false) } }
        }
    }

    /** 先打开明确目标的删除确认，不直接删除。 */
    fun requestDelete(position: SavedPosition) {
        if (!state.value.businessBusy) { deleteCommandId = UUID.randomUUID().toString(); mutableState.update { it.copy(pendingDelete = position) } }
    }

    /** 取消删除确认，不产生持久化写入。 */
    fun cancelDelete() { if (!state.value.businessBusy) mutableState.update { it.copy(pendingDelete = null) } }

    /** 确认后按原版本删除；已运行目标不依赖该记录。 */
    fun confirmDelete() {
        val position = state.value.pendingDelete ?: return
        catalogCommand(CatalogMutation.Delete(position.id, position.revision), "收藏已删除。", deleteCommandId ?: return)
    }

    /** 仅在无活动会话时打开批量清空确认。 */
    fun requestClear() {
        if (state.value.session.phase == SessionPhase.Idle && !state.value.businessBusy) {
            clearCommandId = UUID.randomUUID().toString(); mutableState.update { it.copy(confirmClear = true) }
        }
    }

    /** 取消批量清空，保留全部数据。 */
    fun cancelClear() { if (!state.value.businessBusy) mutableState.update { it.copy(confirmClear = false) } }

    /** 再次核对运行状态后清空；启动/运行/停止/待清理均禁止批量删除。 */
    fun confirmClear() {
        if (state.value.session.phase != SessionPhase.Idle) return
        catalogCommand(CatalogMutation.Clear, "全部收藏已删除。", clearCommandId ?: return)
    }

    /** 执行列表命令，失败时保留确认目标并刷新最新版本以便用户重选。 */
    private fun catalogCommand(mutation: CatalogMutation, success: String, commandId: String) {
        val port = catalog ?: return
        businessCommand {
            val result = port.execute(commandId, mutation)
            if (result.code == CatalogCode.Success) mutableState.update { it.copy(pendingDelete = null, confirmClear = false) }
            reloadSaved()
            if (result.code == CatalogCode.Success) success else catalogErrorText(result.code)
        }
    }

    /** 串行防重提交，仍保留业务协调器的独立并发与幂等保护。 */
    private fun businessCommand(block: suspend () -> String) {
        if (state.value.businessBusy) return
        mutableState.update { it.copy(businessBusy = true, businessMessage = null) }
        viewModelScope.launch {
            try { val message = block(); mutableState.update { it.copy(businessMessage = message) } }
            finally { mutableState.update { it.copy(businessBusy = false) } }
        }
    }

    /** 请求开启在线功能；首次开启进入明确同意对话框，不直接初始化 SDK。 */
    fun enableOnline() {
        if (!keyConfigured || disposed) return
        onlineEnabled = true
        refreshGate()
        if (consent) requestAddress()
    }

    /** 接受已展示的当前隐私说明；先确认记录落盘，再释放在线门禁。 */
    fun acceptPrivacy() {
        if ((!state.value.privacyRequired && state.value.gate != OnlineMapGate.ConsentRequired) || disposed || state.value.privacySaving) return
        savePrivacy(PrivacyConsentRecord(mapAccepted = true)) {
            consent = true
            onlineEnabled = true
            mutableState.update { it.copy(privacyRequired = false, mapConsentAccepted = true) }
            refreshGate()
            requestAddress()
        }
    }

    /** 拒绝并记录当前版本的明确选择，后续冷启动不反复强迫；主动开启仍可重新同意。 */
    fun rejectPrivacy() {
        if (state.value.privacySaving || disposed) return
        consent = false
        disableOnline()
        val sdkError = try { client.revokePrivacy(); null } catch (_: RuntimeException) {
            "SDK 授权状态更新失败，在线门禁已关闭；请退出应用后重新打开。"
        }
        mutableState.update { it.copy(privacyRequired = false, mapConsentAccepted = false, deviceConsentAccepted = false, deviceConsentDecided = true) }
        savePrivacy(PrivacyConsentRecord(deviceDecided = true)) {
            if (sdkError != null) mutableState.update { it.copy(privacyError = sdkError) }
        }
    }

    /** 撤回地图与设备定位用途同意，先关闭门禁并取消请求，不影响本地收藏及运行中的模拟停止。 */
    fun revokePrivacy() = rejectPrivacy()

    /** 单独记录设备定位用途同意；只有地图授权有效时接受，不自行申请系统权限或获取位置。 */
    fun acceptDeviceLocationConsent() {
        if (!consent || state.value.privacySaving || disposed) return
        savePrivacy(PrivacyConsentRecord(mapAccepted = true, deviceAccepted = true, deviceDecided = true)) {
            mutableState.update { it.copy(deviceConsentAccepted = true, deviceConsentDecided = true) }
        }
    }

    /** 记录仅使用地图、不读取设备位置的选择；保留地图同意，后续只有主动点击定位才重新询问。 */
    fun rejectDeviceLocationConsent() {
        if (!consent || state.value.privacySaving || disposed) return
        cancelDeviceLocation()
        mutableState.update { it.copy(deviceConsentAccepted = false, deviceConsentDecided = true) }
        savePrivacy(PrivacyConsentRecord(mapAccepted = true, deviceDecided = true)) {}
    }

    /** 持久化失败保持当前门禁关闭或旧状态；撤回已在内存生效，提示用户重试以避免重启恢复旧记录。 */
    private fun savePrivacy(record: PrivacyConsentRecord, onSaved: () -> Unit) {
        val store = privacyStore
        if (store == null) { onSaved(); return }
        mutableState.update { it.copy(privacySaving = true, privacyError = null) }
        viewModelScope.launch {
            val saved = store.write(record)
            if (disposed) return@launch
            mutableState.update { it.copy(privacySaving = false, privacyError = if (saved) null else
                "授权选择未能保存，请检查存储空间并重试；撤回失败时重启可能恢复旧记录。") }
            if (saved) onSaved()
        }
    }

    /** 在完整用途授权及在线门禁满足后尝试一次定位；自动尝试不会覆盖已有选点。 */
    fun locateDevice(automatic: Boolean = false) {
        val current = state.value
        val port = deviceLocation ?: return
        if (disposed || current.gate != OnlineMapGate.Ready || !current.deviceConsentAccepted || current.locating) return
        if (current.session.phase != SessionPhase.Idle) {
            locationPermissionMessage("模拟会话未结束，暂不获取设备位置。")
            return
        }
        if (automatic && (automaticLocationAttempted || current.selected != null)) return
        automaticLocationAttempted = true
        cancelDeviceLocation()
        mutableState.update { it.copy(locating = true, deviceLocationMessage = "正在定位…") }
        locationJob = viewModelScope.launch {
            val result = port.locate()
            coroutineContext.ensureActive()
            if (disposed || state.value.gate != OnlineMapGate.Ready || state.value.session.phase != SessionPhase.Idle) return@launch
            mutableState.update { when (result) {
                is DeviceLocationResult.Located -> it.copy(locating = false,
                    cameraRequest = MapCameraRequest(++cameraSequence, result.coordinate, if (result.accuracyMeters > 500) 12f else 16f),
                    deviceLocationMessage = if (result.accuracyMeters > 500) "大致设备位置已居中" else "设备位置已居中")
                is DeviceLocationResult.Unavailable -> it.copy(locating = false, deviceLocationMessage = result.message)
            } }
        }
    }

    /** 显示权限或用途恢复提示，不修改已选目标或会话事实。 */
    fun locationPermissionMessage(message: String) { mutableState.update { it.copy(deviceLocationMessage = message) } }

    /** 后台或业务意图到来时取消一次定位，并清除相机请求；不停止用户主动运行的模拟。 */
    fun cancelDeviceLocation() {
        locationJob?.cancel()
        locationJob = null
        mutableState.update { it.copy(locating = false, cameraRequest = null, deviceLocationMessage = null) }
    }

    /** 关闭在线功能并丢弃未完成结果；保留当前版本同意，关闭不等同于撤回。 */
    fun disableOnline() {
        cancelDeviceLocation()
        onlineEnabled = false
        searchToken = null
        addressToken = null
        mutableState.update { it.copy(searching = false, places = emptyList(), searchMessage = null,
            addressMessage = null, mapError = null) }
        refreshGate()
    }

    /** 同步门禁并清除旧地图错误，让重新开启能够再次尝试初始化。 */
    private fun refreshGate() {
        mutableState.update { it.copy(gate = resolveOnlineMapGate(keyConfigured, onlineEnabled, consent), mapError = null) }
    }

    /** 记录实际 SDK 错误；不把失败状态伪装成已加载地图。 */
    fun mapFailed(message: String) {
        if (state.value.gate == OnlineMapGate.Ready && !disposed) mutableState.update { it.copy(mapError = message) }
    }

    /** 更新手输纬度草稿并清除该字段旧错误，不更改原目标。 */
    fun changeLatitude(value: String) { mutableState.update { it.copy(latitude = value, latitudeError = null) } }

    /** 更新手输经度草稿并清除该字段旧错误，不更改原目标。 */
    fun changeLongitude(value: String) { mutableState.update { it.copy(longitude = value, longitudeError = null) } }

    /** 只修改下一次提交的坐标系，不将现有目标重新贴标签。 */
    fun changeInputSystem(value: MapCoordinateSystem) { mutableState.update { it.copy(inputSystem = value) } }

    /** 校验手输表单；失败时保留上一次有效目标，成功时创建新的原始目标。 */
    fun submitManual() {
        val current = state.value
        val result = validateCoordinateInput(current.latitude, current.longitude, current.inputSystem)
        mutableState.update { it.copy(latitudeError = result.latitudeError, longitudeError = result.longitudeError) }
        result.coordinate?.let { select(SelectedPosition(it, PositionSource.Manual)) }
    }

    /** 真正点击地图才创建目标；门禁不满足或地图失败时拒绝事件。 */
    fun pickMap(coordinate: MapCoordinate) {
        if (state.value.gate != OnlineMapGate.Ready || state.value.mapError != null) return
        require(coordinate.system == MapCoordinateSystem.GCJ02)
        select(SelectedPosition(coordinate, PositionSource.Map))
    }

    /** 选择当前请求的真实搜索候选；过期列表或关闭在线功能后的事件无效。 */
    fun pickPlace(place: MapPlace) {
        if (state.value.gate != OnlineMapGate.Ready || place !in state.value.places) return
        select(SelectedPosition(place.coordinate, PositionSource.Search, place.title, place.address.takeIf { it.isNotBlank() }))
    }

    /** 替换目标并取消旧地址结果的应用；搜索候选本身保持可继续选择。 */
    private fun select(target: SelectedPosition) {
        if (disposed) return
        cancelDeviceLocation()
        addressToken = null
        mutableState.update { it.copy(selected = target, addressMessage = null) }
        if (target.address == null) requestAddress()
    }

    /** 更新关键词并使旧搜索回调失效，避免返回结果与输入不匹配。 */
    fun changeQuery(value: String) {
        invalidateSearch()
        mutableState.update { it.copy(query = value) }
    }

    /** 更新查询城市；空字符串表示不限城市，同样使旧请求失效。 */
    fun changeCity(value: String) {
        invalidateSearch()
        mutableState.update { it.copy(city = value) }
    }

    /** 清除旧候选与等待状态；不承诺取消 SDK 已发送的网络请求。 */
    private fun invalidateSearch() {
        searchToken = null
        mutableState.update { it.copy(searching = false, places = emptyList(), searchMessage = null) }
    }

    /** 搜索当前输入；Key/隐私门禁未通过或关键词为空时不调用 SDK。 */
    fun search() {
        val current = state.value
        if (disposed || current.gate != OnlineMapGate.Ready || current.query.isBlank()) return
        val token = Any()
        searchToken = token
        mutableState.update { it.copy(searching = true, places = emptyList(), searchMessage = "正在查询…") }
        client.search(current.query, current.city) callback@ { result ->
            if (disposed || searchToken !== token || state.value.gate != OnlineMapGate.Ready) return@callback
            searchToken = null
            mutableState.update { state -> when (result) {
                is MapQueryResult.Success -> state.copy(searching = false, places = result.value,
                    searchMessage = if (result.value.isEmpty()) "未找到地点，请换关键词或城市。" else "选择结果作为目标位置")
                is MapQueryResult.Failure -> state.copy(searching = false, places = emptyList(), searchMessage = result.message)
            } }
        }
    }

    /** 为当前未有地址的目标发起真实查询；旧目标响应不能覆盖新目标或关闭后的页面。 */
    private fun requestAddress() {
        val current = state.value
        val target = current.selected ?: return
        if (disposed || current.gate != OnlineMapGate.Ready || target.address != null) return
        val token = Any()
        addressToken = token
        mutableState.update { it.copy(addressMessage = "正在查询地址…") }
        client.reverseGeocode(target.coordinate) { result ->
            if (disposed || addressToken !== token || state.value.gate != OnlineMapGate.Ready) return@reverseGeocode
            addressToken = null
            mutableState.update { state -> when (result) {
                is MapQueryResult.Success -> state.copy(selected = target.copy(address = result.value),
                    addressMessage = if (result.value == null) "该位置暂无地址信息。" else null)
                is MapQueryResult.Failure -> state.copy(addressMessage = result.message)
            } }
        }
    }

    /** ViewModel 销毁后清空令牌；不再应用仍由 SDK 完成的回调。 */
    override fun onCleared() {
        cancelDeviceLocation()
        disposed = true
        catalogToken = null
        searchToken = null
        addressToken = null
        super.onCleared()
    }
}
