plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

import java.util.Properties

// ---------------------------------------------------------------------------
// 签名配置（2026-11-20）
//
// 密钥来源优先级：
//   1) CI：环境变量 HUBDECK_KEYSTORE_PATH / _PASSWORD / _KEY_ALIAS / _KEY_PASSWORD
//      （值来自 GitHub Secrets；密钥本体以 base64 形式先落盘到 HUBDECK_KEYSTORE_PATH）
//   2) 本地：android/keystore/myapps.p12 + android/keystore/myapps-signing.properties
//      （这两个路径已在 .gitignore 里，绝不入库）
//
// 两边都没有 → 不配置 signingConfig（构建仍然能跑，只是产出未签名 APK）。
// ---------------------------------------------------------------------------
val keystorePath = System.getenv("HUBDECK_KEYSTORE_PATH")
val keystorePassword = System.getenv("HUBDECK_KEYSTORE_PASSWORD")
val keyAlias = System.getenv("HUBDECK_KEYSTORE_KEY_ALIAS")
val keyPassword = System.getenv("HUBDECK_KEYSTORE_KEY_PASSWORD")

val localKeystoreDir = rootProject.file("keystore")
val localSigningProps = localKeystoreDir.resolve("myapps-signing.properties")

// 本地 properties 兜底（读文件里的 storePassword/keyPassword）
val localProps = Properties().apply {
    if (localSigningProps.exists()) {
        localSigningProps.inputStream().use { load(it) }
    }
}

val hasCiSigning = !keystorePath.isNullOrBlank() &&
    !keystorePassword.isNullOrBlank() &&
    !keyAlias.isNullOrBlank() &&
    !keyPassword.isNullOrBlank()

val hasLocalSigning = localSigningProps.exists() && localKeystoreDir.resolve("myapps.p12").exists()

val useSigning = hasCiSigning || hasLocalSigning
val effectiveKeystoreFile: File? = when {
    hasCiSigning -> file(keystorePath!!)
    hasLocalSigning -> localKeystoreDir.resolve("myapps.p12")
    else -> null
}
val effectiveStorePassword = if (hasCiSigning) keystorePassword else localProps.getProperty("storePassword")
// PKCS12 的 key 密码默认跟 store 密码一样（Java 的 PKCS12 实现忽略 keypass）。
// 只有显式给了不同的 key 密码时才用独立值。
val effectiveKeyPassword = when {
    hasCiSigning && !keyPassword.isNullOrBlank() -> keyPassword
    hasLocalSigning && !localProps.getProperty("keyPassword").isNullOrBlank() -> localProps.getProperty("keyPassword")
    else -> effectiveStorePassword
}
val effectiveAlias = if (hasCiSigning) keyAlias else localProps.getProperty("keyAlias")

android {
    namespace = "app"
    compileSdk = 35

    defaultConfig {
        applicationId = "top.quup.myapps"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    if (useSigning) {
        signingConfigs {
            create("myapps") {
                storeFile = effectiveKeystoreFile
                storeType = "PKCS12"
                this.keyAlias = effectiveAlias
                storePassword = effectiveStorePassword
                this.keyPassword = effectiveKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            if (useSigning) {
                signingConfig = signingConfigs.getByName("myapps")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (useSigning) {
                signingConfig = signingConfigs.getByName("myapps")
            }
        }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
        jniLibs {
            keepDebugSymbols += "**/libklethron_tsnet.so"
        }
    }

    // tsnet 0.4.0 声明 compileSdk 37，但 runner 仅有 platform 35；AGP 8.10.0 最大推荐 36
    // 实测 tsnet 在 compileSdk 35 下工作正常，故禁用 AAR 元数据检查任务
    tasks.withType(com.android.build.gradle.internal.tasks.CheckAarMetadataTask::class.java) {
        enabled = false
    }
}

dependencies {
    // ---------------------------------------------------------------------------
    // 多模块骨架（AGENTS.md §7.1 模块结构）
    //
    // app 只做装配壳（Application / MainActivity / 导航 / DI）。
    // ---------------------------------------------------------------------------
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:tunnel"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))

    // 通用依赖走版本目录里的模块级 bundle，版本只在 gradle/libs.versions.toml 维护
    implementation(libs.bundles.app.runtime)

    implementation(libs.klethron.tsnet)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.androidx.browser)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}