package com.sunnyfor.gpsmock.feature.position

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.platform.ui.adaptation.DesignScaledLayout
import com.sunnyfor.gpsmock.platform.ui.adaptation.WindowWidthClass
import com.sunnyfor.gpsmock.platform.ui.mapui.OnlineMapGate
import com.sunnyfor.gpsmock.platform.ui.permissions.MockAuthorization
import com.sunnyfor.gpsmock.platform.ui.permissions.openDeveloperOptions
import com.sunnyfor.gpsmock.platform.ui.permissions.rememberMockAuthorization
import com.sunnyfor.gpsmock.platform.ui.theme.AppGlyph
import com.sunnyfor.gpsmock.platform.ui.theme.AppIcon
import kotlinx.coroutines.delay
import java.util.Locale

/** 地图优先旅程的三个展示目的地；切换只改变 UI，不启动、停止或修改业务数据。 */
private enum class PositionPage(
    /** 底栏可见名称。 */
    val label: String,

    /** 与目的地配对的图标。 */
    val glyph: AppGlyph
) {
    /** 首页地图与目标选择。 */
    Selection("选点", AppGlyph.Map),

    /** 本机收藏管理。 */
    Favorites("常用", AppGlyph.Star),

    /** 在线服务、系统授权与运行说明。 */
    Settings("设置", AppGlyph.Settings)
}

/** 审核通过的 A 方案原生实现：窄屏上下、宽屏左右；主操作固定在滚动内容外。 */
@Composable
internal fun AtlasPositionScreen(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier, mapContent: @Composable () -> Unit) {
    var page by rememberSaveable { mutableStateOf(PositionPage.Selection) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    BackHandler(page != PositionPage.Selection && !manual && !search && state.editor == null && state.pendingDelete == null && !state.confirmClear) {
        page = PositionPage.Selection
    }
    DesignScaledLayout(modifier) { layout ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                AtlasHeader(page, layout.isCompactHeight) { page = PositionPage.Settings }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (page) {
                        PositionPage.Selection -> {
                            if (layout.widthClass == WindowWidthClass.Compact) {
                                if (layout.isCompactHeight) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    AtlasMap(state, actions, Modifier.height(220.dp), { search = true }, mapContent)
                                    AtlasTarget(state, actions, Modifier.fillMaxWidth(), true, { manual = true }, { page = PositionPage.Favorites }, scrollable = false)
                                } else Column(Modifier.fillMaxSize()) {
                                    AtlasMap(state, actions, Modifier.weight(0.52f), { search = true }, mapContent)
                                    AtlasTarget(state, actions, Modifier.weight(0.48f), false, { manual = true }, { page = PositionPage.Favorites })
                                }
                            } else {
                                Row(Modifier.fillMaxSize()) {
                                    AtlasMap(state, actions, Modifier.weight(1f), { search = true }, mapContent)
                                    AtlasTarget(state, actions, Modifier.width(320.dp), layout.isCompactHeight, { manual = true }, { page = PositionPage.Favorites })
                                }
                            }
                        }
                        PositionPage.Favorites -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("把常用的位置留在手边", style = MaterialTheme.typography.titleLarge)
                            Text("仅保存在本机 · 离线可用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SavedPositionCard(state, actions) { page = PositionPage.Selection }
                            state.businessMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        PositionPage.Settings -> AtlasSettings(state, actions)
                    }
                }
                SessionActionBar(state, actions, layout.isCompactHeight)
                AtlasNavigation(page, layout.isCompactHeight) { page = it }
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("设置目标坐标") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ManualCoordinateCard(state, actions) { manual = false } } },
        confirmButton = { TextButton(onClick = { manual = false }) { Text("返回地图") } })
    if (search) AlertDialog(onDismissRequest = { search = false }, title = { Text("寻找一个位置") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            SearchCard(state, actions, onPicked = { search = false }, onEnableOnline = { search = false; actions.enableOnline() })
        } },
        confirmButton = { TextButton(onClick = { search = false }) { Text("返回地图") } })
}

/** 紧凑品牌标题与设置快捷入口，不把开发者信息占据首页主视觉。 */
@Composable
private fun AtlasHeader(page: PositionPage, compact: Boolean, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { AppIcon(AppGlyph.Pin) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(if (page == PositionPage.Selection) "定位助手" else if (page == PositionPage.Favorites) "常用位置" else "应用设置",
                style = MaterialTheme.typography.titleMedium)
            if (!compact) Text("固定位置 · 本地测试", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = settings, modifier = Modifier.semantics { contentDescription = "打开设置" }) {
            AppIcon(AppGlyph.Settings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 搜索入口放在地图上方独立区域，原生地图底部版权/Logo 不被目标面板或浮层覆盖。 */
@Composable
private fun AtlasMap(state: PositionPickerState, actions: PositionPickerViewModel, modifier: Modifier,
    search: () -> Unit, mapContent: @Composable () -> Unit) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp)) {
        Surface(onClick = search, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppGlyph.Search, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("搜索地点或地址", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        MapRegion(state, actions, Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp), mapContent)
    }
}

/**
 * 展示真实待选目标或活动会话冻结目标；后者优先，只有目标内容/来源变化才显示下一次目标提示。
 * 低高度窄窗口交由外层整页滚动，其余窗口保持面板独立滚动，不用固定高度裁切长内容。
 */
@Composable
private fun AtlasTarget(state: PositionPickerState, actions: PositionPickerViewModel, modifier: Modifier, compact: Boolean,
    manual: () -> Unit, favorites: () -> Unit, scrollable: Boolean = true) {
    val frozen = state.session.target.takeIf { state.session.phase != SessionPhase.Idle }
    val selected = state.selected
    val latitude = frozen?.coordinate?.latitude ?: selected?.coordinate?.latitude
    val longitude = frozen?.coordinate?.longitude ?: selected?.coordinate?.longitude
    val pendingChanged = frozen != null && selected != null &&
        (frozen.coordinate.latitude != selected.coordinate.latitude || frozen.coordinate.longitude != selected.coordinate.longitude ||
            frozen.coordinate.system.name != selected.coordinate.system.name || frozen.displayName != selected.title.orEmpty() ||
            frozen.sourceId != selected.savedId || frozen.sourceRevision != selected.savedRevision)
    val scroll = rememberScrollState()
    Surface(modifier.then(if (scrollable) Modifier.fillMaxSize() else Modifier.fillMaxWidth()), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().then(if (scrollable) Modifier.fillMaxSize().verticalScroll(scroll) else Modifier)
            .padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            if (!compact) Box(Modifier.align(Alignment.CenterHorizontally).width(28.dp).height(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (frozen != null) "当前会话 · 目标已冻结" else "待选目标", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = CircleShape, color = if (state.session.phase == SessionPhase.CleanupRequired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                    Text(state.session.phase.displayName(), Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                        color = if (state.session.phase == SessionPhase.CleanupRequired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(frozen?.displayName?.ifBlank { "临时坐标" } ?: selected?.title?.takeIf { it.isNotBlank() }
                        ?: if (selected != null) "已选目标" else "选择一个位置",
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (latitude != null && longitude != null) {
                        Text("纬度 ${String.format(Locale.ROOT, "%.6f", latitude)}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text("经度 ${String.format(Locale.ROOT, "%.6f", longitude)} · ${frozen?.coordinate?.system?.name ?: selected?.coordinate?.system?.name}",
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    } else Text("点击地图，或从手输与常用位置开始", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.editSaved() }, enabled = selected != null && !state.businessBusy,
                    modifier = Modifier.semantics { contentDescription = "收藏当前目标" }) {
                    AppIcon(AppGlyph.Star, tint = if (selected != null && !state.businessBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                }
            }
            if (pendingChanged) Text("待选：${selected.title ?: "临时坐标"} · ${selected.coordinate.latitude}, ${selected.coordinate.longitude}；停止后才能启动新目标。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (frozen == null) (selected?.address ?: state.addressMessage)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = manual, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    AppIcon(AppGlyph.Input, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("手输坐标")
                }
                OutlinedButton(onClick = favorites, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    AppIcon(AppGlyph.Star, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("常用位置")
                }
            }
            if (state.savedPositions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.savedPositions.take(3).forEach { saved ->
                    Surface(onClick = { actions.pickSaved(saved) }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current)) {
                        Text(saved.name, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            state.businessMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** 所有目的地共用的主操作。通知请求只在用户点击启动时触发，运行事实只来自会话投影。 */
@Composable
private fun SessionActionBar(state: PositionPickerState, actions: PositionPickerViewModel, compact: Boolean) {
    val context = LocalContext.current
    val view = state.session
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.startSimulation() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(view.phase) {
        while (view.phase == SessionPhase.Running) { now = SystemClock.elapsedRealtimeNanos(); delay(1_000) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sessionErrorText(view.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (view.phase == SessionPhase.Running) Text("已运行 ${((now - (view.startedAtNanos ?: now)).coerceAtLeast(0) / 1_000_000_000)} 秒 · 最近写入确认 ${((now - (view.lastWriteNanos ?: now)).coerceAtLeast(0) / 1_000_000)}ms 前",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val stop = view.phase == SessionPhase.Running || view.phase == SessionPhase.Starting
        Button(onClick = {
            when (view.phase) {
                SessionPhase.Idle -> if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else actions.startSimulation()
                SessionPhase.Starting, SessionPhase.Running -> actions.stopSimulation()
                SessionPhase.CleanupRequired -> actions.retryCleanup()
                SessionPhase.Stopping -> Unit
            }
        }, enabled = !state.businessBusy && view.phase != SessionPhase.Stopping && (view.phase != SessionPhase.Idle || state.selected != null),
            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (stop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            AppIcon(if (stop) AppGlyph.Stop else AppGlyph.Play, Modifier.size(18.dp), tint = if (stop) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(when (view.phase) {
                SessionPhase.Idle -> "启动固定位置模拟"
                SessionPhase.Starting, SessionPhase.Running -> "停止模拟"
                SessionPhase.Stopping -> "正在检查/清理，请稍候。"
                SessionPhase.CleanupRequired -> "重试清理"
            })
        }
        if (!compact) Text("仅用于本机测试 · 保留系统 mock 标记", Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 三目的地底栏，文字与选中语义并存，颜色不是唯一状态标识。 */
@Composable
private fun AtlasNavigation(page: PositionPage, compact: Boolean, navigate: (PositionPage) -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth()) {
        PositionPage.entries.forEach { item ->
            Column(Modifier.weight(1f).clickable(role = Role.Tab, onClick = { navigate(item) })
                .semantics { selected = page == item }
                .heightIn(min = maxOf(if (compact) 48.dp else 60.dp, LocalMinimumInteractiveComponentSize.current)).padding(vertical = if (compact) 4.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AppIcon(item.glyph, tint = if (page == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.label, style = MaterialTheme.typography.labelSmall,
                    color = if (page == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 次级配置页显示真实授权观察、隐私状态与边界说明；不把预检查当作引擎已启动。 */
@Composable
private fun AtlasSettings(state: PositionPickerState, actions: PositionPickerViewModel) {
    val context = LocalContext.current
    val authorization = rememberMockAuthorization()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSection("模拟位置授权", when (authorization) {
            MockAuthorization.Allowed -> "已选择本应用；最终仍以启动结果为准。"
            MockAuthorization.Denied -> "开发者选项 → 选择模拟位置信息应用 → 定位助手。"
            MockAuthorization.Unknown -> "待实际启动验证；若拒绝请在开发者选项选择本应用。"
        }) { OutlinedButton(onClick = { openDeveloperOptions(context) }) { Text("打开开发者选项") } }
        SettingsSection("在线地图与隐私", "${if (state.gate == OnlineMapGate.Ready) "在线功能已开启" else "在线功能未开启"}。本应用不读取实时位置，地图、搜索与地址查询由高德提供；同意仅保留在本次会话。") {
            if (state.gate == OnlineMapGate.Ready) TextButton(actions::disableOnline) { Text("关闭在线") }
            else TextButton(actions::enableOnline) { Text("开启在线地图") }
        }
        SettingsSection("运行与通知", "仅表示本方 provider 写入成功，不保证其他应用接受模拟位置。运行目标冻结，进程中断后不会自动重启模拟。") {
            val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            Text(if (allowed) "通知权限已允许。" else "点击启动时申请通知权限；未允许时可随时返回界面停止。", style = MaterialTheme.typography.bodySmall)
        }
        SettingsSection("本机数据", "收藏仅保存在本机；WGS-84 与 GCJ-02 坐标明确区分。批量清空仅限无活动会话，并要求再次确认。") {}
    }
}

/** 设置页的单一信息组，集中视觉外壳，不接管权限或业务命令。 */
@Composable
private fun SettingsSection(title: String, description: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
