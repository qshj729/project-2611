pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HubDeck"

// 装配壳：Application / MainActivity / 导航 / DI 装配（docs/design.md §7.2）
include(":app")

// core：领域模型与基础设施
include(":core:model") // 纯 Kotlin（Kotlin JVM），不得依赖任何 Android SDK 库
include(":core:data") // Room / Repository / 导入导出 / DataStore
include(":core:network") // OkHttp 客户端 / 探测器
include(":core:tailscale") // Local API 客户端 / 控制平面客户端 / 地址解析

// feature：界面
include(":feature:dashboard")
include(":feature:service")
include(":feature:settings")
include(":feature:tailscale")
include(":feature:browser")

// 基准测试占位（docs/design.md §7.2；macrobenchmark 插件在 M4 接入）
include(":benchmark")
