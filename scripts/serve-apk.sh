#!/usr/bin/env bash
# 把 debug APK 通过局域网 HTTP 暴露给自己的手机直接下载安装。
#
# 用法:
#   scripts/serve-apk.sh                       # 默认 8099 端口、120 分钟窗口
#   scripts/serve-apk.sh --port 8100 --minutes 60
#   scripts/serve-apk.sh --apk /path/to/other.apk
#
# 说明:
#   - 附件上传上限 10MB，装不下 60MB+ 的隧道版 APK，所以走局域网直下。
#   - 只在本机局域网可达（绑定 0.0.0.0，手机与本机同一 Wi-Fi 即可）。
#   - 窗口到点自动关闭服务；README.txt 与 APK 同目录，内含 sha256 与安装说明。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_APK="$REPO_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"

PORT=8099
MINUTES=120
APK="$DEFAULT_APK"

while [ $# -gt 0 ]; do
  case "$1" in
    --port) PORT="$2"; shift 2 ;;
    --minutes) MINUTES="$2"; shift 2 ;;
    --apk) APK="$2"; shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
done

[ -f "$APK" ] || { echo "APK 不存在：$APK（先跑 scripts/build.sh :app:assembleDebug）" >&2; exit 1; }

DIR="$(cd "$(dirname "$APK")" && pwd)"
BASE="$(basename "$APK")"
SHA="$(sha256sum "$APK" | awk '{print $1}')"
SIZE="$(du -h "$APK" | awk '{print $1}')"

# 取本机局域网 IP（优先 ens19/eth0/wlan0 之类的私有网段地址）
LAN_IP="$(ip -4 addr show scope global 2>/dev/null \
  | awk '/inet /{split($2,a,"/"); print a[1]}' \
  | grep -E '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)' \
  | head -1)"
[ -n "$LAN_IP" ] || LAN_IP="127.0.0.1"

cat > "$DIR/README.txt" <<EOF
HubDeck 调试版 APK（局域网直下）
===============================
文件: $BASE
大小: $SIZE
sha256: $SHA

下载: http://$LAN_IP:$PORT/$BASE

安装（手机与本机同一 Wi-Fi）：
  adb install -r $BASE
  或：浏览器下载后用文件管理器点击安装（需允许「安装未知来源应用」）。

有效期：约 $MINUTES 分钟（到点自动关闭服务）。
EOF

echo "→ 目录: $DIR"
echo "→ sha256: $SHA"
echo "→ 下载地址: http://$LAN_IP:$PORT/$BASE"
echo "→ 窗口: $MINUTES 分钟后自动关闭"

exec timeout "${MINUTES}m" python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$DIR"
