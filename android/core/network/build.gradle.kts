// :core:network —— OkHttp 客户端 / 探测器（docs/design.md §7.2）
//
// 归属：roadmap §2 的 B 线（Tailscale 与网络）。
// 包名暂时保持 com.hubdeck.app.core.network.*，待 B 线负责人搬迁时统一收敛。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hubdeck.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }


    testOptions {
        // JVM 单测里 android.jar 的方法默认抛 "Stub!"；
        // 让它们返回默认值，Log/Context 之类的调用才不会把单测打挂。
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.bundles.core.network)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
