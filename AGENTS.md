# HubDeck Project Shared AGENTS.md

此文件包含所有 HubDeck 项目成员共同遵守的通用规范。各角色专属内容请见各自的 `~/.paperclip/.../agents/{role}/instructions/AGENTS.md`，并在文件开头引用本文件。

---

## 1. 通用协作协议

### 等待用户输入
任何等待用户输入的场景，**必须先读 paperclip skill 的 waiting 章节**，然后建 `interaction` + `PATCH in_review`。

### 语言规范
- **用中文思考，用中文回复**
- 评论、提交信息、文档均使用中文

### Paperclip 心跳与结案
- 遵循 `paperclip` skill 的心跳流程与结案路径
- 评论遵循公司 `agent-communication-standards`（acs-v2，A 档不超过 12 行，四行块：结论 / 变更与证据 / 风险与阻塞 / 下一步）
- 评论里不贴裸日志或工具输出残渣

---

## 2. 权威工作区与 Git 规范

### 唯一代码副本
```
/home/jack/.paperclip/instances/default/projects/f10f9109-c653-4ea3-a4cc-27ab49be3ea5/3e162065-c840-4b27-a56a-5deecd1953be/_default
```
- 你被启动的那个目录**不是**代码副本，不要在那里改代码、不要在那里初始化仓库
- 开工前先执行 `git status` 与 `git log --oneline -3`，确认没有别人的未提交改动
- 远端为 `https://github.com/qshj729/hubdeck.git`；push 前确认任务是否授权推远端

### 共享仓库纪律
- 只 `git add` 自己负责的目录，禁止 `git add -A`、`git checkout .`、`git reset --hard`
- 提交信息用中文，格式：`feat(data): ...` / `chore(build): ...` / `design(tokens): ...` 等

---

## 3. 构建与验证（本地自检 + CI 交付）

**本机有 JDK 17 / Android SDK 35 / Gradle 缓存**（`~/tools/jdk17`、`~/Android/Sdk`、`~/.gradle`），**本地可编译、可跑单测、可跑 Lint**，但**不打 APK**（R8 OOM）。

**提交前必须过本地自检**：`bash scripts/local-build.sh`（编译 + 单测 + Lint，红了就地修，不要推）。

### 唯一交付证据 = GitHub Actions
改代码 → 本地自检绿 → 只 add 自己的目录 → commit（中文）→ push 分支 → 开 PR → 等 Actions：
- `ci.yml`（构建 + 单测 + Lint）
- `ci-emulator.yml`（里程碑/手动/主分支跑，通道 E 模拟器截图取证）
- `apk.yml`（隧道版 APK，tag/手动触发）

### 证据标准
- 证据 = **Actions run 链接 + artifact 名 + 一句话结论**
- Artifacts：`hubdeck-reports`（单测 / Lint 报告）、`hubdeck-emulator-apk`、`hubdeck-emulator-shots`、`hubdeck-build-logs`
- 不要把整段日志贴进评论
- 迁移与往返用例的证据 = Actions run 链接 + `hubdeck-reports` 里的测试报告
- **不写「本地跑过了」当交付证据**——交付证据只认 Actions artifact；本地结果只能用来自查

### 绿色构建会骗人
目标模块 `compileDebugKotlin` 必须有命中行（`NO-SOURCE` 也是绿的），按 `docs/incidents/2026-11-10-m1-hard-kill.md` §6 的 G1–G9 逐条给结论。

### 口径全文
见 `docs/ci-only-policy.md`；设备通道见 `docs/device-test-channel.md` §4E。

---

## 4. 通用纪律（每次交付都要满足）

1. **完成定义**：按各角色专属定义执行
2. **证据要求**：每项变更/验收配对应 Actions run 链接 + artifact 名 + 结论行
3. **交接契约**：接口或实体语义要变，先在任务下留言给相关 owner 升 `docs/contracts.md` 版本，再动手
4. **禁做清单**：不在代码、测试夹具或文档里写真实凭据；不擅自改动已公示的实体字段语义；不本地打 APK、不下载 JDK / SDK / system image、不起本地模拟器
5. **阻塞与升级**：真阻塞用 `blockedByIssueIds` 或带 owner 的 `unblockDescriptor`，并写清下一步；需要跨角色配合时留言给对应 owner
6. **沟通**：见第 1 节

---

## 5. 共同参考文档

- `docs/design.md` — 设计系统、领域模型、仓储契约、无障碍规范
- `docs/contracts.md` — 接口契约版本
- `docs/roadmap.md` — 里程碑与验收标准
- `docs/ci-only-policy.md` — 构建与验收口径
- `docs/acceptance-template.md` — 验收模板
- `docs/device-test-channel.md` — 设备通道手册
- `docs/incidents/` — 事故复盘记录

---

## 6. 引用方式

在各角色的 AGENTS.md 文件开头添加：

```markdown
# 引用项目共享规范
见：`/home/jack/.paperclip/instances/default/projects/f10f9109-c653-4ea3-a4cc-27ab49be3ea5/3e162065-c840-4b27-a56a-5deecd1953be/_default/AGENTS.md`

本文件仅包含该角色**独有**的职责、完成定义、禁做清单与参考文档。
```