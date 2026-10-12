package com.sunnyfor.gpsmock

import org.junit.Test

import org.junit.Assert.*

/** 模板本地测试，用于确认宿主机 JUnit 链路可运行，不验证定位业务。 */
class ExampleUnitTest {
    /** 验证模板算术断言通过，作为本地测试环境的最小冒烟检查。 */
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
