#!/usr/bin/env bash
# 通道 E：CI 模拟器取证脚本（由 reactivecircus/android-emulator-runner 调用）。
#
# 做什么：装 APK → 启动 → 等到我们的窗口真正在前台 → 截图 + UI 树 + logcat，
#         落到 emulator-evidence/。
#
# 关键坑（2026-11-13 实跑踩到，两次）：
#   1) 模拟器冷启动期间 SystemUI 会弹「System UI isn't responding」，
#      截图截到弹窗、UI 树里只有 systemui → 先关错误弹窗再启动。
#   2) CI runner 负载高时 App 冷启动会 ANR：
#      「Input dispatching timed out (Application does not have a focused window)」，
#      进程被杀掉，UI 树里只剩 launcher，而旧的判定只看 mFocusedApp 会误判为通过。
#      → 焦点判定必须看 mCurrentFocus；并且不通过要重启一次再取证。
#
# 证据口径（见 docs/ci-only-policy.md）：
#   - 截图 png = 界面存在且渲染完成
#   - UI 树 xml = 可复核的语义结构（用于断言控件文案）
#   - logcat = 崩溃 / ANR 现场
#
# 门禁语义：**四项检查全过才算通过**，否则退出码 1，让工作流变红。
#   禁止「无论检查结果如何都打印 ✅」——那会让 CI 变成假绿。

set -uo pipefail

EVIDENCE_DIR="${EVIDENCE_DIR:-emulator-evidence}"
APK_PATH="${APK_PATH:-android/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${HUB_DECK_PACKAGE:-com.hubdeck.app.debug}"
ACTIVITY="${HUB_DECK_ACTIVITY:-com.hubdeck.app.MainActivity}"
FOCUS_WAIT="${FOCUS_WAIT:-120}"
RETRY_WAIT="${RETRY_WAIT:-90}"

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

launch_app() {
  log "清空 logcat 并启动 $PACKAGE/$ACTIVITY"
  adb logcat -c >/dev/null 2>&1 || true
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  adb shell am start -n "$PACKAGE/$ACTIVITY" >"$EVIDENCE_DIR/launch.log" 2>&1 || true
}

# 轮询前台窗口：只看 mCurrentFocus（mFocusedApp 在窗口没获得焦点时仍然指向我们的包，
# 用它判定会把「没进前台」误判成「进了前台」）。
wait_for_focus() {
  local deadline=$(( $(date +%s) + FOCUS_WAIT ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    adb shell dumpsys window 2>/dev/null \
      | grep -E "mCurrentFocus" | tr -d '\r' | head -1 >"$EVIDENCE_DIR/focus.txt"
    if grep -q "$PACKAGE" "$EVIDENCE_DIR/focus.txt" 2>/dev/null; then
      adb shell dumpsys window 2>/dev/null \
        | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | head -3 >"$EVIDENCE_DIR/focus.txt"
      return 0
    fi
    # 系统弹窗占住前台时按掉它
    adb shell dumpsys window 2>/dev/null \
      | grep -E "mCurrentFocus" | tr -d '\r' | head -1 \
      | grep -qE "com.android.systemui|com.android.launcher" \
      && adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 3
  done
  adb shell dumpsys window 2>/dev/null \
    | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | head -3 >"$EVIDENCE_DIR/focus.txt"
  return 1
}

collect_evidence() {
  # 截图
  adb exec-out screencap -p >"$EVIDENCE_DIR/01-launch.png" 2>/dev/null || true
  if [ -s "$EVIDENCE_DIR/01-launch.png" ]; then
    log "截图已落盘: $EVIDENCE_DIR/01-launch.png"
  else
    echo "❌ 截图为空" | tee -a "$SUMMARY"
  fi
  # UI 树
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "$EVIDENCE_DIR/01-launch-ui.xml" >/dev/null 2>&1 || true
  # logcat
  adb logcat -d -v brief '*:W' 2>/dev/null | tail -400 >"$EVIDENCE_DIR/logcat-warn.txt" || true
}

# 3) 启动 + 轮询焦点（失败则重启一次再试）
attempt=1
focus_ok=0
while [ "$attempt" -le 2 ]; do
  log "第 $attempt 次启动"
  launch_app
  if wait_for_focus; then
    focus_ok=1
    break
  fi
  log "第 $attempt 次没拿到前台焦点（常见原因：冷启动 ANR），重启一次再试"
  attempt=$(( attempt + 1 ))
done
log "当前焦点: $(cat "$EVIDENCE_DIR/focus.txt" 2>/dev/null || echo '<空>')"

# 4) 取证
collect_evidence

# 5) 四项检查
ui_ok=0
if grep -q "package=\"$PACKAGE\"" "$EVIDENCE_DIR/01-launch-ui.xml" 2>/dev/null; then
  ui_ok=1
  echo "✅ UI 树含 $PACKAGE" | tee -a "$SUMMARY"
else
  echo "❌ UI 树里没有 $PACKAGE（应用没在前台，截到的是别的界面）" | tee -a "$SUMMARY"
fi

proc_ok=0
if adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
  proc_ok=1
  echo "✅ 进程存活: $PACKAGE" | tee -a "$SUMMARY"
else
  echo "❌ 进程已退出: $PACKAGE" | tee -a "$SUMMARY"
fi

anr_ok=0
if grep -qE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" 2>/dev/null; then
  echo "❌ 日志里出现 FATAL EXCEPTION / ANR（$PACKAGE）" | tee -a "$SUMMARY"
  grep -nE "FATAL EXCEPTION|ANR in $PACKAGE" "$EVIDENCE_DIR/logcat-warn.txt" | head -20 | tee -a "$SUMMARY"
else
  anr_ok=1
  echo "✅ 无 $PACKAGE 的 FATAL EXCEPTION / ANR" | tee -a "$SUMMARY"
fi

shot_ok=0
if [ -s "$EVIDENCE_DIR/01-launch.png" ]; then
  shot_ok=1
fi

# 6) 结论：四项全过才算通过（fail-closed）
failed=""
[ "$focus_ok" = "1" ] || failed="$failed 焦点未落在 $PACKAGE\n"
[ "$ui_ok" = "1" ] || failed="$failed UI 树不含 $PACKAGE\n"
[ "$proc_ok" = "1" ] || failed="$failed 进程已退出\n"
[ "$anr_ok" = "1" ] || failed="$failed 出现 FATAL EXCEPTION / ANR\n"
[ "$shot_ok" = "1" ] || failed="$failed 截图为空\n"

if [ -z "$failed" ]; then
  echo "结论: ✅ 通道 E 取证有效——截图与 UI 树对应该应用界面" | tee -a "$SUMMARY"
  echo "--- 证据清单 ---" | tee -a "$SUMMARY"
  ls -la "$EVIDENCE_DIR" | tee -a "$SUMMARY"
  exit 0
fi

echo "结论: ❌ 通道 E 取证无效——以下检查未通过：" | tee -a "$SUMMARY"
printf "%b" "$failed" | tee -a "$SUMMARY"
echo "--- 证据清单 ---" | tee -a "$SUMMARY"
ls -la "$EVIDENCE_DIR" | tee -a "$SUMMARY"
exit 1
