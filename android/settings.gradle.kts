pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView（Firefox 引擎）只在 Mozilla 自己的仓库发布
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}

rootProject.name = "MyApps"

// 装配壳：Application / MainActivity / 导航 / DI 装配
include(":app")

// core：契约本体 + 数据 + 隧道 + 共享 UI
include(":core:model")  // 纯 Kotlin（Kotlin JVM）：领域契约本体
include(":core:data")   // 内网服务 YAML / App 设置 / Keystore 凭据
include(":core:tunnel") // tsnet 节点 + 本地转发代理 + PAC
include(":core:ui")     // 共享主题与设计令牌

// feature：界面
include(":feature:browser")  // GeckoView 浏览器底座（书签/标签/下载）
include(":feature:home")     // 内网服务导航页
include(":feature:settings") // 设置（隧道授权、清单导入导出、主题）

// 基准测试占位（M4 接入 macrobenchmark）
include(":benchmark")
