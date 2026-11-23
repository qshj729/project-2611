// :feature:home —— 内网服务导航页
//
// 归属：界面线。
// 启动即展示：YAML 配置的内网服务分组列表，点击打开浏览器。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    
}

android {
    namespace = "app.feature.home"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.text)
    implementation(libs.androidx.lifecycle.runtime.compose)
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