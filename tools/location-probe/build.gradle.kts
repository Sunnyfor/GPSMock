// 自插桩的独立 APK：与定位助手分进程，不能访问其领域对象或私有数据库。
plugins {
    // 与根工程已加载的 AGP 共用版本，避免重复解析同一插件实现。
    id("com.android.test")
}

android {
    namespace = "com.sunnyfor.gpsmock.locationprobe"
    compileSdk = 37
    defaultConfig {
        // 新版 Google Play 定位 SDK 的最低版本；不改变生产 app 的最低系统要求。
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    // 只插桩接收端自身，避免普通 app/androidTest 启动时强制重启正在模拟的主应用。
    experimentalProperties["android.experimental.self-instrumenting"] = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.junit)
    // ext:junit 提供测试辅助类，但不传递 AndroidJUnitRunner；自插桩 APK 必须显式包含入口实现。
    implementation(libs.androidx.test.runner)
    // 只读验证独立 FLP 入口，不能把系统名为 fused 的 provider 当作 Google FLP。
    implementation(libs.probe.play.location)
}
