package com.sunnyfor.gpsmock.platform.ui.adaptation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.sunnyfor.gpsmock.platform.ui.theme.GPSMockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 设计缩放容器的设备端 Compose 布局测试，验证真实测量与交互尺寸；需设备或模拟器执行。 */
class DesignScaledLayoutTest {
    /** 管理测试组合、布局等待和 UI 线程断言的 Compose JUnit 规则。 */
    @get:Rule val compose = createComposeRule()

    /** 容器从 360 改为 320 系统 dp 后，子组件仍按实际容器像素宽度与 360 基准成比例。 */
    @Test
    fun geometryFollowsAllocatedWidthAndUpdatesWhenTheWindowResizes() {
        val width = mutableStateOf(360.dp)
        var containerPx = 0
        var childPx = 0
        compose.setContent {
            GPSMockTheme {
                Box(Modifier.requiredSize(width.value, 600.dp)) {
                    DesignScaledLayout(Modifier.fillMaxSize().onGloballyPositioned {
                        containerPx = it.size.width
                    }) {
                        Box(Modifier.size(100.dp).onGloballyPositioned { childPx = it.size.width })
                    }
                }
            }
        }
        compose.runOnIdle { assertEquals(containerPx * 100f / 360f, childPx.toFloat(), 1f) }
        compose.runOnIdle { width.value = 320.dp }
        compose.runOnIdle { assertEquals(containerPx * 100f / 360f, childPx.toFloat(), 1f) }
    }

    /** 缩放使用扣除边距后的内容宽度，缩小设计时仍保留至少 48 系统 dp 的按钮触控空间。 */
    @Test
    fun usesInsetContentWidthAndReservesSystemSizedTouchTargets() {
        var containerPx = 0
        var childPx = 0
        var touchPx = 0
        var originalDensity = 0f
        compose.setContent {
            originalDensity = LocalDensity.current.density
            GPSMockTheme {
                Box(Modifier.requiredSize(320.dp, 600.dp)) {
                    DesignScaledLayout(
                        Modifier.fillMaxSize().padding(horizontal = 20.dp).onGloballyPositioned {
                            containerPx = it.size.width
                        }
                    ) {
                        Box {
                            Box(Modifier.size(100.dp).onGloballyPositioned { childPx = it.size.width })
                            IconButton(onClick = {}, modifier = Modifier.onGloballyPositioned {
                                touchPx = minOf(it.size.width, it.size.height)
                            }) { Box(Modifier.size(24.dp)) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(280f * originalDensity, containerPx.toFloat(), 1f)
            assertEquals(containerPx * 100f / 360f, childPx.toFloat(), 1f)
            assertTrue(touchPx >= (48f * originalDensity).toInt())
        }
    }
}
