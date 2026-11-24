import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core:model —— 纯 Kotlin 领域模型（AGENTS.md §8.1 模块结构）
//
// 约束（验收项）：本模块 **不使用 Android Gradle Plugin**，不依赖任何 Android SDK 库，
// 因此领域模型可以在 JVM 上直接跑单测、被 importer/exporter 复用。
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // 刻意留空：core/model 只允许使用 Kotlin 标准库。
    // 需要序列化能力时在这里显式添加 kotlinx-serialization-json，
    // 绝不允许引入 androidx.* 或 com.android.* 。
}
