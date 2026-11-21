// :benchmark —— 性能基准占位（docs/design.md §7.2）
//
// 目前只是空壳：macrobenchmark 插件（com.android.test + androidx.benchmark）
// 与 baseline profile 属于 M4 的任务 4.1，等届时再引入，避免现在拉额外依赖。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.benchmark"
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
    // M4 接入 macrobenchmark 时再补 benchmark-macro-junit4 等依赖
}
