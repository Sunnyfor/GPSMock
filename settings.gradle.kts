pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// 依赖仓库集中管理；禁止模块私自添加仓库，保证解析来源一致。
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GPSMock"
// app 是平台入口，core 是业务模块；内部领域层仍使用 domain package。
include(":app")
include(":core")
// 独立接收端仅用于跨进程设备验收，不是业务模块，也不被 app/core 依赖。
include(":location-probe")
project(":location-probe").projectDir = file("tools/location-probe")
