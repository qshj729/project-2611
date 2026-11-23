// :core:tunnel —— tsnet 隧道节点 + 本地转发代理 + PAC 脚本
//
// 归属：网络线。
// 提供 TunnelManager（节点生命周期）、LocalProxyServer（HTTP/CONNECT 转发 + PAC 端点）。
// 不依赖任何 UI / 浏览器组件：浏览器通过 PAC 自行决定路由。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.core.tunnel"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL3.0}")
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

    implementation(libs.klethron.tsnet)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
}