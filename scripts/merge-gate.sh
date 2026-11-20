#!/usr/bin/env bash
# HubDeck 合并门禁（替代 branch protection）
#
# 背景（2026-11-19 实测）：
#   仓库是 private + 免费版，GitHub API 对 `branches/main/protection` 返回
#   403 "Upgrade to GitHub Pro or make this repository public to enable this feature"
#   → 拿不到 branch protection，也就无法用平台强制「CI 绿才准合」。
#   本脚本用 Actions API 自己判：目标 SHA 上 required 工作流必须全部 success。
#
# 为什么不用 check-runs API：
#   本仓库 PAT 没有 Checks:Read 权限，GET /commits/{sha}/check-runs 返回 403
#   "Resource not accessible by personal access token"；
#   而 GET /actions/runs?head_sha={sha} 可用 → 用它做判定。
#
# 用法:
#   scripts/merge-gate.sh <PR号>                 # 只检查，退出码 0=可合 1=不可合
#   scripts/merge-gate.sh <PR号> --merge         # 检查通过后直接合（squash）
#   scripts/merge-gate.sh <PR号> --method merge  # 检查通过后用 merge commit 合
#   scripts/merge-gate.sh <PR号> --allow-emulator-fail  # 模拟器红也放行（仅限非里程碑 PR）
#
# 可覆盖环境变量:
#   GH_TOKEN / GITHUB_TOKEN   GitHub token（优先使用）
#   HUBDECK_REQUIRED_WORKFLOWS 必过工作流名，逗号分隔（工作流名可含空格），默认 "CI,CI Emulator"
#   HUBDECK_REPO              owner/repo，默认从 git remote 推导
#
# 退出码:
#   0  门禁通过
#   1  门禁不通过（CI 红 / 缺 run / PR 不可合）
#   2  用法或环境错误

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PR=""
MERGE=0
METHOD="squash"
ALLOW_EMULATOR_FAIL=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --merge) MERGE=1 ;;
    --method) METHOD="${2:-squash}"; shift ;;
    --allow-emulator-fail) ALLOW_EMULATOR_FAIL=1 ;;
    -h|--help) sed -n '2,30p' "${BASH_SOURCE[0]}"; exit 0 ;;
    -*) echo "未知参数：$1" >&2; exit 2 ;;
    *) [ -z "$PR" ] && PR="$1" ;;
  esac
  shift
done

if [ -z "$PR" ]; then
  echo "用法：scripts/merge-gate.sh <PR号> [--merge] [--method squash|merge]" >&2
  exit 2
fi

# ---------------------------------------------------------------- token
TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
if [ -z "$TOKEN" ] && command -v gh >/dev/null 2>&1; then
  TOKEN="$(gh auth token 2>/dev/null || true)"
fi
if [ -z "$TOKEN" ] && [ -d "$REPO_ROOT/.git" ]; then
  # 兜底：从 remote URL 里抠 token（仓库当前就是这种配置）
  REMOTE="$(git -c safe.directory='*' -C "$REPO_ROOT" remote get-url origin 2>/dev/null || true)"
  case "$REMOTE" in
    https://*@*) TOKEN="${REMOTE#https://}"; TOKEN="${TOKEN%%@*}" ;;
  esac
fi
if [ -z "$TOKEN" ]; then
  echo "❌ 找不到 GitHub token：请设 GH_TOKEN，或 gh auth login" >&2
  exit 2
fi

API="https://api.github.com"
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

# git 可能由 root 跑、仓库属 jack → 触发 dubious ownership，一律放宽
git_() { git -c safe.directory='*' -C "$REPO_ROOT" "$@" 2>/dev/null || true; }

# ---------------------------------------------------------------- repo
if [ -z "${HUBDECK_REPO:-}" ]; then
  REMOTE="$(git_ remote get-url origin)"
  HUBDECK_REPO="$(printf '%s' "$REMOTE" | sed -n 's#.*github\.com[:/]\([^/]*\)/\([^/]*\)#\1/\2#p' | sed 's/\.git$//')"
fi
if [ -z "$HUBDECK_REPO" ]; then
  echo "❌ 推导不出仓库名，请设 HUBDECK_REPO=owner/repo" >&2
  exit 2
fi

api() { curl -sS "${AUTH[@]}" "$1"; }

# ---------------------------------------------------------------- PR 基本信息
PR_JSON="$(api "$API/repos/$HUBDECK_REPO/pulls/$PR")"
STATE="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("state",""))' 2>/dev/null || true)"
if [ -z "$STATE" ] || [ "$STATE" = "None" ]; then
  echo "❌ 取不到 PR #$PR（token 权限不足或 PR 不存在）" >&2
  printf '%s' "$PR_JSON" | head -c 200 >&2; echo >&2
  exit 2
fi
HEAD_SHA="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["head"]["sha"])')"
BASE_REF="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["base"]["ref"])')"
HEAD_REF="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["head"]["ref"])')"
MERGEABLE="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("mergeable_state") or "unknown")')"
TITLE="$(printf '%s' "$PR_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin).get("title",""))')"

echo "PR #$PR  $HEAD_REF → $BASE_REF"
echo "  标题：$TITLE"
echo "  head sha：${HEAD_SHA:0:8}"
echo "  mergeable_state：$MERGEABLE"
echo

if [ "$STATE" != "open" ]; then
  echo "❌ PR 不是 open 状态（$STATE）" >&2
  exit 1
fi

# ---------------------------------------------------------------- 取该 SHA 上的 Actions runs
RUNS_JSON="$(api "$API/repos/$HUBDECK_REPO/actions/runs?head_sha=$HEAD_SHA")"
if ! printf '%s' "$RUNS_JSON" | python3 -c 'import json,sys;json.load(sys.stdin)' 2>/dev/null; then
  echo "❌ 取 Actions runs 失败（token 权限不足？）" >&2
  printf '%s' "$RUNS_JSON" | head -c 200 >&2; echo >&2
  exit 2
fi

REQUIRED_RAW="${HUBDECK_REQUIRED_WORKFLOWS:-CI,CI Emulator}"
# 工作流名可能含空格（如 "CI Emulator"）→ 用逗号分隔再切数组
IFS=',' read -r -a REQUIRED <<< "$REQUIRED_RAW"
FAILED=0

for WF in "${REQUIRED[@]}"; do
  WF="$(printf '%s' "$WF" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [ -n "$WF" ] || continue
  if [ "$WF" = "CI Emulator" ] && [ "$ALLOW_EMULATOR_FAIL" = "1" ]; then
    echo "── $WF：已按 --allow-emulator-fail 跳过"
    continue
  fi
  LINE="$(printf '%s' "$RUNS_JSON" | python3 -c "
import json,sys
sha='$HEAD_SHA'
name='$WF'
runs=[r for r in json.load(sys.stdin)['workflow_runs'] if r.get('head_sha')==sha and r.get('name')==name]
if not runs:
    print('NORUN')
else:
    # 取该工作流在这个 sha 上最新的那次
    r=sorted(runs,key=lambda x:x['created_at'])[-1]
    print(f\"{r['status']}|{r['conclusion']}|{r['html_url']}\")
")"
  case "$LINE" in
    NORUN)
      # 该工作流在这个 sha 上没跑过。
      # CI Emulator 从 2026-11-20 起改为「里程碑/手动/tag」才触发，PR 上本就没有 run ——
      # 这不算失败，只是「没验」，由 --allow-emulator-fail 或 HUBDECK_REQUIRED_WORKFLOWS 控制。
      if [ "$WF" = "CI Emulator" ]; then
        echo "⏭️  $WF：未触发（里程碑/手动才跑）—— 跳过"
        continue
      fi
      echo "❌ $WF：head sha 上没有该工作流的 run —— 不可合"
      FAILED=1
      ;;
    completed\|success\|*)
      echo "✅ $WF：success  ${LINE##*|}"
      ;;
    completed\|skipped\|*)
      echo "⚠️  $WF：skipped（被路径过滤掉了）—— 不可合"
      FAILED=1
      ;;
    completed\|None\|*)
      echo "❌ $WF：conclusion=None"
      FAILED=1
      ;;
    completed\|*)
      echo "❌ $WF：结论 ${LINE##*|}（失败）"
      FAILED=1
      ;;
    *)
      echo "⏳ $WF：尚未完成（${LINE%%|*}）—— 不可合"
      FAILED=1
      ;;
  esac
done

echo
if [ "$FAILED" != "0" ]; then
  echo "❌ 门禁不通过：PR #$PR 不可合" >&2
  exit 1
fi

if [ "$MERGEABLE" = "dirty" ]; then
  echo "❌ 门禁通过，但 GitHub 报 mergeable_state=dirty（有冲突）—— 不可合" >&2
  exit 1
fi

echo "✅ 门禁通过：PR #$PR 可合"

if [ "$MERGE" != "1" ]; then
  echo "（加 --merge 才会真的执行合并）"
  exit 0
fi

# ---------------------------------------------------------------- 执行合并
BODY="$(printf '{"merge_method":"%s"}' "$METHOD")"
RES="$(curl -sS -X PUT "${AUTH[@]}" -H "Content-Type: application/json" \
  "$API/repos/$HUBDECK_REPO/pulls/$PR/merge" -d "$BODY")"
MERGED="$(printf '%s' "$RES" | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d.get("merged") or d.get("message") or d)' 2>/dev/null || true)"
if [ "$MERGED" = "True" ]; then
  echo "✅ 已合并：$MERGED"
  exit 0
fi
echo "❌ 合并失败：$MERGED" >&2
exit 1
