#!/usr/bin/env bash
# 取证脚本（scripts/ci-emulator-probe.sh）的判定逻辑自检。
#
# 为什么需要：通道 E 的真实环境（软模拟、无 KVM）又慢又不稳，脚本判错时反馈周期是
# 20 分钟以上，而且错法五花八门（run 44270374570：APK 根本没装上，脚本却继续空跑
# 5 分钟才报红）。这里用假的 adb 把「常见坏情况」在本地几秒内复现出来，
# 保证脚本**该红就红、该停就停**，不用每次都烧一轮模拟器。
#
# 用法: bash scripts/ci-emulator-probe.selftest.sh
# 退出码 0 = 全部通过。

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROBE="$REPO_ROOT/scripts/ci-emulator-probe.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASS=0
FAIL=0

ok()   { PASS=$((PASS+1)); printf '✅ %s\n' "$*"; }
bad()  { FAIL=$((FAIL+1)); printf '❌ %s\n' "$*"; }

# ---------------------------------------------------------------------------
# 假 adb：按场景返回不同的系统状态
# ---------------------------------------------------------------------------
STUB="$WORK/bin"
mkdir -p "$STUB"
cat >"$STUB/adb" <<'STUB_EOF'
#!/usr/bin/env bash
# 假的 adb：模拟几种真实遇到过的系统状态
scenario="${STUB_SCENARIO:-happy}"
args="$*"
case "$args" in
  "shell settings put"*) exit 0 ;;
  "shell am broadcast"*) exit 0 ;;
  "shell input keyevent"*) exit 0 ;;
  "shell dumpsys window"*)
    case "$scenario" in
      focus_null) echo "  mCurrentFocus=null" ;;
      # 多 display：未激活的 display 先输出 null 行，真正的焦点行在后面
      focus_multi_display)
        echo "  mCurrentFocus=null"
        echo "  mCurrentFocus=Window{47d320e u0 top.quup.myapps.debug/app.MainActivity}"
        ;;
      *) echo "  mCurrentFocus=Window{9f1b2c u0 top.quup.myapps.debug/app.MainActivity}" ;;
    esac
    echo "  mFocusedApp=ActivityRecord{748298a u0 top.quup.myapps.debug/.MainActivity t7}"
    exit 0 ;;
  "shell service check package"*)
    case "$scenario" in
      no_package_service) echo "Service package: not found" ;;
      *) echo "Service package: found" ;;
    esac
    exit 0 ;;
  "install"*)
    case "$scenario" in
      install_fails|no_package_service)
        echo "adb: failed to install android/app/build/outputs/apk/debug/app-debug.apk: cmd: Can't find service: package"
        exit 1 ;;
      *) echo "Performing Streamed Install"; echo "Success"; exit 0 ;;
    esac ;;
  "shell pm path"*) echo "package:/data/app/top.quup.myapps.debug/base.apk"; exit 0 ;;
  "logcat -c"*) exit 0 ;;
  "shell am force-stop"*) exit 0 ;;
  "shell am start"*) echo "Starting: Intent { cmp=top.quup.myapps.debug/app.MainActivity }"; exit 0 ;;
  "exec-out screencap -p"*) printf '\211PNG\r\n\032\nfake-image-bytes'; exit 0 ;;
  "shell uiautomator dump"*) exit 0 ;;
  "pull "*)
    dest="${args##* }"
    printf '<hierarchy><node package="top.quup.myapps.debug" text="HubDeck" /></hierarchy>' >"$dest"
    exit 0 ;;
  "shell dumpsys activity activities"*) echo "  mResumedActivity: ActivityRecord{top.quup.myapps.debug/.MainActivity}"; exit 0 ;;
  "logcat -d "*) exit 0 ;;
  "shell pidof"*) echo "12345"; exit 0 ;;
  *) exit 0 ;;
esac
STUB_EOF
chmod +x "$STUB/adb"

# 注意：Paperclip harness 每次 bash 调用都会重写 PATH（把自定义前缀换成 per-run 目录），
# 所以不能用「PATH 前面塞一个目录」来替换 adb —— 改用导出的 shell 函数：
# bash 子进程里函数优先于 PATH 查找。
export STUB
adb() { "$STUB/adb" "$@"; }
export -f adb

# 假 APK（脚本只检查文件存在）
APK="$WORK/app-debug.apk"
: >"$APK"

run_probe() {
  local scenario="$1" out_dir="$2"
  mkdir -p "$out_dir"
  (
    cd "$WORK" || exit 1
    STUB_SCENARIO="$scenario" \
    EVIDENCE_DIR="$out_dir/evidence" \
    APK_PATH="$APK" \
    SETTLE_WAIT=10 \
    FOCUS_WAIT=5 \
    RENDER_WAIT=0 \
    INSTALL_RETRY=2 \
    INSTALL_RETRY_WAIT=1 \
    bash "$PROBE" >"$out_dir/stdout.txt" 2>&1
  )
  echo $?
}

# --- 场景 1：一切正常 → 必须绿 ---------------------------------------------
rc="$(run_probe happy "$WORK/s1")"
if [ "$rc" = "0" ] && grep -q "结论: ✅" "$WORK/s1/evidence/summary.txt"; then
  ok "场景 1：正常路径 → 绿"
else
  bad "场景 1：正常路径应为绿，实际 exit=$rc"
  tail -5 "$WORK/s1/stdout.txt" 2>/dev/null
fi

# --- 场景 2：package 服务一直没起来 → 必须快速判红并说明原因 ---------------
start_ts=$(date +%s)
rc="$(run_probe no_package_service "$WORK/s2")"
elapsed=$(( $(date +%s) - start_ts ))
if [ "$rc" != "0" ] \
   && grep -q "APK 安装失败" "$WORK/s2/evidence/summary.txt" \
   && [ "$elapsed" -lt 90 ]; then
  ok "场景 2：package 服务未就绪 → 快速判红（${elapsed}s，未空跑）"
else
  bad "场景 2：应快速判红并写明安装失败，实际 exit=$rc 用时 ${elapsed}s"
  tail -5 "$WORK/s2/stdout.txt" 2>/dev/null
fi

# --- 场景 3：install 明确失败 → 必须判红 ---------------------------------
rc="$(run_probe install_fails "$WORK/s3")"
if [ "$rc" != "0" ] && grep -q "APK 安装失败" "$WORK/s3/evidence/summary.txt"; then
  ok "场景 3：install 返回失败 → 判红并写明原因"
else
  bad "场景 3：install 失败应判红，实际 exit=$rc"
fi

# --- 场景 4：焦点始终拿不到 → 必须判红（且不能宣称通过） -------------------
rc="$(run_probe focus_null "$WORK/s4")"
if [ "$rc" != "0" ] && grep -q "焦点未落在" "$WORK/s4/evidence/summary.txt"; then
  ok "场景 4：拿不到前台焦点 → 判红"
else
  bad "场景 4：拿不到焦点应判红，实际 exit=$rc"
fi

# --- 场景 5：判红时不能出现「结论: ✅」（防假绿） --------------------------
if grep -rq "结论: ✅" "$WORK/s2/evidence/summary.txt" "$WORK/s3/evidence/summary.txt" "$WORK/s4/evidence/summary.txt" 2>/dev/null; then
  bad "判红场景里出现了「结论: ✅」（假绿）"
else
  ok "判红场景均未出现「结论: ✅」（无假绿）"
fi

# --- 场景 6：多 display 输出（null 行在前）→ 焦点确实在我们包里，必须绿 -----
# 2026-11-21 CI 实测踩到：dumpsys window 先输出未激活 display 的
# "mCurrentFocus=null"，真正的焦点行在后面。旧逻辑 head -1 取到 null 行，
# 误判「没拿到焦点」→ 白等 10 分钟后判红，而实际焦点一直在我们包里。
rc="$(run_probe focus_multi_display "$WORK/s6")"
if [ "$rc" = "0" ] && grep -q "结论: ✅" "$WORK/s6/evidence/summary.txt"; then
  ok "场景 6：多 display 焦点（null 行在前）→ 绿"
else
  bad "场景 6：焦点行在 null 行之后时应判绿，实际 exit=$rc"
  tail -5 "$WORK/s6/stdout.txt" 2>/dev/null
fi

printf '\n自检结果：通过 %s 项，失败 %s 项\n' "$PASS" "$FAIL"
[ "$FAIL" = "0" ] || exit 1
