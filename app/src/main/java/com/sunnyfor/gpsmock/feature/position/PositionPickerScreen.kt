package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.SavedStateRegistryOwner
import com.sunnyfor.gpsmock.platform.ui.adaptation.SystemDensityContent
import com.sunnyfor.gpsmock.platform.ui.mapui.AMapPickerMap
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.platform.ui.theme.AppSpacing
import com.sunnyfor.gpsmock.platform.ui.theme.AppGlyph
import com.sunnyfor.gpsmock.platform.ui.theme.AppIcon

/** 高德完整隐私政策的官方链接；简短页面说明不替代完整披露。 */
private const val AMAP_PRIVACY_URL = "https://lbs.amap.com/pages/privacy/"

/**
 * 观察页面状态，只在门禁通过且 SDK 没有报错时组合原生地图。
 * @param viewModel Activity 保留的选点协调器。
 * @param apiKey 构建时配置值，不在页面展示。
 * @param owner 地图生命周期和状态保存宿主。
 * @param modifier 已扣除系统安全区的有界区域。
 */
@Composable
internal fun PositionPickerRoute(viewModel: PositionPickerViewModel, apiKey: String,
    owner: SavedStateRegistryOwner, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PositionPickerScreen(state, viewModel, modifier) {
        SystemDensityContent {
            AMapPickerMap(apiKey, owner, state.selected?.coordinate, viewModel::pickMap,
                viewModel::mapFailed, Modifier.fillMaxSize())
        }
    }
}

/**
 * 地图优先的目标选择入口：界面重排与真实业务状态分离。
 *
 * 表单独立滚动，地图处于有界区域。大字体或键盘压缩空间时仍可滚动访问字段；
 * 待选目标保存在页面内存；收藏与会话反馈只来自公开用例，不自行假造成功。测试可注入无 SDK 的地图槽位。
 *
 * @param state 不可变页面快照。
 * @param actions 状态意图接收方。
 * @param modifier 实际可用容器，不应放进无界滚动父级。
 * @param mapContent 门禁通过后才执行的真实地图或测试槽位。
 */
@Composable
internal fun PositionPickerScreen(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier = Modifier, mapContent: @Composable () -> Unit) {
    AtlasPositionScreen(state, actions, modifier, mapContent)
    if (state.gate == OnlineMapGate.ConsentRequired) PrivacyDialog(actions)
    PositionBusinessDialogs(state, actions)
}

/** 根据门禁呈现真实地图或配置回退，保留 SDK 版权和 Logo，不用面板遮挡它们。 */
@Composable
internal fun MapRegion(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier, mapContent: @Composable () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxSize()) {
            if (state.gate == OnlineMapGate.Ready) {
                Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                    Text("地图选点 · GCJ-02", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = actions::disableOnline) { Text("关闭在线") }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val showDecoration = maxHeight >= 200.dp
                val tight = maxHeight < 130.dp
                val showHint = maxHeight >= 110.dp
                if (state.gate == OnlineMapGate.Ready && state.mapError == null) mapContent()
                else if (tight) Column(Modifier.padding(AppSpacing.small).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        Text(mapGateTitle(state), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (state.gate == OnlineMapGate.Disabled) TextButton(actions::enableOnline) { Text("开启在线地图", style = MaterialTheme.typography.labelMedium) }
                        else if (state.mapError != null) TextButton(onClick = { actions.disableOnline(); actions.enableOnline() }) { Text("重新尝试") }
                    }
                    if (showHint) Text("手输与常用位置仍可离线使用", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else Column(Modifier.padding(AppSpacing.medium).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.extraSmall)) {
                    if (showDecoration) AppIcon(AppGlyph.Pin, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(mapGateTitle(state), style = MaterialTheme.typography.titleMedium)
                    Text(state.mapError ?: if (state.gate == OnlineMapGate.MissingKey) {
                        "在 local.properties 填写 AMAP_API_KEY 后重新构建。现在仍可手输坐标。"
                    } else "手输与常用位置仍可离线使用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.gate == OnlineMapGate.Disabled) {
                        OutlinedButton(onClick = actions::enableOnline) { Text("开启在线地图") }
                    } else if (state.mapError != null) {
                        OutlinedButton(onClick = { actions.disableOnline(); actions.enableOnline() }) { Text("重新尝试") }
                    }
                }
            }
        }
    }
}

/** 地图门禁/错误的真实标题，不用“已开启”推断 SDK 已成功渲染。 */
private fun mapGateTitle(state: PositionPickerState): String = when (state.gate) {
    OnlineMapGate.MissingKey -> "地图 Key 未配置"
    OnlineMapGate.Ready -> "地图暂不可用"
    else -> "在线地图未开启"
}


/**
 * 缺 Key、拒绝隐私或地图失败时仍可手输；字段顺序明确为纬度、经度。
 * 校验失败保留草稿与原目标，成功后才通知宿主关闭输入界面。
 *
 * @param onSelected 同步校验与选点成功后的 UI 回调，不代表已收藏或已启动。
 */
@Composable
internal fun ManualCoordinateCard(state: PositionPickerState, actions: PositionPickerViewModel, onSelected: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("手输坐标", style = MaterialTheme.typography.titleMedium)
            Text("十进制度 · 纬度在前，经度在后", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.latitude, actions::changeLatitude, Modifier.fillMaxWidth(),
                label = { Text("纬度") }, singleLine = true, isError = state.latitudeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { state.latitudeError?.let { Text(it) } })
            OutlinedTextField(state.longitude, actions::changeLongitude, Modifier.fillMaxWidth(),
                label = { Text("经度") }, singleLine = true, isError = state.longitudeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { state.longitudeError?.let { Text(it) } })
            Row(Modifier.fillMaxWidth()) {
                MapCoordinateSystem.entries.forEach { system ->
                    Row(Modifier.weight(1f).selectable(state.inputSystem == system, role = Role.RadioButton,
                        onClick = { actions.changeInputSystem(system) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.inputSystem == system, onClick = null)
                        Text(system.displayName(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Button(onClick = {
                actions.submitManual()
                if (actions.state.value.latitudeError == null && actions.state.value.longitudeError == null) onSelected()
            }, Modifier.fillMaxWidth()) { Text("使用此坐标") }
        }
    }
}


/**
 * 只查询真实 POI；未同意或缺 Key 时显式禁用，不提供假候选。
 *
 * @param onPicked 选择候选后的 UI 返回回调，不执行收藏或启动。
 * @param onEnableOnline 宿主先关闭搜索界面再进入隐私披露，避免叠加两个对话框。
 */
@Composable
internal fun SearchCard(state: PositionPickerState, actions: PositionPickerViewModel, onPicked: () -> Unit = {},
    onEnableOnline: () -> Unit = actions::enableOnline) {
    val online = state.gate == OnlineMapGate.Ready
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("搜索地点", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.query, actions::changeQuery, Modifier.fillMaxWidth(), enabled = online,
                singleLine = true, label = { Text("地点或地址关键词") })
            OutlinedTextField(state.city, actions::changeCity, Modifier.fillMaxWidth(), enabled = online,
                singleLine = true, label = { Text("城市（可选）") }, supportingText = { Text("建议中文城市名或区号（如北京/010）；留空不限城市") })
            OutlinedButton(actions::search, Modifier.fillMaxWidth(), enabled = online && state.query.isNotBlank() && !state.searching) {
                Text("查询地点")
            }
            if (!online) Text("配置 Key 并同意隐私说明后可用。", style = MaterialTheme.typography.bodySmall)
            if (state.gate == OnlineMapGate.Disabled) TextButton(onEnableOnline) { Text("开启在线地图") }
            if (state.searching) CircularProgressIndicator(Modifier.width(24.dp))
            state.searchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.places.forEach { place ->
                OutlinedButton(onClick = { actions.pickPlace(place); onPicked() }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(place.title)
                        if (place.address.isNotBlank()) Text(place.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** 展示供应方、用途和数据处理说明，只有明确点击同意才释放 SDK 门禁。 */
@Composable
private fun PrivacyDialog(actions: PositionPickerViewModel) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = actions::disableOnline,
        title = { Text("启用高德在线服务") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
                Text("地图、地点搜索和地址查询由北京高德图强科技有限公司提供。开启后，查询关键词、城市、所选坐标会发送给高德；SDK 还可能处理网络地址、设备及应用信息，详情请阅读完整隐私政策。")
                Text("本应用不读取设备实时位置，不申请真实定位权限。同意仅保留在本次会话；拒绝后仍可手输和管理收藏。关闭在线功能会释放地图并忽略旧结果，但不能撤销已发出的请求。")
                TextButton(onClick = { uriHandler.openUri(AMAP_PRIVACY_URL) }) { Text("阅读高德完整隐私政策") }
            }
        },
        confirmButton = { TextButton(actions::acceptPrivacy) { Text("同意并开启") } },
        dismissButton = { TextButton(actions::disableOnline) { Text("暂不开启") } }
    )
}

/** 显示带分隔符的标准坐标系名称，避免混淆两种坐标数值。 */
private fun MapCoordinateSystem.displayName(): String = when (this) {
    MapCoordinateSystem.WGS84 -> "WGS-84"
    MapCoordinateSystem.GCJ02 -> "GCJ-02"
}
