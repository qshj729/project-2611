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
LOG_FILE="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S).log"

if [ "$#" -eq 0 ]; then
  set -- :app:assembleDebug :app:testDebugUnitTest
fi

cd "$REPO_ROOT/android"

echo "→ gradle $* (log: $LOG_FILE)"
set +e
./gradlew "$@" 2>&1 | tee "$LOG_FILE"
STATUS="${PIPESTATUS[0]}"
set -e

if [ "$STATUS" -eq 0 ]; then
  echo "→ BUILD OK: $LOG_FILE"
else
  echo "→ BUILD FAILED ($STATUS): $LOG_FILE" >&2
fi
exit "$STATUS"
