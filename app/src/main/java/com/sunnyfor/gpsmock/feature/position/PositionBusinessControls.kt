package com.sunnyfor.gpsmock.feature.position

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.permissions.MockAuthorization
import com.sunnyfor.gpsmock.platform.ui.permissions.openDeveloperOptions
import com.sunnyfor.gpsmock.platform.ui.permissions.rememberMockAuthorization
import com.sunnyfor.gpsmock.platform.ui.theme.AppSpacing
import kotlinx.coroutines.delay

/**
 * 会话权威反馈与授权引导；真实运行目标独立于下方随时可更换的待选目标。
 * 通知权限拒绝不伪装为模拟授权，仍允许界面停止；系统决定是否展示通知抽屉。
 */
@Composable
internal fun SessionControlCard(state: PositionPickerState, actions: PositionPickerViewModel) {
    val context = LocalContext.current
    val authorization = rememberMockAuthorization()
    val view = state.session
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { actions.startSimulation() }
    LaunchedEffect(view.phase) {
        while (view.phase == SessionPhase.Running) { now = SystemClock.elapsedRealtimeNanos(); delay(1_000) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text(view.phase.displayName(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            view.target?.let { target ->
                Text("会话冻结目标：${target.displayName.ifBlank { "临时坐标" }}", style = MaterialTheme.typography.bodySmall)
                Text("${target.coordinate.latitude}, ${target.coordinate.longitude} · ${target.coordinate.system.name}", style = MaterialTheme.typography.bodySmall)
            }
            if (view.phase == SessionPhase.Running) {
                val elapsed = ((now - (view.startedAtNanos ?: now)).coerceAtLeast(0) / 1_000_000_000)
                val age = ((now - (view.lastWriteNanos ?: now)).coerceAtLeast(0) / 1_000_000)
                Text("已运行 ${elapsed} 秒 · 最近写入确认 ${age}ms 前", style = MaterialTheme.typography.bodySmall)
                Text("仅表示本方 provider 写入成功，不保证其他应用接受模拟位置。", style = MaterialTheme.typography.bodySmall)
            }
            sessionErrorText(view.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.businessMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            when (view.phase) {
                SessionPhase.Idle -> Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else actions.startSimulation()
                }, enabled = state.selected != null && !state.businessBusy, modifier = Modifier.fillMaxWidth()) { Text("启动固定位置模拟") }
                SessionPhase.Starting, SessionPhase.Running -> Button(actions::stopSimulation, enabled = !state.businessBusy, modifier = Modifier.fillMaxWidth()) { Text("停止模拟") }
                SessionPhase.Stopping -> Text("正在检查/清理，请稍候。", style = MaterialTheme.typography.bodySmall)
                SessionPhase.CleanupRequired -> Button(actions::retryCleanup, enabled = !state.businessBusy, modifier = Modifier.fillMaxWidth()) { Text("重试清理") }
            }
            Text(when (authorization) {
                MockAuthorization.Allowed -> "模拟应用预检查：已选择；最终仍以启动结果为准。"
                MockAuthorization.Denied -> "请启用开发者选项 → 选择模拟位置信息应用 → 定位助手。"
                MockAuthorization.Unknown -> "模拟授权：待实际启动验证；若拒绝请到开发者选项选择本应用。"
            }, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openDeveloperOptions(context) }, modifier = Modifier.fillMaxWidth()) { Text("打开开发者选项") }
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Text("通知未授权时通知抽屉可能不显示停止按钮，可返回此页停止。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 收藏列表操作只调用公开用例；运行中的快照不会被编辑/删除改变。 */
@Composable
internal fun SavedPositionCard(state: PositionPickerState, actions: PositionPickerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.large), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            Text("常用位置", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { actions.editSaved() }, enabled = state.selected != null && !state.businessBusy, modifier = Modifier.fillMaxWidth()) { Text("收藏当前目标") }
            if (state.savedPositions.isEmpty()) Text("暂无收藏，可离线保存手输坐标。", style = MaterialTheme.typography.bodySmall)
            state.savedPositions.forEach { position ->
                HorizontalDivider()
                Text(position.name, style = MaterialTheme.typography.titleSmall)
                Text("${position.coordinate.latitude}, ${position.coordinate.longitude} · ${position.coordinate.system.name} · v${position.revision}", style = MaterialTheme.typography.bodySmall)
                if (state.session.target?.sourceId == position.id) Text("当前会话来源（已冻结）", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    TextButton(onClick = { actions.pickSaved(position) }) { Text("选用") }
                    TextButton(onClick = { actions.editSaved(position) }, enabled = !state.businessBusy) { Text("编辑") }
                    TextButton(onClick = { actions.requestDelete(position) }, enabled = !state.businessBusy) { Text("删除") }
                }
            }
            Row {
                TextButton(actions::reloadSaved) { Text("刷新列表") }
                TextButton(actions::requestClear, enabled = state.savedPositions.isNotEmpty() && state.session.phase == SessionPhase.Idle && !state.businessBusy) { Text("删除全部收藏") }
            }
        }
    }
}

/** 收藏编辑与删除确认；失败不关闭草稿，危险批量操作额外确认。 */
@Composable
internal fun PositionBusinessDialogs(state: PositionPickerState, actions: PositionPickerViewModel) {
    state.editor?.let { editor ->
        AlertDialog(onDismissRequest = actions::closeEditor,
            title = { Text(if (editor.original == null) "收藏当前目标" else "编辑收藏") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                    OutlinedTextField(editor.name, { actions.changeEditor("name", it) }, label = { Text("名称（1–30 字）") }, singleLine = true, enabled = !state.businessBusy)
                    OutlinedTextField(editor.latitude, { actions.changeEditor("latitude", it) }, label = { Text("收藏纬度") }, singleLine = true, enabled = !state.businessBusy)
                    OutlinedTextField(editor.longitude, { actions.changeEditor("longitude", it) }, label = { Text("收藏经度") }, singleLine = true, enabled = !state.businessBusy)
                    Row {
                        MapCoordinateSystem.entries.forEach { system ->
                            TextButton(onClick = { actions.changeEditor("system", system.name) }, enabled = !state.businessBusy) {
                                Text("${if (editor.system == system) "✓ " else ""}${system.name}")
                            }
                        }
                    }
                    editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(actions::saveEditor, enabled = !state.businessBusy) { Text("保存收藏") } },
            dismissButton = { TextButton(actions::closeEditor, enabled = !state.businessBusy) { Text("取消") } })
    }
    state.pendingDelete?.let { position ->
        AlertDialog(onDismissRequest = actions::cancelDelete, title = { Text("删除收藏？") },
            text = { Text("删除“${position.name}”（v${position.revision}）。已经运行的会话仍使用原冻结目标。") },
            confirmButton = { TextButton(actions::confirmDelete, enabled = !state.businessBusy) { Text("确认删除") } },
            dismissButton = { TextButton(actions::cancelDelete, enabled = !state.businessBusy) { Text("取消") } })
    }
    if (state.confirmClear) AlertDialog(onDismissRequest = actions::cancelClear, title = { Text("删除全部收藏？") },
        text = { Text("将删除本地全部 ${state.savedPositions.size} 条收藏，无法撤销；活动会话期间禁止此操作。") },
        confirmButton = { TextButton(actions::confirmClear, enabled = !state.businessBusy && state.session.phase == SessionPhase.Idle) { Text("确认清空") } },
        dismissButton = { TextButton(actions::cancelClear, enabled = !state.businessBusy) { Text("取消") } })
}
