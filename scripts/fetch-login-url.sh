#!/usr/bin/env bash
# 从 logcat 里捞出 tsnet 节点的浏览器授权链接，交给老板点一下。
#
# 用法:
#   scripts/fetch-login-url.sh              # 读当前 adb 设备的 logcat 缓冲区
#   scripts/fetch-login-url.sh --serial emulator-5554
#   scripts/fetch-login-url.sh --follow     # 持续等待新链接（节点启动后才出现）
#
# 说明:
#   - tsnet 的 NodeStatus.loginUrl() 返回形如 https://login.tailscale.com/a/<hash> 的链接。
#   - 建议 App 侧用固定 tag 打这一行，便于精确匹配：
#       Log.i("HubDeckTsnet", "TSNET_LOGIN_URL=" + status.loginUrl())
#   - 本脚本优先匹配上面的 tag；匹配不到时退化为扫全部 login.tailscale.com 链接。
#   - 链接只打印在终端里，不写进任何文档或评论。

set -euo pipefail

SERIAL=""
FOLLOW=0

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --follow) FOLLOW=1; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

ADB_ARGS=()
if [ -n "$SERIAL" ]; then
  ADB_ARGS=(-s "$SERIAL")
fi

extract() {
  # 只取最后一条：节点重启会打印新链接，旧链接已失效。
  adb "${ADB_ARGS[@]}" logcat -d 2>/dev/null \
    | grep -oE 'TSNET_LOGIN_URL=https://[^[:space:]]+' \
    | tail -n 1 \
    | sed 's/^TSNET_LOGIN_URL=//'
}

if [ "$FOLLOW" -eq 1 ]; then
  echo "等待授权链接（Ctrl-C 退出）…" >&2
  while true; do
    url="$(extract || true)"
    if [ -n "$url" ]; then
      echo "$url"
      exit 0
    fi
    sleep 3
  done
fi

url="$(extract || true)"
if [ -z "$url" ]; then
  # 退化为扫所有 login.tailscale.com 链接（App 侧没用固定 tag 时兜底）。
  url="$(adb "${ADB_ARGS[@]}" logcat -d 2>/dev/null \
    | grep -oE 'https://login\.tailscale\.com/[a-z]/[A-Za-z0-9]+' \
    | tail -n 1 || true)"
fi

if [ -z "$url" ]; then
  echo "logcat 里没有授权链接：节点可能还没启动，或 App 侧没打印 loginUrl" >&2
  exit 1
fi

echo "$url"
