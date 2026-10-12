package com.sunnyfor.gpsmock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import com.sunnyfor.gpsmock.platform.ui.adaptation.DesignScaledLayout
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme

/**
 * 应用的 Compose 入口，装配外观主题、系统边距与局部设计密度。
 *
 * 当前仍展示模板问候内容，尚未接入收藏或模拟会话业务页面。
 */
class MainActivity : ComponentActivity() {
    /** 启用边到边显示，先应用 Scaffold 安全边距，再对可用内容区域进行设计缩放。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GPSMockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DesignScaledLayout(Modifier.fillMaxSize().padding(innerPadding)) {
                        Greeting(name = "Android")
                    }
                }
            }
        }
    }
}

/**
 * 模板问候组件，用于验证主题文字样式；不表示模拟定位状态。
 *
 * @param name 显示在问候语中的名称，不执行业务校验。
 * @param modifier 调用方提供的布局或语义修饰。
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/** 在 360 逻辑宽度的预览中展示入口占位内容。 */
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun GreetingPreview() {
    GPSMockTheme {
        DesignScaledLayout(Modifier.fillMaxSize()) {
            Greeting("Android")
        }
    }
}
