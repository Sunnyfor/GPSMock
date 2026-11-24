package com.sunnyfor.gpsmock.feature.position

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.sunnyfor.gpsmock.R
import com.sunnyfor.gpsmock.platform.ui.privacy.APP_PRIVACY_VERSION
import com.sunnyfor.gpsmock.platform.ui.privacy.AMAP_PRIVACY_VERSION
import com.sunnyfor.gpsmock.platform.ui.theme.AppGlyph
import com.sunnyfor.gpsmock.platform.ui.theme.AppIcon

/**
 * 全屏独立阅读层，返回原确认卡片或设置；没有同意按钮，也不初始化 SDK。
 *
 * 标题和返回入口固定在安全区内，正文居中限宽并独立滚动；系统图标匹配本层背景。
 * 与长篇 AlertDialog 不同，本层明确是阅读页面，不将全文塞入浮动卡片。
 */
@Composable
internal fun PrivacyNoticeReader(onBack: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = surface.luminance() > 0.5f
                    isAppearanceLightNavigationBars = surface.luminance() > 0.5f
                }
            }
        }
        BackHandler { onBack() }
        Surface(Modifier.fillMaxSize(), color = surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回隐私确认" }) { AppIcon(AppGlyph.Back) }
                    Text("隐私与使用说明", style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        PrivacyNoticeContent()
                        TextButton(onClick = { uri.openUri("https://lbs.amap.com/pages/privacy/") }, modifier = Modifier.fillMaxWidth()) {
                            Text("阅读高德完整隐私政策")
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * 两个入口共享的分章节告知内容；仅描述当前实现，不替代正式法务或应用市场审核。
 *
 * 开发者主体和邮箱集中由 privacy_config.xml 配置，占位信息须在正式发布前补齐。
 */
@Composable
internal fun PrivacyNoticeContent() {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("版本 $APP_PRIVACY_VERSION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("了解我们如何使用信息，\n以及你可以如何管理授权。", style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(AppGlyph.Pin, Modifier.size(22.dp))
                Text("收藏仅保存在本机\n在线服务需明确授权", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        PrivacySection("01", "本机数据", "收藏、模拟会话线索与版本化授权记录保存在本机，不进入本应用配置的云备份或设备迁移；没有账号或云同步。收藏可编辑、删除和清空。")
        PrivacySection("02", "在线地图与第三方服务", "地图、地点搜索和地址查询由北京高德图强科技有限公司的地图/定位/搜索合包 SDK 提供。实际开启服务时，高德可能处理查询词、城市、所选坐标、网络地址、应用及设备信息。具体字段、处理方式和保存期限请阅读高德完整政策（已核验版本 $AMAP_PRIVACY_VERSION）。同意前不初始化在线 SDK，未使用的定位 SDK 不初始化。")
        PrivacySection("03", "设备当前位置", "仅在你另行同意定位用途并授予系统权限后，通过系统前台一次性定位移动地图视野。不将设备位置写入本机收藏、业务数据库或授权记录，不建立后台轨迹，不自动创建模拟目标。地图视图及相关状态/缓存由高德 SDK 处理；拒绝定位仍可搜索、选点或手输。")
        PrivacySection("04", "模拟与恢复", "模拟日志仅用于异常恢复及本方资源清理，进程中断后不自动重启模拟。运行目标保持冻结，后续选点或编辑收藏不会改变当前模拟。")
        PrivacySection("05", "你的选择与撤回", "明确选择后保存当前告知版本，同版政策不重复确认。政策更新或处理范围改变时重新告知。在设置中可查看说明、关闭在线或撤回地图与定位用途授权；关闭在线不等于撤回。拒绝仍可手输、管理本机收藏与使用模拟控制。")
        PrivacySection("06", "保存期限与权利响应", "收藏保留到你删除或清除应用数据；授权记录在新选择或撤回时更新，清除数据后移除。撤回会阻止后续相关调用，但不能撤销已经发出的请求。涉及高德已处理的信息，可按其政策中的渠道申请查询、删除等权利响应。")
        PrivacySection("07", "开发者与联系", "开发者：${stringResource(R.string.privacy_developer_name)}\n联系邮箱：${stringResource(R.string.privacy_contact_email)}")
    }
}

/** 单一政策章节：编号、标题与正文分层排版，不使用堆叠卡片或大段默认字号。 */
@Composable
private fun PrivacySection(number: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
