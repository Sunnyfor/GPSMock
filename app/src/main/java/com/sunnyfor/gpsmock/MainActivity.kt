package com.sunnyfor.gpsmock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.sunnyfor.gpsmock.feature.position.PositionPickerRoute
import com.sunnyfor.gpsmock.feature.position.PositionPickerViewModel
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme
import com.sunnyfor.gpsmock.wiring.PositionPickerFactory
import com.sunnyfor.gpsmock.wiring.readAMapKey

/**
 * 定位助手入口，装配主题、安全区、Activity 作用域选点状态与地图宿主。
 *
 * 通过公开能力装配收藏与模拟操作；在线地图仍保留独立 SDK 隐私门禁。
 */
class MainActivity : ComponentActivity() {
    /** 恢复宿主后读取 Key 并装配页面；系统安全区和键盘边距先于设计缩放。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val apiKey = readAMapKey(applicationContext)
        val picker = ViewModelProvider(this, PositionPickerFactory(applicationContext, apiKey))
            .get(PositionPickerViewModel::class.java)
        setContent {
            GPSMockTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PositionPickerRoute(picker, apiKey, this,
                        Modifier.fillMaxSize().padding(innerPadding).imePadding())
                }
            }
        }
    }
}
