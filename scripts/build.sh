#!/usr/bin/env bash
# HubDeck 构建入口：统一设置工具链后跑 Gradle。
#
# 用法:
#   scripts/build.sh :app:assembleDebug
#   scripts/build.sh :app:testDebugUnitTest :app:lintDebug
#   scripts/build.sh            # 默认 assembleDebug + 单测
#
# 退出码即 Gradle 退出码；日志同时写到 ~/.cache/hubdeck-toolchain/build-$(date +%s).log

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/hubdeck-env.sh
source "$REPO_ROOT/scripts/hubdeck-env.sh"

LOG_DIR="${HUBDECK_TOOLCHAIN:-$HOME/.cache/hubdeck-toolchain}"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S)-$$.log"

# 并行协作纪律：整台机器只有 5.9GB RAM，多个 agent 同时跑 Gradle 会互相抢内存，
# 导致 OOM（表现为 adapter_failed / process_lost）。这里用 flock 把构建串行化：
# 同一时刻只允许一个 Gradle 构建，其余 agent 排队等待（默认最多等 40 分钟）。
BUILD_LOCK="$LOG_DIR/build.lock"
LOCK_WAIT="${HUBDECK_BUILD_LOCK_WAIT:-2400}"
mkdir -p "$LOG_DIR"
exec 9>"$BUILD_LOCK"
if ! flock -w "$LOCK_WAIT" 9; then
  echo "→ 等构建锁超过 ${LOCK_WAIT}s，放弃（另一个 agent 的构建还在跑）" >&2
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
