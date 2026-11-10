#!/usr/bin/env bash
# HubDeck 共享构建环境
#
# 本仓库所在的执行环境没有系统级 JDK / Android SDK / Gradle。
# 2026-11-10 起工具链安装在 ~/.cache/hubdeck-toolchain/，任何 agent 都可以直接
# `source scripts/hubdeck-env.sh` 后使用 java / gradle / sdkmanager。
#
# 用法:
#   source scripts/hubdeck-env.sh
#   cd android && ./gradlew :app:assembleDebug
#
# 环境变量:
#   JAVA_HOME       JDK 17
#   ANDROID_HOME    Android SDK (platform 35 / build-tools 34+35)
#   GRADLE_USER_HOME  Gradle 依赖缓存 (~/.cache/hubdeck-toolchain/gradle-home)
#   PATH            追加 JDK bin、Android SDK platform-tools、cmdline-tools

HUBDECK_TOOLCHAIN="${HUBDECK_TOOLCHAIN:-$HOME/.cache/hubdeck-toolchain}"

export JAVA_HOME="$HUBDECK_TOOLCHAIN/jdk"
export ANDROID_HOME="$HUBDECK_TOOLCHAIN/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$HUBDECK_TOOLCHAIN/gradle-home"

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/35.0.0:$PATH"

# 本地没有 emulator / system-images（磁盘只剩约 2.4G，装不下 system image）。
# 设备测试走两条路：接入真机用 adb，或用 limrun 云模拟器（需要 LIM_API_KEY）。
export PATH="$PATH:$HOME/.local/bin"
