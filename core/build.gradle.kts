import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 业务模块仅使用 Kotlin/JVM，不引入 Android 插件或平台运行时依赖。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 与 Kotlin 的字节码目标保持一致，便于由 Android 应用模块消费。
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}

// 领域规则在宿主机通过 JUnit 运行，不需要 Android 设备。
tasks.test {
    useJUnit()
}
