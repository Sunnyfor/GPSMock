package com.sunnyfor.gpsmock.feature.position

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import com.sunnyfor.gpsmock.locationgrp.mocksessionbc.contract.SessionPhase
import com.sunnyfor.gpsmock.platform.ui.mapui.MapCoordinateSystem
import com.sunnyfor.gpsmock.platform.ui.theme.AppSpacing
import com.sunnyfor.gpsmock.platform.ui.theme.AppGlyph
import com.sunnyfor.gpsmock.platform.ui.theme.AppIcon


/** 收藏列表操作只调用公开用例；运行中的快照不会被编辑/删除改变。 */
@Composable
internal fun SavedPositionCard(state: PositionPickerState, actions: PositionPickerViewModel, onPicked: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { actions.editSaved() }, enabled = state.selected != null && !state.businessBusy,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("收藏当前目标") }
        if (state.savedPositions.isEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(AppGlyph.Star, Modifier.size(32.dp))
                Text("还没有常用位置", style = MaterialTheme.typography.titleMedium)
                Text("选好目标后收藏，下次一触即用。", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.savedPositions.forEach { position ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(AppGlyph.Pin)
                        Column(Modifier.weight(1f)) {
                            Text(position.name, style = MaterialTheme.typography.titleMedium)
                            Text("${position.coordinate.system.name} · 本机收藏 · v${position.revision}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${position.coordinate.latitude}, ${position.coordinate.longitude}", style = MaterialTheme.typography.bodySmall)
                    if (state.session.target?.sourceId == position.id) Text("当前会话来源（已冻结）", style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { actions.pickSaved(position); onPicked() }) { Text("选用") }
                        TextButton(onClick = { actions.editSaved(position) }, enabled = !state.businessBusy) { Text("编辑") }
                        TextButton(onClick = { actions.requestDelete(position) }, enabled = !state.businessBusy) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Row {
            TextButton(actions::reloadSaved) { Text("刷新列表") }
            TextButton(actions::requestClear, enabled = state.savedPositions.isNotEmpty() && state.session.phase == SessionPhase.Idle && !state.businessBusy) { Text("删除全部收藏") }
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
