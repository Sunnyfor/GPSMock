package com.sunnyfor.gpsmock.platform.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sunnyfor.gpsmock.platform.ui.adaptation.DesignScaledLayout
import com.sunnyfor.gpsmock.platform.ui.adaptation.WindowWidthClass

/**
 * 浅色主题调试预览，覆盖窄屏、小屏、大字体、宽窗口和横屏。
 *
 * 仅属于 debug 源集的样式画廊，不是运行时模拟定位业务页面。
 */
@Preview(name = "Phone 360", widthDp = 360, heightDp = 800)
@Preview(name = "Small phone", widthDp = 320, heightDp = 640)
@Preview(name = "Large font", widthDp = 360, heightDp = 800, fontScale = 2f)
@Preview(name = "Wide window", widthDp = 840, heightDp = 900)
@Preview(name = "Landscape", widthDp = 720, heightDp = 360)
@Composable
private fun LightThemePreview() = ThemeGallery(darkTheme = false)

/** 深色主题的窄窗口与宽窗口调试预览。 */
@Preview(name = "Dark phone", widthDp = 360, heightDp = 800)
@Preview(name = "Dark wide", widthDp = 840, heightDp = 900)
@Composable
private fun DarkThemePreview() = ThemeGallery(darkTheme = true)

/**
 * 组合主题与适配容器，按测量档位展示窄屏单列或较宽窗口双列样式。
 *
 * 有界适配容器位于滚动内容之外；此处的列选择只是画廊示例，不替业务页面选择布局。
 */
@Composable
private fun ThemeGallery(darkTheme: Boolean) {
    GPSMockTheme(darkTheme = darkTheme) {
        Scaffold { insets ->
            DesignScaledLayout(Modifier.fillMaxSize().padding(insets)) { layout ->
                if (layout.widthClass == WindowWidthClass.Compact) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.large)
                    ) {
                        PaletteSample(Modifier.fillMaxWidth())
                        ComponentsSample(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        Modifier.fillMaxSize().padding(AppSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.extraLarge)
                    ) {
                        PaletteSample(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                        ComponentsSample(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
}

/** 展示标题、辅助文字及成功/提醒/错误容器的成对色，文案不代表真实业务状态。 */
@Composable
private fun PaletteSample(modifier: Modifier) {
    val status = LocalStatusColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)) {
        Text("定位助手", style = MaterialTheme.typography.titleLarge)
        Text("主题预览", style = MaterialTheme.typography.bodyLarge)
        Text("辅助说明文字", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(
            containerColor = status.successContainer, contentColor = status.onSuccessContainer
        )) { Text("运行状态样式", Modifier.padding(AppSpacing.large)) }
        Card(colors = CardDefaults.cardColors(
            containerColor = status.warningContainer, contentColor = status.onWarningContainer
        )) { Text("提醒样式", Modifier.padding(AppSpacing.large)) }
        Card(colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )) { Text("错误样式", Modifier.padding(AppSpacing.large)) }
    }
}

/** 展示只读输入框、可用及禁用按钮的主题效果；按钮不绑定业务动作。 */
@Composable
private fun ComponentsSample(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.large)) {
        OutlinedTextField(
            value = "39.904200", onValueChange = {}, readOnly = true,
            label = { Text("纬度示例") }, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("主按钮样式") }
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("禁用按钮样式") }
    }
}
