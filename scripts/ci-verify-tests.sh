#!/usr/bin/env bash
# 校验单测真的执行了（防 NO-SOURCE 假绿）。
#
# 背景（2026-11-13 实测）：CI 跑的是 `:app:testDebugUnitTest`，而测试已经搬到
# `:core:data` 等模块 —— 该任务输出 `NO-SOURCE`，于是「绿」但模块单测一次没跑。
# 这个脚本把「有没有真的跑」变成可判定的门禁：
#   1) 存在测试结果 XML（至少 1 个）
#   2) 用例总数 > 0
#   3) 失败数 = 0、错误数 = 0
#   4) 契约要求必须有测试的模块（默认 core/data）确实产出结果
#
# 用法：bash scripts/ci-verify-tests.sh
# 可覆盖：HUBDECK_REQUIRED_TEST_MODULES="core/data core/network"

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

REQUIRED_MODULES="${HUBDECK_REQUIRED_TEST_MODULES:-core/data}"

python3 - "$REQUIRED_MODULES" <<'PY'
import glob, os, sys
import xml.etree.ElementTree as ET

required = [m.strip() for m in sys.argv[1].split() if m.strip()]
files = sorted(glob.glob('android/**/build/test-results/testDebugUnitTest/*.xml', recursive=True))
print(f'→ 单测结果文件数: {len(files)}')
if not files:
    print('::error:::没有任何单测结果文件 —— CI 是 NO-SOURCE 假绿')
    sys.exit(1)

total = failures = errors = skipped = 0
per_module = {}
for f in files:
    try:
        root = ET.parse(f).getroot()
    except Exception as exc:  # noqa: BLE001
        print(f'::error:::解析测试结果失败 {f}: {exc}')
        sys.exit(1)
    t = int(root.get('tests', 0))
    fl = int(root.get('failures', 0))
    er = int(root.get('errors', 0))
    sk = int(root.get('skipped', 0))
    total += t
    failures += fl
    errors += er
    skipped += sk
    module = f.split('/build/')[0]
    per_module[module] = per_module.get(module, 0) + t

print('→ 各模块用例数:', per_module)
print(f'→ 用例总数 {total}，失败 {failures}，错误 {errors}，跳过 {skipped}')

if total == 0:
    print('::error:::单测用例数为 0 —— CI 是假绿')
    sys.exit(1)
if failures or errors:
    print(f'::error:::存在失败/错误用例（失败 {failures}，错误 {errors}）')
    sys.exit(1)

missing = []
for module in required:
    key = f'android/{module}'
    if per_module.get(key, 0) <= 0:
        missing.append(module)
if missing:
    print('::error:::以下模块必须产出单测结果但没有：' + ', '.join(missing))
    sys.exit(1)

print('→ 单测门禁通过')
PY
