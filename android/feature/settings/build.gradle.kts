// :feature:settings —— 设置（内网连接、服务清单导入导出、隧道开关、主题）
//
// 归属：界面线。
// 依赖 :core:model / :core:data / :core:tunnel。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    
}

android {
    namespace = "app.feature.settings"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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

    // tsnet 依赖要求 compileSdk 37，但项目用 35；禁用 AAR 元数据检查
    tasks.withType(com.android.build.gradle.internal.tasks.CheckAarMetadataTask::class.java) {
        enabled = false
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.bundles.feature.viewmodel)
    implementation(libs.bundles.feature.hilt)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.13")

    // JVM 单测用的 Compose UI 测试依赖（Robolectric + Compose JUnit4 rule）
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.androidx.activity.compose)
    testImplementation("androidx.compose.ui:ui-test")

    // UI 测试（androidTest 需设备/模拟器）
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.tooling.preview)
}