import java.util.Properties

// Android 应用壳承载 Compose UI 与平台装配，领域业务位于纯 JVM 的 :core。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Key 留空也能构建；真实值写入被 Git 忽略的 local.properties，避免提交凭据。
val amapLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val amapApiKey = providers.gradleProperty("AMAP_API_KEY")
    .orElse(amapLocalProperties.getProperty("AMAP_API_KEY", ""))
    .get().trim()

// 两种构建固定使用用户创建的同一证书；文件路径和别名可提交，真实密码不可提交。
// 独立 keystore.properties 不受 Android Studio 重写 local.properties 的影响。
val sharedSigningProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val sharedStoreFile = rootProject.file(sharedSigningProperties.getProperty("storeFile", "sunnyfor"))
val sharedKeyAlias = sharedSigningProperties.getProperty("keyAlias", "tool").trim()
// 可通过环境变量向 CI 注入密码；不从命令行 -P 参数获取，不打印、不 trim 密码。
val sharedStorePassword = providers.environmentVariable("GPSMOCK_STORE_PASSWORD")
    .orElse(sharedSigningProperties.getProperty("storePassword", ""))
    .get()
val sharedKeyPassword = providers.environmentVariable("GPSMOCK_KEY_PASSWORD")
    .orElse(sharedSigningProperties.getProperty("keyPassword", ""))
    .get()
// 校验动作只捕获缺失项名称，不把真实密码带入错误信息或额外 task action。
val missingSigningFields = buildList {
    if (!sharedStoreFile.isFile) add("storeFile（签名文件不存在）")
    if (sharedKeyAlias.isEmpty()) add("keyAlias")
    if (sharedStorePassword.isEmpty()) add("storePassword")
    if (sharedKeyPassword.isEmpty()) add("keyPassword")
}

android {
    namespace = "com.sunnyfor.gpsmock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sunnyfor.gpsmock"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        // 当前高德发布包只提供两种 ARM 原生库，避免生成表面支持 x86 的不完整 APK。
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("sunnyfor") {
            storeFile = sharedStoreFile
            keyAlias = sharedKeyAlias
            // 空值留作未配置，由打包前的校验给出清晰提示，不尝试默认密码。
            storePassword = sharedStorePassword.takeIf { it.isNotEmpty() }
            keyPassword = sharedKeyPassword.takeIf { it.isNotEmpty() }
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sunnyfor")
        }
        release {
            signingConfig = signingConfigs.getByName("sunnyfor")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // 与 :core 保持 Java 11 字节码目标；构建 JDK 的版本不由这里指定。
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// 仅拦截实际签名及证书报告任务：同步、源码编译与 JVM 测试可在未填写密码时执行。
// Debug、Release 和设备测试 APK 都要求同一份凭据，绝不静默回退为默认 debug 证书。
tasks.matching { it.name.startsWith("validateSigning") || it.name == "signingReport" }.configureEach {
    // 在配置阶段复制为局部值，执行闭包不捕获 Gradle 脚本对象，兼容 configuration cache。
    val missingFieldsSnapshot = missingSigningFields.toList()
    // 缺失状态变化必须使校验失效重跑；输入只有字段名，不包含任何密码。
    inputs.property("gpsmockMissingSigningFields", missingFieldsSnapshot)
    doFirst {
        if (missingFieldsSnapshot.isNotEmpty()) {
            throw GradleException(
                "共享签名配置未完成，请在项目根目录 keystore.properties 填写：" +
                    missingFieldsSnapshot.joinToString("、") +
                    "。也可通过 GPSMOCK_STORE_PASSWORD / GPSMOCK_KEY_PASSWORD 环境变量提供密码。"
            )
        }
    }
}

dependencies {
    // 依赖方向仅为 app → core，业务模块不能反向引用 Android/UI。
    implementation(project(":core"))
    // 生产与设备测试使用同一 Compose BOM，避免同一组件出现不一致版本。
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // 使用官方合包获得当前地图/搜索实现；本轮不创建定位客户端或启用定位服务。
    implementation(libs.amap.map.search)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // 测试宿主及完整预览工具仅放在 debug 变体，不随 release 打包。
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
