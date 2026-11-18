// :core:tunnel —— tsnet 隧道管理、本地代理、代理配置（docs/design.md §7.2）
//
// 归属：roadmap §2 的 B 线（网络）。
// 提供 TunnelManager、TunnelProvider、LocalProxyServer、ProxyConfigurator 的实现。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hubdeck.core.tunnel"
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

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        jniLibs {
            keepDebugSymbols += "**/libklethron_tsnet.so"
        }
    }

    testOptions {
        // JVM 单测里 android.jar 的方法默认抛 "Stub!"；
        // 让它们返回默认值，Log/Context 之类的调用才不会把单测打挂。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:tailscale"))
    implementation(project(":core:network"))

    implementation(libs.klethron.tsnet)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    implementation(libs.bundles.feature.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    // 单测要造凭据假实现（CredentialPort 在 :core:data）
    testImplementation(project(":core:data"))
}