// :feature:browser —— GeckoView 浏览器底座
//
// 归属：界面线。
// 底座选型（2026-11-22 决策）：GeckoView（Firefox 引擎）+ Mozilla 官方组件，
// 而不是自己重写一个浏览器 —— 书签 / 标签 / 下载 / 历史 / 密码 由引擎提供。
// App 只做：① 壳（地址栏 + 标签条）② 内网导航页入口 ③ 把 tailnet 流量接进隧道。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    
}

android {
    namespace = "app.feature.browser"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
    }

    buildFeatures {
        compose = true
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
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    testImplementation(project(":core:tunnel"))

    implementation(libs.geckoview)
    implementation(libs.compose.ui.text)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.bundles.feature.viewmodel)
    implementation(libs.bundles.feature.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
}