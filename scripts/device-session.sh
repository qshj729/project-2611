#!/usr/bin/env bash
# HubDeck 设备测试通道助手
#
# 统一三条设备通道的操作，避免 CMP-8（测试基线与验收证据）反复手写 adb / lim 命令：
#   通道 A：USB 或无线调试的真机，走原生 adb
#   通道 B：Limrun 云模拟器，走 lim CLI
#   通道 D：本地模拟器（AVD），同样走原生 adb（序列号形如 emulator-5554），启动见
#           docs/device-test-channel.md §4
#
# 用法:
#   scripts/device-session.sh status              # 探测可用通道 + 打印设备信息
#   scripts/device-session.sh info                # 只打印设备信息
#   scripts/device-session.sh create [标签]       # 通道 B：新建云模拟器实例
#   scripts/device-session.sh install [apk]       # 安装 APK（默认 debug APK）
#   scripts/device-session.sh launch [包名]       # 启动应用（默认 top.quup.myapps.debug）
#   scripts/device-session.sh shot [输出.png]     # 截图
#   scripts/device-session.sh element-tree        # 通道 B：打印 UI 元素树
#   scripts/device-session.sh logcat [行数]        # 抓最近日志
#
# 环境变量:
#   HUB_DECK_PACKAGE     应用包名，默认 top.quup.myapps.debug
#   HUB_DECK_APK         APK 路径，默认 android/app/build/outputs/apk/debug/app-debug.apk
#   HUB_DECK_DEVICE_ID   通道 B 的实例 id（android_<region>_<ulid>），不设则自动取最近一个
#
# 退出码:
#   0 成功 / 1 用法或运行错误 / 3 当前没有可用设备通道

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/hubdeck-env.sh
source "$REPO_ROOT/scripts/hubdeck-env.sh"

APP_PACKAGE="${HUB_DECK_PACKAGE:-top.quup.myapps.debug}"
DEFAULT_APK="$REPO_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"
CHANNEL="none"
SERIAL=""
IS_EMULATOR="no"
LIM_ID="${HUB_DECK_DEVICE_ID:-}"

log()  { printf '%s\n' "$*"; }
warn() { printf '! %s\n' "$*" >&2; }
die()  { printf '✗ %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
}

find_lim() {
  if command -v lim >/dev/null 2>&1; then
    command -v lim
    return 0
  fi
  local candidate
  for candidate in "$HOME"/.nvm/versions/node/*/bin/lim "$HOME"/.local/bin/lim /usr/local/bin/lim; do
    [ -x "$candidate" ] && { printf '%s\n' "$candidate"; return 0; }
  done
  return 1
}

# 探测通道：优先真机 adb，其次 Limrun 云模拟器。
detect_channel() {
  CHANNEL="none"
  SERIAL=""
  IS_EMULATOR="no"
  LIM_ID="${HUB_DECK_DEVICE_ID:-}"

  if command -v adb >/dev/null 2>&1; then
    adb start-server >/dev/null 2>&1 || true
    local line serial state
    while IFS= read -r line; do
      case "$line" in
        "List of devices"*) continue ;;
        "") continue ;;
      esac
      serial="$(printf '%s' "$line" | awk '{print $1}')"
      state="$(printf '%s' "$line" | awk '{print $2}')"
      if [ "$state" = "device" ]; then
        CHANNEL="adb"
        SERIAL="$serial"
        case "$serial" in
          emulator-*) IS_EMULATOR="yes" ;;
        esac
        return 0
      fi
    done <<< "$(adb devices 2>/dev/null)"
  fi

  local lim
  if lim="$(find_lim)"; then
    local listing
    if listing="$("$lim" android list 2>/dev/null)"; then
      CHANNEL="lim"
      if [ -z "$LIM_ID" ]; then
        LIM_ID="$(printf '%s' "$listing" | grep -o 'android_[A-Za-z0-9_]*' | head -1)"
      fi
    fi
  fi
  return 0
}

no_channel_hint() {
  warn "当前没有可用设备通道。"
  warn "  通道 D（本地模拟器）：emulator -avd hubdeck_api34 -no-window -no-audio 启动已建好的 AVD（需 /dev/kvm 权限，或 ANDROID_I_WANT_MY_TCG=yes -accel off 软模拟）。"
  warn "  通道 A（真机）：把开启「USB 调试 / 无线调试」的设备接到本机，adb devices 能看到 device 状态。"
  warn "  通道 B（云模拟器）：lim login --api-key <key> 或 export LIM_API_KEY=<key>，然后 lim android create。"
  warn "详见 docs/device-test-channel.md。"
  exit 3
}

require_channel() {
  detect_channel
  [ "$CHANNEL" != "none" ] || no_channel_hint
}

adb_cmd() {
  if [ -n "$SERIAL" ]; then
    adb -s "$SERIAL" "$@"
  else
    adb "$@"
  fi
}

lim_cmd() {
  local lim
  lim="$(find_lim)" || die "找不到 lim CLI（npm install --global lim）"
  if [ -n "$LIM_ID" ]; then
    "$lim" android "$@" --id "$LIM_ID"
  else
    "$lim" android "$@"
  fi
}

# 打印设备信息（型号 / Android 版本 / 连接方式），用于在 CMP-10 记录通道信息。
print_info() {
  if [ "$CHANNEL" = "adb" ]; then
    local model release sdk abi
    model="$(adb_cmd shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
    release="$(adb_cmd shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
    sdk="$(adb_cmd shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
    abi="$(adb_cmd shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')"
    local conn="USB"
    case "$SERIAL" in
      *:*) conn="无线调试 (adb over TCP)" ;;
    esac
    if [ "$IS_EMULATOR" = "yes" ]; then
      conn="本地模拟器（AVD，adb over TCP）"
      log "通道      : D / 本地模拟器"
    else
      log "通道      : A / adb 真机"
    fi
    log "序列号    : $SERIAL"
    log "连接方式  : $conn"
    log "型号      : ${model:-未知}"
    log "Android   : ${release:-未知} (SDK ${sdk:-未知})"
    log "ABI       : ${abi:-未知}"
  elif [ "$CHANNEL" = "lim" ]; then
    log "通道      : B / Limrun 云模拟器"
    log "实例 id   : ${LIM_ID:-未找到（先 lim android create）}"
    log "连接方式  : Limrun 托管实例（adb 隧道可用 lim android connect 建立）"
  else
    no_channel_hint
  fi
}

cmd_status() {
  detect_channel
  if [ "$CHANNEL" = "none" ]; then
    no_channel_hint
  fi
  print_info
}

cmd_info() {
  require_channel
  print_info
}

cmd_create() {
  local lim tag="${1:-hubdeck}"
  lim="$(find_lim)" || die "找不到 lim CLI（npm install --global lim）"
  "$lim" android create --no-open --no-connect --label "hubdeck=$tag"
}

cmd_install() {
  require_channel
  local apk="${1:-$DEFAULT_APK}"
  [ -f "$apk" ] || die "APK 不存在：$apk（先跑 scripts/build.sh :app:assembleDebug）"
  if [ "$CHANNEL" = "adb" ]; then
    adb_cmd install -r "$apk"
  else
    lim_cmd install-app "$apk"
    warn "install-app 是异步的：稍等几秒再 launch-app（技能说明里的已知行为）"
  fi
}

cmd_launch() {
  require_channel
  local pkg="${1:-$APP_PACKAGE}"
  if [ "$CHANNEL" = "adb" ]; then
    adb_cmd shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null
    log "已启动 $pkg"
  else
    lim_cmd launch-app "$pkg" --detach
    log "已启动 $pkg"
  fi
}

cmd_shot() {
  require_channel
  local out="${1:-$REPO_ROOT/docs/device-screenshot.png}"
  mkdir -p "$(dirname "$out")"
  if [ "$CHANNEL" = "adb" ]; then
    adb_cmd exec-out screencap -p > "$out"
  else
    lim_cmd screenshot "$out"
  fi
  log "截图已保存：$out"
}

cmd_element_tree() {
  require_channel
  if [ "$CHANNEL" = "adb" ]; then
    adb_cmd exec-out uiautomator dump /dev/tty 2>/dev/null | sed 's/></>\n</g'
  else
    lim_cmd element-tree | sed 's/></>\n</g'
  fi
}

cmd_logcat() {
  require_channel
  local lines="${1:-200}"
  if [ "$CHANNEL" = "adb" ]; then
    adb_cmd logcat -d -t "$lines"
  else
    lim_cmd logcat --tail "$lines"
  fi
}

main() {
  local cmd="${1:-status}"
  shift || true
  case "$cmd" in
    status)       cmd_status "$@" ;;
    info)         cmd_info "$@" ;;
    create)       cmd_create "$@" ;;
    install)      cmd_install "$@" ;;
    launch)       cmd_launch "$@" ;;
    shot)         cmd_shot "$@" ;;
    element-tree) cmd_element_tree "$@" ;;
    logcat)       cmd_logcat "$@" ;;
    -h|--help|help) usage ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
