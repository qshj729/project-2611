#!/usr/bin/env bash
# 通道 E：CI 模拟器取证脚本（由 reactivecircus/android-emulator-runner 调用）。
#
# 做什么：装 APK → 启动 → 等待渲染 → 截图 + UI 树 + logcat，落到 emulator-evidence/。
# 约定：模拟器动作必须失败即停（set -e），但取证本身失败不应让整条流水线失去证据，
#       所以关键步骤都带 || true，并把结果写进 summary 文件。
#
# 证据口径（见 docs/ci-only-policy.md）：
#   - 截图 png = 界面存在且渲染完成
#   - UI 树 xml = 可复核的语义结构（用于断言控件文案）
#   - logcat = 崩溃 / ANR 现场

set -uo pipefail

EVIDENCE_DIR="${EVIDENCE_DIR:-emulator-evidence}"
APK_PATH="${APK_PATH:-android/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${HUB_DECK_PACKAGE:-com.hubdeck.app.debug}"
ACTIVITY="${HUB_DECK_ACTIVITY:-com.hubdeck.app.MainActivity}"
WAIT_SECONDS="${WAIT_SECONDS:-12}"

mkdir -p "$EVIDENCE_DIR"
SUMMARY="$EVIDENCE_DIR/summary.txt"
: >"$SUMMARY"

log() { echo "→ $*"; }

log "APK: $APK_PATH"
if [ ! -f "$APK_PATH" ]; then
  echo "❌ 找不到 APK: $APK_PATH" | tee -a "$SUMMARY"
  ls -la android/app/build/outputs/apk/debug/ 2>/dev/null | tee -a "$SUMMARY" || true
  exit 1
fi

log "安装 APK"
adb install -r -t "$APK_PATH" 2>&1 | tee "$EVIDENCE_DIR/install.log"

log "清空 logcat 并启动 $PACKAGE/$ACTIVITY"
adb logcat -c || true
adb shell am start -W -n "$PACKAGE/$ACTIVITY" 2>&1 | tee "$EVIDENCE_DIR/launch.log" || true

log "等待 ${WAIT_SECONDS}s 让界面渲染"
sleep "$WAIT_SECONDS"

# 截图
SHOT="$EVIDENCE_DIR/01-launch.png"
adb exec-out screencap -p >"$SHOT" 2>/dev/null || true
[ -s "$SHOT" ] && log "截图已落盘: $SHOT" || echo "❌ 截图为空" | tee -a "$SUMMARY"

# UI 树
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
adb pull /sdcard/ui.xml "$EVIDENCE_DIR/01-launch-ui.xml" >/dev/null 2>&1 || true

# 进程是否存活
if adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  echo "✅ 进程存活: $PACKAGE" | tee -a "$SUMMARY"
else
  echo "❌ 进程已退出: $PACKAGE" | tee -a "$SUMMARY"
fi

# 崩溃与 ANR 现场
adb logcat -d -v brief '*:W' 2>/dev/null | tail -400 >"$EVIDENCE_DIR/logcat-warn.txt" || true
if grep -qE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" 2>/dev/null; then
  echo "❌ 日志里出现 FATAL EXCEPTION / ANR" | tee -a "$SUMMARY"
  grep -nE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" | head -20 | tee -a "$SUMMARY"
else
  echo "✅ 无 FATAL EXCEPTION / ANR" | tee -a "$SUMMARY"
fi

echo "--- 证据清单 ---" | tee -a "$SUMMARY"
ls -la "$EVIDENCE_DIR" | tee -a "$SUMMARY"
