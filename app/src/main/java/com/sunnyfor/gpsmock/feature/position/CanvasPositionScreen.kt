package com.sunnyfor.gpsmock.feature.position

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/**
 * 已确认的地图画布：顶部一体化、地图弹性占据余量，目标摘要与主操作分开测量。
 *
 * 窄窗口上下布局，宽窗口显式改为地图与 320 设计单位操作区并列；
 * 低高度或大字体下摘要允许滚动，主操作不进入滚动区，不固定地图百分比裁切按钮。
 * 收藏与会话仍只经公开契约操作；地图槽位不会被放在滚动或底部弹层内反复创建。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanvasPositionScreen(state: PositionPickerState, actions: PositionPickerViewModel,
    modifier: Modifier, mapContent: @Composable () -> Unit, onLocate: () -> Unit) {
    var favorites by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var readingPrivacy by rememberSaveable { mutableStateOf(false) }
    BackHandler(favorites && panel == null && state.editor == null && state.pendingDelete == null && !state.confirmClear) { favorites = false }
    DesignScaledLayout(modifier) { layout ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val summaryLimit = maxHeight * if (layout.isCompactHeight) 0.22f else 0.28f
                Column(Modifier.fillMaxSize()) {
                    CanvasHeader(favorites, layout.isCompactHeight, { favorites = false }, { panel = "settings" })
                    if (favorites) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("保存在本机 · 点击选用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SavedPositionCard(state, actions) { favorites = false }
                        }
                        CanvasSessionAction(state, actions, layout.isCompactHeight)
                    } else if (layout.widthClass == WindowWidthClass.Compact) {
                        CanvasMap(state, actions, Modifier.weight(1f).fillMaxWidth(), { panel = "search" }, onLocate, mapContent)
                        // 给摘要设上限而不是整张固定高度；48dp 主按钮和工具入口始终在滚动区之外。
                        CanvasTarget(state, Modifier.fillMaxWidth().heightIn(max = summaryLimit)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = if (layout.isCompactHeight) 4.dp else 12.dp),
                                layout.isCompactHeight) { panel = "detail" }
                        CanvasSessionAction(state, actions, layout.isCompactHeight)
                        CanvasTools({ favorites = true }, { panel = "manual" })
                    } else {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            CanvasMap(state, actions, Modifier.weight(1f).fillMaxHeight(), { panel = "search" }, onLocate, mapContent)
                            Column(Modifier.width(320.dp).fillMaxHeight()) {
                                CanvasTarget(state, Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), false) { panel = "detail" }
                                CanvasSessionAction(state, actions, layout.isCompactHeight)
                                CanvasTools({ favorites = true }, { panel = "manual" })
                            }
                        }
                    }
                }
            }
        }
    }
    if (panel != null) ModalBottomSheet(onDismissRequest = { panel = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (panel) { "manual" -> "输入坐标"; "search" -> "寻找地点"; "detail" -> "位置详情"; else -> "设置" },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { panel = null }) { Text("返回地图") }
            }
            when (panel) {
                "manual" -> ManualCoordinateCard(state, actions) { panel = null }
                "search" -> SearchCard(state, actions, onPicked = { panel = null }, onEnableOnline = { panel = null; actions.enableOnline() })
                "detail" -> CanvasDetails(state) { panel = null; actions.editSaved() }
                else -> CanvasSettings(state, actions, onLocate = { panel = null; onLocate() },
                    privacy = { readingPrivacy = true }, close = { panel = null })
            }
        }
    }
    if (readingPrivacy) PrivacyNoticeReader { readingPrivacy = false }
}

/** 简短品牌与单个设置入口；背景取 surface，与外层系统安全区一致，不做卡片拼接。 */
@Composable
private fun CanvasHeader(favorites: Boolean, compact: Boolean, back: () -> Unit, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 58.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (favorites) IconButton(onClick = back, modifier = Modifier.semantics { contentDescription = "返回地图" }) { AppIcon(AppGlyph.Back) }
        else AppIcon(AppGlyph.Pin, Modifier.size(20.dp))
        Text(if (favorites) "常用位置" else "定位助手", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = settings, modifier = Modifier.semantics { contentDescription = "打开设置" }) {
            AppIcon(AppGlyph.Settings, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** 搜索悬浮在地图顶部，居中按钮距离地图底部留出版权区；地图与目标面板不重叠。 */
@Composable
private fun CanvasMap(state: PositionPickerState, actions: PositionPickerViewModel, modifier: Modifier,
    search: () -> Unit, locate: () -> Unit, mapContent: @Composable () -> Unit) {
    Box(modifier) {
        MapRegion(state, actions, Modifier.fillMaxSize(), mapContent)
        if (state.gate == OnlineMapGate.Ready && state.mapError == null) {
            Surface(onClick = search, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppGlyph.Search, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("搜索地点或地址", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppIcon(AppGlyph.Arrow, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(onClick = locate, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 52.dp),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Box(Modifier.size(maxOf(48.dp, LocalMinimumInteractiveComponentSize.current))
                    .semantics { contentDescription = "居中到设备位置" }, contentAlignment = Alignment.Center) {
                    if (state.locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else AppIcon(AppGlyph.Locate)
                }
            }
        }
    }
}

/** 摘要只保留名称、地址和必要状态；经纬度、收藏与运行细节在详情中查看。 */
@Composable
private fun CanvasTarget(state: PositionPickerState, modifier: Modifier, compact: Boolean, detail: () -> Unit) {
    val frozen = state.session.target.takeIf { state.session.phase != SessionPhase.Idle }
    val chosen = state.selected
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
        if (!compact) Surface(Modifier.align(Alignment.CenterHorizontally).width(29.dp).height(3.dp),
            color = MaterialTheme.colorScheme.outlineVariant, shape = CircleShape) {}
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (frozen != null) "当前模拟目标" else "目标位置", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.session.phase != SessionPhase.Idle) Text(state.session.phase.displayName(), style = MaterialTheme.typography.labelSmall,
                color = if (state.session.phase == SessionPhase.CleanupRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(frozen?.displayName?.ifBlank { "临时坐标" } ?: chosen?.title?.takeIf { it.isNotBlank() }
                ?: if (chosen != null) "已选目标" else "想把位置设在哪里？", Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (frozen != null || chosen != null) IconButton(onClick = detail,
                modifier = Modifier.semantics { contentDescription = "查看目标详情" }) { AppIcon(AppGlyph.More) }
        }
        Text(if (frozen != null) "目标已冻结 · 新选点不会替换当前模拟" else chosen?.address ?: "点击地图选点，或从常用和坐标开始",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (state.session.phase != SessionPhase.Idle && chosen != null && frozen != null &&
            (chosen.coordinate.latitude != frozen.coordinate.latitude || chosen.coordinate.longitude != frozen.coordinate.longitude ||
                chosen.coordinate.system.name != frozen.coordinate.system.name || chosen.savedId != frozen.sourceId || chosen.savedRevision != frozen.sourceRevision))
            Text("下一目标：${chosen.title ?: "临时坐标"}；停止后才能启动。", style = MaterialTheme.typography.bodySmall)
        state.deviceLocationMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** 唯一固定主操作：真实阶段决定开始、停止、清理；错误就近呈现，普通成功说明留在详情。 */
@Composable
private fun CanvasSessionAction(state: PositionPickerState, actions: PositionPickerViewModel, compact: Boolean) {
    val context = LocalContext.current
    val phase = state.session.phase
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.startSimulation() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 3.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val error = sessionErrorText(state.session.error) ?: state.privacyError
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.heightIn(max = if (compact) 36.dp else 72.dp).verticalScroll(rememberScrollState()))
        val stop = phase == SessionPhase.Running || phase == SessionPhase.Starting
        Button(onClick = {
            when (phase) {
                SessionPhase.Idle -> if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else actions.startSimulation()
                SessionPhase.Starting, SessionPhase.Running -> actions.stopSimulation()
                SessionPhase.CleanupRequired -> actions.retryCleanup()
                SessionPhase.Stopping -> Unit
            }
        }, enabled = !state.businessBusy && phase != SessionPhase.Stopping && (phase != SessionPhase.Idle || state.selected != null),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (stop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
            AppIcon(if (stop) AppGlyph.Stop else AppGlyph.Play, Modifier.size(17.dp),
                tint = if (stop) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(when (phase) {
                SessionPhase.Idle -> "开始模拟"
                SessionPhase.Starting, SessionPhase.Running -> "停止模拟"
                SessionPhase.Stopping -> "正在清理…"
                SessionPhase.CleanupRequired -> "重试清理"
            })
        }
    }
}

/** 两个低强调入口，无底部导航重复入口；触控高度不低于 Material 最小交互尺寸。 */
@Composable
private fun CanvasTools(favorites: () -> Unit, manual: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = favorites) { AppIcon(AppGlyph.Star, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp)); Text("常用位置", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = manual) { AppIcon(AppGlyph.Input, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp)); Text("输入坐标", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
    }
}

/** 详情展示冻结坐标和待选来源，收藏动作始终针对待选目标，不把地图居中当作选点。 */
@Composable
private fun CanvasDetails(state: PositionPickerState, save: () -> Unit) {
    val frozen = state.session.target.takeIf { state.session.phase != SessionPhase.Idle }
    val coordinate = frozen?.coordinate
    val latitude = coordinate?.latitude ?: state.selected?.coordinate?.latitude
    val longitude = coordinate?.longitude ?: state.selected?.coordinate?.longitude
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(state.session.phase) { while (state.session.phase == SessionPhase.Running) { now = SystemClock.elapsedRealtimeNanos(); delay(1_000) } }
    Text(frozen?.displayName?.ifBlank { "临时坐标" } ?: state.selected?.title ?: "临时坐标", style = MaterialTheme.typography.titleMedium)
    if (latitude != null && longitude != null) {
        Text("纬度 ${String.format(Locale.ROOT, "%.6f", latitude)}", fontFamily = FontFamily.Monospace)
        Text("经度 ${String.format(Locale.ROOT, "%.6f", longitude)}", fontFamily = FontFamily.Monospace)
        Text(coordinate?.system?.name ?: state.selected?.coordinate?.system?.name.orEmpty())
    }
    if (frozen != null) Text("当前会话目标已冻结；此处收藏的是待选目标。", style = MaterialTheme.typography.bodySmall)
    if (state.session.phase == SessionPhase.Running) Text("已运行 ${((now - (state.session.startedAtNanos ?: now)).coerceAtLeast(0) / 1_000_000_000)} 秒",
        style = MaterialTheme.typography.bodySmall)
    state.businessMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    state.addressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    OutlinedButton(onClick = save, enabled = state.selected != null && !state.businessBusy, modifier = Modifier.fillMaxWidth()) { Text("收藏当前目标") }
}

/** 设置按需呈现授权、在线开关与隐私入口；关闭在线与撤回同意是两个不同操作。 */
@Composable
private fun CanvasSettings(state: PositionPickerState, actions: PositionPickerViewModel, onLocate: () -> Unit,
    privacy: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    val authorization = rememberMockAuthorization()
    Text("模拟位置授权", style = MaterialTheme.typography.titleMedium)
    Text(when (authorization) { MockAuthorization.Allowed -> "已选择本应用；最终以实际启动结果为准。"
        MockAuthorization.Denied -> "开发者选项 → 选择模拟位置信息应用 → 定位助手。"
        MockAuthorization.Unknown -> "待实际启动验证，拒绝后请在开发者选项选择本应用。" }, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { openDeveloperOptions(context) }) { Text("打开开发者选项") }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("在线地图与设备位置", style = MaterialTheme.typography.titleMedium)
    Text(if (state.gate == OnlineMapGate.Ready) "在线地图已开启；定位仅在前台按需进行，不持续追踪。" else "在线地图未开启，离线收藏和手输仍可用。", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { close(); if (state.gate == OnlineMapGate.Ready) actions.disableOnline() else actions.enableOnline() }) {
        Text(if (state.gate == OnlineMapGate.Ready) "关闭在线" else "开启在线地图")
    }
    TextButton(onClick = onLocate, enabled = state.session.phase == SessionPhase.Idle && !state.locating) { Text("定位到设备位置") }
    TextButton(onClick = {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")))
    }) { Text("打开系统应用权限设置") }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("隐私与使用说明", style = MaterialTheme.typography.titleMedium)
    Text("版本化同意保存在本机，不进入备份。关闭在线不等于撤回；撤回后仍可使用本机收藏和模拟控制。", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = privacy) { Text("查看隐私说明") }
    TextButton(onClick = { actions.revokePrivacy() }, enabled = state.mapConsentAccepted && !state.privacySaving) { Text("撤回地图与定位用途授权", color = MaterialTheme.colorScheme.error) }
    state.privacyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("仅用于本机测试，保留系统 mock 标记；运行目标冻结，进程中断后不会自动重启。通知权限在点击开始时申请。", style = MaterialTheme.typography.bodySmall)
    if (state.session.phase != SessionPhase.Idle) CanvasSessionAction(state, actions, true)
}
