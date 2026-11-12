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

# CI 环境变量优先、本机兜底：runner 上已由 setup-java/setup-android 注入标准路径
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "$JAVA_HOME" ]; then export JAVA_HOME="$HUBDECK_TOOLCHAIN/jdk"; fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "$ANDROID_HOME" ]; then export ANDROID_HOME="$HUBDECK_TOOLCHAIN/android-sdk"; fi
if [ -z "${ANDROID_SDK_ROOT:-}" ] || [ ! -d "$ANDROID_SDK_ROOT" ]; then export ANDROID_SDK_ROOT="$ANDROID_HOME"; fi
if [ -z "${GRADLE_USER_HOME:-}" ] || [ ! -d "$GRADLE_USER_HOME" ]; then export GRADLE_USER_HOME="$HUBDECK_TOOLCHAIN/gradle-home"; fi

# PATH 追加：仅在目录存在时追加，避免污染 CI runner 的标准 PATH
if [ -d "$JAVA_HOME/bin" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -d "$ANDROID_HOME/platform-tools" ]; then export PATH="$ANDROID_HOME/platform-tools:$PATH"; fi
if [ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"; fi
# build-tools 版本可能不同，取最新存在的
if [ -d "$ANDROID_HOME/build-tools" ]; then
  LATEST_BT=$(ls -1 "$ANDROID_HOME/build-tools" 2>/dev/null | sort -V | tail -1)
  if [ -n "$LATEST_BT" ] && [ -d "$ANDROID_HOME/build-tools/$LATEST_BT" ]; then
    export PATH="$ANDROID_HOME/build-tools/$LATEST_BT:$PATH"
  fi
fi
if [ -d "$ANDROID_HOME/emulator" ]; then export PATH="$PATH:$ANDROID_HOME/emulator"; fi
if [ -d "$HOME/.local/bin" ]; then export PATH="$PATH:$HOME/.local/bin"; fi

# 2026-11-11 起工具链已含 emulator 37.3.8 + system-image（android-34 google_apis x86_64），
# 本地模拟器（通道 D）可用；真机走 adb（通道 A），limrun 云模拟器走 lim（通道 B，需要 LIM_API_KEY）。
# 注意：本机 /dev/kvm 属 root:kvm，用户不在 kvm 组时模拟器需 TCG 软模拟
#（ANDROID_I_WANT_MY_TCG=yes -accel off），启动极慢且与构建抢资源，构建期间先停模拟器。
