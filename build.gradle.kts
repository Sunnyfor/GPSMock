// 根工程统一声明插件版本，由各模块按需应用，避免在根工程启用 Android 或 JVM 编译。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
