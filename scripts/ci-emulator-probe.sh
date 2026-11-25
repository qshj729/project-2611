#!/usr/bin/env bash
# 通道 E：CI 模拟器取证脚本（由 reactivecircus/android-emulator-runner 调用）。
#
# 做什么：等系统安定 → 装 APK → 启动 → 等窗口真正在前台 → 截图 + UI 树 + logcat，
#         落到 emulator-evidence/。
#
# 运行环境现实（2026-11-18 实测 run 44262601326）：
#   该 runner 没有 /dev/kvm（ProbeKVM: This user doesn't have permissions to use KVM），
#   模拟器以 `-accel off` 纯软件模拟（TCG）跑：冷启动 404s、install 3.5 分钟，
#   刚开机那几分钟 SystemUI 还会弹「Application Not Responding: com.android.systemui」，
#   弹窗会抢走窗口焦点 → 截图截到弹窗、UI 树里只有 systemui。
#   所以本脚本按「慢 + 系统会弹窗」写：先等系统安定，再装、再启动，
#   等焦点期间持续清掉系统错误弹窗。
#
# 关键坑（2026-11-13 / 2026-11-18 实跑踩到）：
#   1) 模拟器冷启动期间 SystemUI 会弹「System UI isn't responding」，
#      截图截到弹窗、UI 树里只有 systemui → 先关错误弹窗再启动。
#   2) CI runner 负载高时 App 冷启动会 ANR：
#      「Input dispatching timed out (Application does not have a focused window)」，
#      进程被杀掉，UI 树里只剩 launcher，而旧的判定只看 mFocusedApp 会误判为通过。
#      → 焦点判定必须看 mCurrentFocus；并且不通过要重启一次再取证。
#   3) mFocusedApp 在窗口没获得焦点时仍然指向我们的包（2026-11-18 实测
#      mFocusedApp=top.quup.myapps.debug 而 mCurrentFocus 是 systemui 的 ANR 弹窗），
#      所以焦点判定只看 mCurrentFocus。
#
# 证据口径（见 design.md §7.4）：
#   - 截图 png = 界面存在且渲染完成
#   - UI 树 xml = 可复核的语义结构（用于断言控件文案）
#   - logcat = 崩溃 / ANR 现场
#
# 门禁语义：**五项检查全过才算通过**，否则退出码 1，让工作流变红。
#   禁止「无论检查结果如何都打印 ✅」——那会让 CI 变成假绿。

set -uo pipefail

EVIDENCE_DIR="${EVIDENCE_DIR:-emulator-evidence}"
APK_PATH="${APK_PATH:-android/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="${HUB_DECK_PACKAGE:-top.quup.myapps.debug}"
ACTIVITY="${HUB_DECK_ACTIVITY:-app.MainActivity}"
SETTLE_WAIT="${SETTLE_WAIT:-300}"   # 等系统安定（开机后 SystemUI 还在忙）
FOCUS_WAIT="${FOCUS_WAIT:-300}"     # 等我们的窗口拿到焦点
RENDER_WAIT="${RENDER_WAIT:-20}"    # 拿到焦点后再等渲染
INSTALL_RETRY="${INSTALL_RETRY:-3}" # 安装重试次数（package 服务未就绪时会失败）
INSTALL_RETRY_WAIT="${INSTALL_RETRY_WAIT:-10}" # 安装失败后的重试间隔

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

# 0) 关掉系统 ANR / 错误弹窗，关动画（否则截图容易被弹窗占住）
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true

# 弹窗清理：ANR / 崩溃对话框会抢焦点，BACK + CLOSE_SYSTEM_DIALOGS 两条路都走一遍
dismiss_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
}

current_focus() {
  # dumpsys window 会按 display 输出多行 mCurrentFocus，未激活的 display 对应
  # "mCurrentFocus=null"。只取 head -1 会命中那个 null 行而漏掉真正的焦点行
  # （2026-11-21 实测：焦点明明落在 top.quup.myapps.debug/app.MainActivity，
  #  但 head -1 取到 null 行 → 误判成「没拿到焦点」，白等 10 分钟后判红）。
  adb shell dumpsys window 2>/dev/null \
    | grep -E "mCurrentFocus" | tr -d '\r' | grep -v '^[[:space:]]*$' | awk '!seen[$0]++'
}

# 焦点判定：mCurrentFocus=null 是「窗口系统还没起来」，不是「已经有焦点」。
# 2026-11-18 run 44270374570 实测：系统服务（settings / overlay / activity / package）
# 全都还没注册时，dumpsys 仍然会输出 "mCurrentFocus=null"，
# 旧逻辑只判字符串非空 → 误判成「系统已安定」→ 紧接着 install 失败
# （Can't find service: package），脚本还继续往下跑，白等 5 分钟才报红。
focus_ready() {
  local focus="$1"
  [ -n "$focus" ] || return 1
  # 只要有一行非 null 的 mCurrentFocus 就算窗口系统就绪
  # （多 display 场景下 null 行与真实焦点行会同时出现）
  printf '%s\n' "$focus" | grep -v "mCurrentFocus=null" | grep -q "mCurrentFocus" || return 1
  return 0
}

# package 服务（PackageManager）是否已注册。没注册就装不了 APK。
package_service_ready() {
  local out
  out="$(adb shell service check package 2>/dev/null | tr -d '\r' | head -1)"
  case "$out" in
    *"package: found"*|*"package: ok"*) return 0 ;;
  esac
  return 1
}

# 1) 等系统安定：开机后那几分钟 SystemUI 自己都在 ANR，等它不再弹错窗再动我们的应用
settle_system() {
  local deadline=$(( $(date +%s) + SETTLE_WAIT ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    local focus
    focus="$(current_focus)"
    printf '%s\n' "$focus" >"$EVIDENCE_DIR/focus-boot.txt"
    if focus_ready "$focus"; then
      if printf '%s' "$focus" | grep -qi "not responding"; then
        log "系统还在弹 ANR，先按掉：$focus"
        dismiss_dialogs
      elif package_service_ready; then
        log "系统已安定：$focus（package 服务已就绪）"
        return 0
      else
        log "窗口已有焦点，但 package 服务还没起来，等一等"
      fi
    else
      log "窗口系统还没就绪（$focus），等一等"
    fi
    sleep 5
  done
  log "系统安定等待超时（${SETTLE_WAIT}s），继续尝试"
  return 1
}

# 2) 安装：装不上就直接判红，不再空跑后面的启动 / 截图
install_apk() {
  local attempt=1
  while [ "$attempt" -le "$INSTALL_RETRY" ]; do
    log "安装 APK（第 $attempt/$INSTALL_RETRY 次）"
    if adb install -r -t "$APK_PATH" >"$EVIDENCE_DIR/install.log" 2>&1; then
      cat "$EVIDENCE_DIR/install.log"
      if adb shell pm path "$PACKAGE" 2>/dev/null | grep -q "package:"; then
        log "安装成功，$PACKAGE 已在设备上"
        return 0
      fi
      log "install 返回 0，但 pm path 查不到 $PACKAGE，按失败处理"
    else
      cat "$EVIDENCE_DIR/install.log"
      log "安装失败（多数是 package 服务还没就绪），10 秒后重试"
    fi
    sleep "$INSTALL_RETRY_WAIT"
    attempt=$(( attempt + 1 ))
  done
  echo "❌ APK 安装失败：$APK_PATH" | tee -a "$SUMMARY"
  echo "--- install.log ---" | tee -a "$SUMMARY"
  cat "$EVIDENCE_DIR/install.log" 2>/dev/null | tee -a "$SUMMARY"
  echo "结论: ❌ 通道 E 取证无效——APK 没装上，截图 / UI 树 / logcat 全部无意义" | tee -a "$SUMMARY"
  ls -la "$EVIDENCE_DIR" | tee -a "$SUMMARY"
  return 1
}

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
    current_focus >"$EVIDENCE_DIR/focus.txt"
    if grep -q "$PACKAGE" "$EVIDENCE_DIR/focus.txt" 2>/dev/null; then
      adb shell dumpsys window 2>/dev/null \
        | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | head -6 >"$EVIDENCE_DIR/focus.txt"
      return 0
    fi
    # 系统弹窗占住前台时按掉它
    if grep -qiE "com.android.systemui|com.android.launcher|not responding" "$EVIDENCE_DIR/focus.txt" 2>/dev/null; then
      dismiss_dialogs
    fi
    sleep 3
  done
  adb shell dumpsys window 2>/dev/null \
    | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r' | head -6 >"$EVIDENCE_DIR/focus.txt"
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

  # UI 树（uiautomator 在软模拟下容易因为窗口不 idle 失败，重试几次）
  local attempt=1
  while [ "$attempt" -le 3 ]; do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    if adb pull /sdcard/ui.xml "$EVIDENCE_DIR/01-launch-ui.xml" >/dev/null 2>&1 \
      && [ -s "$EVIDENCE_DIR/01-launch-ui.xml" ]; then
      log "UI 树已落盘: $EVIDENCE_DIR/01-launch-ui.xml（第 $attempt 次）"
      break
    fi
    log "UI 树 dump 第 $attempt 次失败，重试"
    sleep 10
    attempt=$(( attempt + 1 ))
  done

  # 当前前台 Activity（复核用）
  adb shell dumpsys activity activities 2>/dev/null \
    | grep -E "mResumedActivity|mCurrentFocus" | tr -d '\r' | head -6 \
    >"$EVIDENCE_DIR/resumed.txt" || true

  # logcat
  adb logcat -d -v brief '*:W' 2>/dev/null | tail -400 >"$EVIDENCE_DIR/logcat-warn.txt" || true
  adb logcat -d -v brief 2>/dev/null \
    | grep -iE "hubdeck|tsnet|tailscale" | tail -120 \
    >"$EVIDENCE_DIR/logcat-app.txt" || true
}

# 3) 等系统安定 → 装 APK（装不上直接判红）→ 启动 + 轮询焦点（失败则重启一次再试）
log "等系统安定（最多 ${SETTLE_WAIT}s）"
settle_system || true

if ! install_apk; then
  exit 1
fi

attempt=1
focus_ok=0
while [ "$attempt" -le 2 ]; do
  log "第 $attempt 次启动"
  launch_app
  if wait_for_focus; then
    focus_ok=1
    break
  fi
  log "第 $attempt 次没拿到前台焦点（常见原因：冷启动 ANR / 系统弹窗抢焦点），重启一次再试"
  attempt=$(( attempt + 1 ))
done
log "当前焦点: $(cat "$EVIDENCE_DIR/focus.txt" 2>/dev/null || echo '<空>')"

# 4) 取证（拿到焦点后先等渲染，再截图 / dump）
log "等 ${RENDER_WAIT}s 让界面渲染"
sleep "$RENDER_WAIT"
collect_evidence

# 5) 五项检查
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
if [ -s "$EVIDENCE_DIR/01-launch.png" ] && [ -s "$EVIDENCE_DIR/01-launch-ui.xml" ]; then
  shot_ok=1
fi

# 6) 结论：五项全过才算通过（fail-closed）
failed=""
[ "$focus_ok" = "1" ] || failed="$failed 焦点未落在 $PACKAGE\n"
[ "$ui_ok" = "1" ] || failed="$failed UI 树不含 $PACKAGE\n"
[ "$proc_ok" = "1" ] || failed="$failed 进程已退出\n"
[ "$anr_ok" = "1" ] || failed="$failed 出现 FATAL EXCEPTION / ANR\n"
[ "$shot_ok" = "1" ] || failed="$failed 截图 / UI 树为空\n"

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
