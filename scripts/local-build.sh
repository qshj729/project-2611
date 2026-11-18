#!/usr/bin/env bash
# HubDeck 本地编译检查入口。
#
# 定位（2026-11-18 老板决策：本地要尽量检查好，再提交）：
#   本地只做「提交前自检」——编译 + 单测 + Lint；
#   交付物（APK）仍由 GitHub Actions 出（scripts/build.sh），本地不出 APK。
#   原因见 docs/ci-only-policy.md §2.5：本机只有 5.8GB 内存，gradle.properties 封了
#   1g 堆，:app:mergeExtDexDebug 会被 R8 OOM；CI runner 上是 2g 堆，不会。
#
# 用法:
#   scripts/local-build.sh                     # 默认：编译 + 单测 + Lint（推荐）
#   scripts/local-build.sh compileDebugKotlin  # 自定义 gradle 任务
#   scripts/local-build.sh --assemble         # 额外尝试打 debug APK（本机可能 OOM）
#
# 退出码即 gradle 退出码；失败时先看日志再推远端，别让 CI 去发现同样的错。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

log() { printf '→ %s\n' "$*"; }
die() { printf '✗ %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. JDK 17
# ---------------------------------------------------------------------------
JAVA_HOME="${HUBDECK_LOCAL_JAVA_HOME:-$HOME/tools/jdk17}"
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  else
    die "找不到 JDK。请装 Temurin 17 到 ~/tools/jdk17，或设置 HUBDECK_LOCAL_JAVA_HOME。"
  fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
log "JDK: $JAVA_HOME ($("$JAVA_HOME/bin/java" -version 2>&1 | head -1))"

# ---------------------------------------------------------------------------
# 2. Android SDK
# ---------------------------------------------------------------------------
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
[ -d "$SDK_DIR/platforms" ] || die "找不到 Android SDK（$SDK_DIR/platforms 不存在）。设置 ANDROID_HOME。"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
log "SDK: $SDK_DIR"

# local.properties 不进版本库（.gitignore 已忽略），每次自动生成，省得手写
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$REPO_ROOT/android/local.properties"

# ---------------------------------------------------------------------------
# 3. Gradle 分发包：wrapper 自带 10s 超时，本机慢网络必超时 → 先用 curl 兜底
# ---------------------------------------------------------------------------
WRAPPER_PROPS="$REPO_ROOT/android/gradle/wrapper/gradle-wrapper.properties"
DIST_URL="$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS" | sed 's/\\//g')"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

dist_ready() {
  # $1 = 分发包哈希目录
  [ -d "$1" ] && find "$1" -maxdepth 1 -name 'gradle-*' -type d | grep -q .
}

ensure_dist() {
  [ -n "$DIST_URL" ] || return 0
  local dist_name hash_dir
  dist_name="$(basename "$DIST_URL" .zip)"
  hash_dir="$(python3 - "$DIST_URL" <<'PY'
import hashlib, sys
url = sys.argv[1]
md5 = hashlib.md5(url.encode("utf-8")).digest()
n = int.from_bytes(md5, "big")
digits = "0123456789abcdefghijklmnopqrstuvwxyz"
out = ""
while n:
    n, r = divmod(n, 36)
    out = digits[r] + out
print(out or "0")
PY
)"
  local target="$GRADLE_HOME/wrapper/dists/$dist_name/$hash_dir"
  if dist_ready "$target"; then
    return 0
  fi
  mkdir -p "$target"
  log "Gradle 分发包缺失，curl 兜底下载（wrapper 自带 10s 超时本机必超时）…"
  if ! curl -sL --retry 3 --retry-all-errors --connect-timeout 30 -o "$target/$dist_name.zip" "$DIST_URL"; then
    rm -f "$target/$dist_name.zip"
    die "Gradle 分发包下载失败：$DIST_URL"
  fi
}
ensure_dist

# ---------------------------------------------------------------------------
# 4. Gradle 缓存清理：Gradle 8.11 的自动 GC 会在构建进行中删掉刚下载的
#    artifact（实测 2026-11-18：NoSuchFileException modules-2/...jar）→ 关掉
# ---------------------------------------------------------------------------
GRADLE_PROPS="$GRADLE_HOME/gradle.properties"
mkdir -p "$GRADLE_HOME"
if ! grep -q '^org\.gradle\.cache\.cleanup=' "$GRADLE_PROPS" 2>/dev/null; then
  {
    echo "# HubDeck 本地构建：关掉 Gradle 8.11 自动缓存清理"
    echo "# 它会在构建进行中删掉 ~/.gradle/caches/modules-2 里刚下载的 artifact，"
    echo "# 表现为 NoSuchFileException / Failed to store cache entry ... .part。"
    echo "org.gradle.cache.cleanup=false"
    echo "org.gradle.vfs.watch=false"
  } >> "$GRADLE_PROPS"
fi

# ---------------------------------------------------------------------------
# 5. 跑任务
# ---------------------------------------------------------------------------
TASKS=()
WITH_ASSEMBLE=0
for arg in "$@"; do
  case "$arg" in
    --assemble) WITH_ASSEMBLE=1 ;;
    *) TASKS+=("$arg") ;;
  esac
done

if [ "${#TASKS[@]}" -eq 0 ]; then
  # compileDebugKotlin / compileKotlin 覆盖全部模块的编译（含 :core:model 纯 Kotlin 模块）
  # testDebugUnitTest 用聚合写法（:app:testDebugUnitTest 是 NO-SOURCE，会假绿）
  TASKS=(compileDebugKotlin compileKotlin testDebugUnitTest :app:lintDebug)
  [ "$WITH_ASSEMBLE" = "1" ] && TASKS+=(:app:assembleDebug)
fi

LOG_DIR="${HUBDECK_LOCAL_LOG_DIR:-$REPO_ROOT/.build-logs}"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/local-build-$(date +%Y%m%d-%H%M%S).log"

log "gradle ${TASKS[*]}"
log "日志: $LOG_FILE"

cd "$REPO_ROOT/android"
set -o pipefail
./gradlew "${TASKS[@]}" --max-workers="${HUBDECK_LOCAL_WORKERS:-1}" 2>&1 | tee "$LOG_FILE"
STATUS=${PIPESTATUS[0]}

if [ "$STATUS" -eq 0 ]; then
  log "本地检查通过：$LOG_FILE"
else
  printf '✗ 本地检查失败（exit %s）：%s\n' "$STATUS" "$LOG_FILE" >&2
  printf '  先在这里修好再推远端，别把同样的错留给 CI 发现。\n' >&2
fi
exit "$STATUS"
