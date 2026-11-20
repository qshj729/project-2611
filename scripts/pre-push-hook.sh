#!/usr/bin/env bash
# HubDeck pre-push hook：推 Android 源码前必须过本地自检
#
# 安装：ln -sf ../../scripts/pre-push-hook.sh .git/hooks/pre-push
#
# 行为：
#   - 推送内容里**只有** android/ 下的改动时，才跑 scripts/local-build.sh（编译 + 单测 + Lint）
#   - 纯 docs/ scripts/ .github/ 改动 → 跳过本地自检（不白等，也不被别人分支的坏测试拦住）
#   - 本地自检红 → 拦截推送（git push --no-verify 可强制跳过）

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 只在 HubDeck 仓库里生效
if [ ! -f "$REPO_ROOT/scripts/local-build.sh" ]; then
  exit 0
fi

# git 可能由 root 跑、仓库属 jack → 触发 dubious ownership，一律放宽
git_() { git -c safe.directory='*' -C "$REPO_ROOT" "$@" 2>/dev/null; }

# pre-push 的 stdin：<local ref> <local sha> <remote ref> <remote sha>
ZERO="0000000000000000000000000000000000000000"
LOCAL_SHA=""
REMOTE_SHA=""
while read -r _ref LOCAL_SHA _rref REMOTE_SHA; do
  :
done

if [ -z "$LOCAL_SHA" ]; then
  # 拿不到 stdin 信息 → 保守起见直接跑自检
  NEEDS_CHECK=1
else
  if [ "$REMOTE_SHA" = "$ZERO" ]; then
    # 新分支：跟默认远端分支比
    BASE="$(git_ rev-parse --verify --quiet origin/main || true)"
    [ -z "$BASE" ] && BASE="$LOCAL_SHA"
  else
    BASE="$REMOTE_SHA"
  fi
  CHANGED="$(git_ diff --name-only "$BASE" "$LOCAL_SHA" 2>/dev/null || true)"
  if printf '%s\n' "$CHANGED" | grep -q '^android/'; then
    NEEDS_CHECK=1
  else
    NEEDS_CHECK=0
  fi
fi

if [ "$NEEDS_CHECK" != "1" ]; then
  echo "→ pre-push: 本次推送不含 android/ 改动，跳过本地自检"
  exit 0
fi

echo "→ pre-push: 运行本地自检 (scripts/local-build.sh)"

cd "$REPO_ROOT"
bash scripts/local-build.sh
STATUS=$?

if [ "$STATUS" -ne 0 ]; then
  printf '\n❌ 本地自检失败，推送被拦截。\n' >&2
  printf '   请修好再推：git push\n' >&2
  printf '   （如需强制跳过：git push --no-verify）\n' >&2
  exit 1
fi

echo "✅ 本地自检通过，继续推送"
exit 0