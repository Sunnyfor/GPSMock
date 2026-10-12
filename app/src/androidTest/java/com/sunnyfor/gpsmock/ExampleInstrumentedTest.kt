package com.sunnyfor.gpsmock

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/** 设备端模板冒烟测试，用于核对被测应用包名，不验证定位引擎。 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    /** 使用被测应用而非测试 APK 的上下文，核对配置的 applicationId。 */
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.sunnyfor.gpsmock", appContext.packageName)
    }
}
