#!/usr/bin/env bash
# 通道 E：CI 模拟器取证脚本（由 reactivecircus/android-emulator-runner 调用）。
#
# 做什么：装 APK → 启动 → 等到我们的窗口真正在前台 → 截图 + UI 树 + logcat，
#         落到 emulator-evidence/。
#
# 关键坑（2026-11-13 首次实跑踩到）：模拟器冷启动期间 SystemUI 会弹
# 「System UI isn't responding」，此时截图截到的是弹窗，UI 树里也只有 systemui，
# 看起来像 App 没起来。所以这里：先关错误弹窗 → 启动后轮询焦点窗口 →
# 必要时把弹窗按掉 → 确认前台包名是我们的再截图。
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
FOCUS_WAIT="${FOCUS_WAIT:-120}"

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

# 1) 关掉系统 ANR / 错误弹窗，关动画（否则截图容易被弹窗占住）
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

# 2) 安装
log "安装 APK"
adb install -r -t "$APK_PATH" 2>&1 | tee "$EVIDENCE_DIR/install.log"

# 3) 启动（不用 am start -W：冷启动会超过 18s 超时，反而把流程打断）
log "清空 logcat 并启动 $PACKAGE/$ACTIVITY"
adb logcat -c >/dev/null 2>&1 || true
adb shell am start -n "$PACKAGE/$ACTIVITY" >"$EVIDENCE_DIR/launch.log" 2>&1 || true

# 4) 轮询前台窗口，直到是我们的包
log "等待前台窗口（最多 ${FOCUS_WAIT}s）"
focus=""
deadline=$(( $(date +%s) + FOCUS_WAIT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  focus=$(adb shell dumpsys window 2>/dev/null \
          | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | head -3)
  case "$focus" in
    *"$PACKAGE"*) break ;;
  esac
  # 系统弹窗占住前台时按掉它
  case "$focus" in
    *com.android.systemui*|*com.android.launcher*) adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true ;;
  esac
  sleep 3
done
echo "$focus" >"$EVIDENCE_DIR/focus.txt"
log "当前焦点: ${focus:-<空>}"

focus_ok=0
case "$focus" in *"$PACKAGE"*) focus_ok=1 ;; esac

# 5) 截图
SHOT="$EVIDENCE_DIR/01-launch.png"
adb exec-out screencap -p >"$SHOT" 2>/dev/null || true
if [ -s "$SHOT" ]; then
  log "截图已落盘: $SHOT"
else
  echo "❌ 截图为空" | tee -a "$SUMMARY"
fi

# 6) UI 树
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
adb pull /sdcard/ui.xml "$EVIDENCE_DIR/01-launch-ui.xml" >/dev/null 2>&1 || true

if grep -q "package=\"$PACKAGE\"" "$EVIDENCE_DIR/01-launch-ui.xml" 2>/dev/null; then
  echo "✅ UI 树含 $PACKAGE" | tee -a "$SUMMARY"
else
  echo "⚠️ UI 树里没有 $PACKAGE（前台可能被系统弹窗占住）" | tee -a "$SUMMARY"
fi

# 7) 进程存活
if adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  echo "✅ 进程存活: $PACKAGE" | tee -a "$SUMMARY"
else
  echo "❌ 进程已退出: $PACKAGE" | tee -a "$SUMMARY"
fi

# 8) 崩溃 / ANR 现场
adb logcat -d -v brief '*:W' 2>/dev/null | tail -400 >"$EVIDENCE_DIR/logcat-warn.txt" || true
if grep -qE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" 2>/dev/null; then
  echo "❌ 日志里出现 FATAL EXCEPTION / ANR（$PACKAGE）" | tee -a "$SUMMARY"
  grep -nE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" | head -20 | tee -a "$SUMMARY"
else
  echo "✅ 无 $PACKAGE 的 FATAL EXCEPTION / ANR" | tee -a "$SUMMARY"
fi

# 9) 结论：截图能不能当界面证据
if [ "$focus_ok" = "1" ]; then
  echo "结论: ✅ 通道 E 取证有效——截图与 UI 树对应该应用界面" | tee -a "$SUMMARY"
else
  echo "结论: ❌ 通道 E 取证无效——应用未进入前台，截图不可当界面证据（见 focus.txt）" | tee -a "$SUMMARY"
fi

echo "--- 证据清单 ---" | tee -a "$SUMMARY"
ls -la "$EVIDENCE_DIR" | tee -a "$SUMMARY"
