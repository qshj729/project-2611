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
  set -- :app:assembleDebug :app:testDebugUnitTest
fi

cd "$REPO_ROOT/android"

echo "→ gradle $* (log: $LOG_FILE)"
set +e
./gradlew "$@" --max-workers=2 2>&1 | tee "$LOG_FILE"
STATUS="${PIPESTATUS[0]}"
set -e

if [ "$STATUS" -eq 0 ]; then
  echo "→ BUILD OK: $LOG_FILE"
else
  echo "→ BUILD FAILED ($STATUS): $LOG_FILE" >&2
fi
exit "$STATUS"
