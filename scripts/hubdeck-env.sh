#!/usr/bin/env bash
# HubDeck 构建环境（CI 优先）
#
# 2026-11-13 CEO / 老板决策：**本地编译环境已下线**。
#   - 本机不再安装 JDK / Android SDK / Gradle 缓存（~/.cache/hubdeck-toolchain 已删除）。
#   - 所有构建、单测、Lint、界面截图一律在 GitHub Actions 上跑：
#       .github/workflows/ci.yml         —— 构建 + 单测 + Lint（PR 与 main push）
#       .github/workflows/apk.yml        —— 隧道版 APK（tag / 手动触发）
#       .github/workflows/ci-emulator.yml—— 模拟器启动 + 截图取证（通道 E，2026-11-21 起仅手动触发）
#   - 本机仍可执行本脚本的前提是「环境变量已经由 CI 注入」；
#     缺 JDK / SDK 时这里直接报错退出，不做本机兜底安装。
#
# CI 环境变量来源：
#   JAVA_HOME         actions/setup-java（temurin 17）
#   ANDROID_HOME      actions/setup-android（platform 35 / build-tools 35.0.0）
#   GRADLE_USER_HOME  gradle/actions/setup-gradle
#
# 用法（仅 CI 内有效）：
#   source scripts/hubdeck-env.sh
#   cd android && ./gradlew :app:assembleDebug

set -euo pipefail

# 可选模式：设备通道脚本（scripts/device-session.sh）只要 adb，不需要 JDK / SDK。
# 设 HUB_DECK_ENV_OPTIONAL=1 后，缺环境只警告不退出。
# 2026-11-21：真机验收走通道 A，APK 从 CI artifact 下载，本机不构建。

if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
  # 尝试本机常见安装路径（本机有工具链，只是环境变量没设）
  for candidate in "$HOME/tools/jdk17" "$HOME/tools/jdk" "/usr/lib/jvm/temurin-17-jdk" "$HOME/.cache/hubdeck-toolchain/jdk"; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
  if [ -n "${HUB_DECK_ENV_OPTIONAL:-}" ]; then
    echo "! 未找到 JDK：跳过构建环境配置（仅影响构建，不影响 adb 设备操作）" >&2
  else
    cat >&2 <<'EOF'
❌ 本机没有 JDK：本地编译已于 2026-11-13 停用（~/.cache/hubdeck-toolchain 已删除）。
   请走 GitHub Actions：push 分支 → 开 PR → 看 Actions 结果。
   本地需要跑单步命令时，用 GitHub Actions 的 workflow_dispatch 触发。
EOF
    exit 2
  fi
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME:-}" ]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/android-sdk" "$HOME/.cache/hubdeck-toolchain/sdk"; do
    if [ -d "$candidate" ]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME:-}" ]; then
  if [ -n "${HUB_DECK_ENV_OPTIONAL:-}" ]; then
    echo "! 未找到 ANDROID_HOME：跳过构建环境配置（仅影响构建，不影响 adb 设备操作）" >&2
  else
    cat >&2 <<'EOF'
❌ 没有 ANDROID_HOME：本地编译已于 2026-11-13 停用。
   请走 GitHub Actions：.github/workflows/ci.yml（构建 + 单测 + Lint）
                        .github/workflows/ci-emulator.yml（模拟器截图取证）
EOF
    exit 2
  fi
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

# PATH 追加（CI runner 上这些目录都已存在）
if [ -d "$JAVA_HOME/bin" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -d "$ANDROID_HOME/platform-tools" ]; then export PATH="$ANDROID_HOME/platform-tools:$PATH"; fi
if [ -d "$ANDROID_HOME/cmdline-tools/latest/bin" ]; then export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"; fi
if [ -d "$ANDROID_HOME/build-tools" ]; then
  LATEST_BT=$(ls -1 "$ANDROID_HOME/build-tools" 2>/dev/null | sort -V | tail -1)
  if [ -n "$LATEST_BT" ] && [ -d "$ANDROID_HOME/build-tools/$LATEST_BT" ]; then
    export PATH="$ANDROID_HOME/build-tools/$LATEST_BT:$PATH"
  fi
fi
if [ -d "$ANDROID_HOME/emulator" ]; then export PATH="$PATH:$ANDROID_HOME/emulator"; fi
if [ -d "$HOME/.local/bin" ]; then export PATH="$PATH:$HOME/.local/bin"; fi
