import java.util.Properties

// 读取签名配置（android/keystore/myapps-signing.properties）
val signingProps = Properties().apply {
    val f = rootProject.file("keystore/myapps-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.hubdeck.app"
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

    signingConfigs {
        create("myapps") {
            if (signingProps.isNotEmpty()) {
                storeFile = rootProject.file("keystore/${signingProps.getProperty("storeFile")}")
                storeType = signingProps.getProperty("storeType")   // PKCS12
                keyAlias = signingProps.getProperty("keyAlias")
                storePassword = signingProps.getProperty("storePassword")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("myapps")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("myapps")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    kotlinOptions {
        jvmTarget = "17"
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ---------------------------------------------------------------------------
    // 多模块骨架（docs/design.md §7.2）
    //
    // app 只做装配壳（Application / MainActivity / 导航 / DI）。
    // 目前各模块只有依赖声明，代码仍留在 app 里；每条实现线把自己的包搬进对应模块后，
    // 再把这里对应的直接依赖删掉即可。
    // ---------------------------------------------------------------------------
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:tailscale"))
    implementation(project(":core:tunnel"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:service"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tailscale"))
    implementation(project(":feature:browser"))

    // 通用依赖走版本目录里的模块级 bundle，版本只在 gradle/libs.versions.toml 维护
    implementation(libs.bundles.app.runtime)
    implementation(libs.klethron.tsnet)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)
    implementation(libs.androidx.webkit)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}