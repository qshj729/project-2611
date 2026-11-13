#!/usr/bin/env bash
# HubDeck 构建入口（CI 内唯一入口）。
#
# 2026-11-13 起：本地不再构建，本脚本只在 GitHub Actions runner 上跑
# （.github/workflows/ci.yml、apk.yml、ci-emulator.yml 调用的都是它）。
#
# 用法:
#   scripts/build.sh :app:assembleDebug
#   scripts/build.sh :app:testDebugUnitTest :app:lintDebug
#   scripts/build.sh            # 默认 assembleDebug + 单测
#
# 退出码即 Gradle 退出码；日志写到工作区 .build-logs/（CI 会作为 artifact 上传，
# 也就是「构建证据」的落点）。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/hubdeck-env.sh
source "$REPO_ROOT/scripts/hubdeck-env.sh"

LOG_DIR="${HUBDECK_BUILD_LOG_DIR:-$REPO_ROOT/.build-logs}"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S)-$$.log"

# 构建串行化：同一个 runner 上同一时刻只跑一个 Gradle 构建，避免内存争抢。
# CI 每个 job 是独立 runner，本机已不构建，这里只是保底。
BUILD_LOCK="$LOG_DIR/build.lock"
LOCK_WAIT="${HUBDECK_BUILD_LOCK_WAIT:-2400}"
exec 9>"$BUILD_LOCK"
if ! flock -w "$LOCK_WAIT" 9; then
  echo "→ 等构建锁超过 ${LOCK_WAIT}s，放弃（另一个构建还在跑）" >&2
  exit 75
fi
echo "→ 取得构建锁 $BUILD_LOCK"

if [ "$#" -eq 0 ]; then
  # 用聚合写法跑所有模块的单测；:app:testDebugUnitTest 是 NO-SOURCE（假绿）
  set -- :app:assembleDebug testDebugUnitTest
fi

cd "$REPO_ROOT/android"

# GitHub Actions 的 runner 是独占的 7GB 机器，R8 合并 dex 需要比 1g 更大的堆；
# 本机（Paperclip 宿主机只有 5.9GB、还要跑数据库与 agent 进程）保持 gradle.properties 里的 1g 上限。
# 2026-11-13 实测：CI runner 上 1g 堆会让 :app:mergeExtDexDebug 抛 OutOfMemoryError
# （run 43414013454，模拟器取证工作流因此假红）。这里只在 GitHub Actions 里放宽。
GRADLE_JVM_ARGS_OVERRIDE=""
if [ "${GITHUB_ACTIONS:-}" = "true" ]; then
  GRADLE_JVM_ARGS_OVERRIDE="${HUBDECK_GRADLE_JVM_ARGS:--Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8}"
fi

# 重试策略：只对「网络 / 缓存抖动」这类瞬时失败重试，真实编译错一次就判失败。
# 2026-11-13 实测两例同源假红：GitHub 缓存服务返回 400、插件仓库解析失败
# （run 43416506396：KSP 插件 could not resolve；run 43414013454：R8 OOM）。
RETRYABLE_PATTERN='could not resolve|Cache service responded with 400|Read timed out|Connection reset|Could not GET|Could not HEAD|Premature end of Content-Length'
MAX_ATTEMPTS="${HUBDECK_BUILD_MAX_ATTEMPTS:-3}"
attempt=1
STATUS=1
while : ; do
  echo "→ gradle $* (第 $attempt 次, log: $LOG_FILE)"
  set +e
  if [ -n "$GRADLE_JVM_ARGS_OVERRIDE" ]; then
    ./gradlew "$@" --max-workers=2 "-Dorg.gradle.jvmargs=$GRADLE_JVM_ARGS_OVERRIDE" 2>&1 | tee "$LOG_FILE"
  else
    ./gradlew "$@" --max-workers=2 2>&1 | tee "$LOG_FILE"
  fi
  STATUS="${PIPESTATUS[0]}"
  set -e
  if [ "$STATUS" -eq 0 ]; then
    break
  fi
  if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    break
  fi
  if ! grep -Eq "$RETRYABLE_PATTERN" "$LOG_FILE"; then
    echo "→ 失败原因不是网络抖动，按真实失败处理，不重试" >&2
    break
  fi
  echo "→ 命中网络抖动特征，10 秒后重试（第 $attempt 次）" >&2
  attempt=$((attempt + 1))
  sleep 10
done

if [ "$STATUS" -eq 0 ]; then
  echo "→ BUILD OK: $LOG_FILE"
else
  echo "→ BUILD FAILED ($STATUS): $LOG_FILE" >&2
fi
exit "$STATUS"
