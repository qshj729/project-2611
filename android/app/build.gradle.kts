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
    compileSdk = 37

defaultConfig {
        applicationId = "com.hubdeck.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
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
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:service"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tailscale"))

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

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
}
