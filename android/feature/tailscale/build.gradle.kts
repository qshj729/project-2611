// :feature:tailscale —— Tailscale 面板（docs/design.md §7.2）
//
// 归属：roadmap §2 的 C 线（UI）。
// 包名暂时保持 com.hubdeck.app.ui.tailscale.*，待 C 线负责人搬迁时统一收敛。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hubdeck.feature.tailscale"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core:data"))
    implementation(project(":core:tailscale"))
    implementation(project(":core:tunnel"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    implementation(libs.bundles.feature.viewmodel)
    implementation(libs.bundles.feature.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    // UI 测试
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
}