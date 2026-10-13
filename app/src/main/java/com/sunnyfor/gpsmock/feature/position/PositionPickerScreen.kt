package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.SavedStateRegistryOwner
import com.sunnyfor.gpsmock.platform.ui.adaptation.DesignScaledLayout
import com.sunnyfor.gpsmock.platform.ui.adaptation.SystemDensityContent
import com.sunnyfor.gpsmock.platform.ui.adaptation.WindowWidthClass
import com.sunnyfor.gpsmock.platform.ui.mapui.AMapPickerMap
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.platform.ui.theme.AppSpacing
import java.util.Locale

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
 * 目标选择页面：窄窗口上下分区，宽窗口左右分区，主题只提供外观令牌。
 *
 * 表单独立滚动，地图处于有界区域。大字体或键盘压缩空间时仍可滚动访问字段；
 * 待选目标保存在页面内存；收藏与会话反馈只来自公开用例，不自行假造成功。测试可注入无 SDK 的地图槽位。
 * @param state 不可变页面快照。
 * @param actions 状态意图接收方。
 * @param modifier 实际可用容器，不应放进无界滚动父级。
 * @param mapContent 门禁通过后才执行的真实地图或测试槽位。
 */
@Composable
internal fun PositionPickerScreen(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier = Modifier, mapContent: @Composable () -> Unit) {
    DesignScaledLayout(modifier) { layout ->
        Column(Modifier.fillMaxSize().padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("定位助手", style = MaterialTheme.typography.headlineSmall)
                    Text("固定位置 · 选择目标", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(state.session.phase.displayName(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (layout.widthClass == WindowWidthClass.Compact) {
                MapRegion(state, actions, Modifier.weight(if (layout.isCompactHeight) 0.35f else 0.45f).fillMaxWidth(), mapContent)
                PickerPanel(state, actions, Modifier.weight(0.55f).fillMaxWidth())
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
                    MapRegion(state, actions, Modifier.weight(1f).fillMaxSize(), mapContent)
                    PickerPanel(state, actions, Modifier.width(320.dp).fillMaxSize())
                }
            }
        }
    }
    if (state.gate == OnlineMapGate.ConsentRequired) PrivacyDialog(actions)
    PositionBusinessDialogs(state, actions)
}

/** 根据门禁呈现真实地图或配置回退，保留 SDK 版权和 Logo，不用面板遮挡它们。 */
@Composable
private fun MapRegion(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier, mapContent: @Composable () -> Unit) {
    Card(modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                Text("地图选点 · GCJ-02", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (state.gate == OnlineMapGate.Ready) {
                    TextButton(onClick = actions::disableOnline) { Text("关闭在线") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.gate == OnlineMapGate.Ready && state.mapError == null) mapContent()
                else Column(Modifier.padding(AppSpacing.large).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    Text(when (state.gate) {
                        OnlineMapGate.MissingKey -> "地图 Key 未配置"
                        OnlineMapGate.Ready -> "地图暂不可用"
                        else -> "在线地图未开启"
                    }, style = MaterialTheme.typography.titleMedium)
                    Text(state.mapError ?: if (state.gate == OnlineMapGate.MissingKey) {
                        "在 local.properties 填写 AMAP_API_KEY 后重新构建。现在仍可手输坐标。"
                    } else "开启后可点击地图选点、搜索地点和查询地址。", style = MaterialTheme.typography.bodySmall)
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

/** 独立滚动的手输、目标和真实搜索面板，窗口缩小时不挤压地图内部组件。 */
@Composable
private fun PickerPanel(state: PositionPickerState, actions: PositionPickerViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        SessionControlCard(state, actions)
        if (state.selected != null) TargetCard(state)
        ManualCoordinateCard(state, actions)
        SavedPositionCard(state, actions)
        SearchCard(state, actions)
        Text("固定位置模拟仅用于本机测试，输出保留系统 mock 标记。收藏仅保存在本机；进程中断后不会自动重启模拟。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 缺 Key、拒绝隐私或地图失败时仍可手输；字段顺序明确为纬度、经度。 */
@Composable
private fun ManualCoordinateCard(state: PositionPickerState, actions: PositionPickerViewModel) {
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
            Button(actions::submitManual, Modifier.fillMaxWidth()) { Text("使用此坐标") }
        }
    }
}

/** 展示原始目标及真实地址状态；地址失败不取消合法的坐标选择。 */
@Composable
private fun TargetCard(state: PositionPickerState) {
    val target = state.selected ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("已选目标", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            target.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Text("纬度 ${String.format(Locale.ROOT, "%.6f", target.coordinate.latitude)}")
            Text("经度 ${String.format(Locale.ROOT, "%.6f", target.coordinate.longitude)}")
            Text("${target.coordinate.system.displayName()} · ${when (target.source) {
                PositionSource.Map -> "地图选点"
                PositionSource.Search -> "搜索结果"
                PositionSource.Manual -> "手输坐标"
                PositionSource.Saved -> "常用位置"
            }}", style = MaterialTheme.typography.labelLarge)
            Text(target.address ?: state.addressMessage ?: "未查询地址；可开启在线地图查询。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 只查询真实 POI；未同意或缺 Key 时显式禁用，不提供假候选。 */
@Composable
private fun SearchCard(state: PositionPickerState, actions: PositionPickerViewModel) {
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
            if (state.searching) CircularProgressIndicator(Modifier.width(24.dp))
            state.searchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            state.places.forEach { place ->
                OutlinedButton(onClick = { actions.pickPlace(place) }, modifier = Modifier.fillMaxWidth()) {
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
