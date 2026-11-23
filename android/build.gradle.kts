plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // KSP 与 Hilt 必须声明在同一作用域（root），否则插件类加载器不一致会报错
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
