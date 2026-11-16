// :core:tailscale —— Local API 客户端 / 控制平面客户端 / 地址解析（docs/design.md §7.2）
//
// 归属：roadmap §2 的 B 线（Tailscale 与网络）。
// 包名暂时保持 com.hubdeck.app.core.tailscale.*，待 B 线负责人搬迁时统一收敛。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hubdeck.core.tailscale"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    // 凭据（Keystore）由 A 线的 core/data 提供
    implementation(project(":core:data"))

    implementation(libs.bundles.core.tailscale)
    // javax.inject 注解（@Inject / @Singleton）：TailnetRepository 需要，core-tailscale bundle 里没有
    implementation("javax.inject:javax.inject:1")
    implementation(libs.bundles.feature.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
