# MyApps（HubDeck）· 项目 AGENTS.md

> **本文件是工程说明书 + 权威文档索引。** 读它知道「项目怎么干、谁管哪、证据怎么交、去哪查设计」。
> **权威文档只有两份**：
> - **`design.md`（仓库根）** —— **应用设计方案（权威）**：产品形态、ADR、模块结构、冻结契约 C-01~C-09、数据格式、交付验收口径、里程碑、风险登记册。
> - **`todo.md`（仓库根，只存本地、不入版本库）** —— **进展与待办**：当前阶段、未闭合项、M1 清单、待拍板决策、验收状态。
>
> 原 `design.md` / `contracts.md` / `plan.md` / `delivery.md` 四份文档已合并进 `design.md`（2026-11-25），**不再单独存在、不再被引用**。
> **冲突裁决**：本文件（干活方式）> `design.md`（怎么设计）> `todo.md`（做到哪）。
> **维护**：共享规范只在本文件维护，**只有技术负责人能改**；角色文件只写自己独有的部分，不复制共享内容。
> **本文件只写「基本不变」的东西**（规范 / 索引 / 红线）；进展、待办、决策状态一律在 `todo.md`。

**目录**

| 部分 | 章节 | 什么时候看 |
| --- | --- | --- |
| 速览 | §0 | **每个任务开始先看这 30 秒** |
| 第一部分 · 开发规范 | §1–§4 | 工作区、构建、交付、纪律 |
| 第二部分 · 注意事项 | §5–§7 | 红线、坑、验收基线 |
| 第三部分 · 权威文档索引 | §8–§10 | 要查设计 / 查进展 / 角色归属 |

---

## 0. 30 秒速览（每个任务开始先看）

| 项 | 事实 |
| --- | --- |
| 产品 | **MyApps**：给 GeckoView（Firefox 引擎）**套上 Tailscale** 的自用安卓浏览器 —— 一个 App 顶「浏览器 + Tailscale App」两个 |
| 形态 | Kotlin + Compose，minSdk 34 / targetSdk 35，`applicationId = top.quup.myapps`，源码根包 `app`；**自用不上架**，无账号、无服务器、无遥测 |
| 仓库 | `~/project-2611`（`android/` 是 Gradle 工程），远端 `github.com/qshj729/project-2611` |
| 模块 | 8 个：`:app` + `:core:model` / `:core:data` / `:core:tunnel` + `:feature:browser` / `:feature:home` / `:feature:settings` + `:benchmark` |
| 依赖方向 | `:core:model` ← `:core:data` `:core:tunnel` ← `:feature:*` ← `:app`（只往下，无环） |
| 方案在哪 | **应用设计方案 = 仓库根 `design.md`**（ADR / 契约 / 数据格式 / 交付口径 / 里程碑 / 风险） |
| 进展待办 | **`todo.md`** —— 当前阶段、未闭合项 G-1~G-10、M1 清单、待拍板 D1–D6、验收状态 |
| 交付 | 本地 `bash scripts/local-build.sh` 绿 → push → PR → **证据只认 Actions artifact** |
| 你的活 | 你能碰哪个目录看 §8.2 归属表；改契约看 §8.3 流程；交证据看 §3–§4 |

**三条最容易踩的坑**

1. **别自造浏览器能力**：标签 / 书签 / 下载 / 历史 / 无痕必须用 GeckoView 或 Mozilla 组件实现，**不要自己写**（ADR-201）。
2. **契约本体只有一份**：`InternalService` / `TunnelState` / `SecretStore` 定义在 `:core:model`，别的模块不得重定义同名类型（C-01）。
3. **绿色构建会骗人**：模块 `NO-SOURCE` 也是绿的；收口前确认目标模块 `compileDebugKotlin` 有命中行（§3.4）。

---
---

# 第一部分 · 开发规范（怎么干活）

## 1. 权威工作区与 Git 纪律

### 1.1 工作区

- **唯一权威工作区** = `~/project-2611`（代码唯一正本）。直接在这里改代码、提交。
- **每个任务有自己的 git worktree**（分支隔离、互不污染）；在权威工作区或自己的 worktree 里正常作业。
- **不要** `git init` / 重新克隆 / `git checkout .` / `git reset --hard` 别人的改动。
- 开工前先跑 `git status` 与 `git log --oneline -3`，确认没有别人的未提交改动。
- 远端 `https://github.com/qshj729/project-2611.git`；**push 前确认任务是否授权推远端**。

### 1.2 本机环境（每个新 shell 先确认）

- Bash 工具环境里 **`$HOME` 默认可能为空** → 先 `export HOME="/home/$(id -un)"` 再跑 git。
- 首次遇到 `fatal: detected dubious ownership` → 一次性执行
  `git config --global --add safe.directory ~/project-2611`。
- 本机有 JDK 17（`~/tools/jdk17`）、Android SDK 35（`~/Android/Sdk`）、Gradle 缓存（`~/.gradle`）；
  **不要**再装第二份 JDK / SDK / system-image，**不要**起本地模拟器。

### 1.3 提交纪律

- **只 `git add` 自己负责的目录**（路径级 add）；禁止 `git add -A` / `git add .` / `git checkout .` / `git reset --hard`。
- 一次提交只动一条线的目录；共享仓库的 pre-commit 守卫会拦跨线提交。
- 确实需要跨线（架构级收口）：由项目负责人用 `HUB_DECK_ALLOW_CROSS_MODULE=1 git commit ...`。
- 提交信息用中文：`feat(data): ...` / `feat(ui): ...` / `feat(network): ...` / `fix(build): ...` / `chore(build): ...` / `design(tokens): ...` / `docs(repo): ...`。
- 契约变更的 commit 必须带 `契约 CHG-xxx`（见 §8.3）。

## 2. 构建与验证

### 2.1 本地自检（提交前必过）

```bash
bash scripts/local-build.sh            # 全量：编译 + 单测 + Lint + debug APK（约 25 分钟）
bash scripts/local-build.sh --no-apk   # 只编译 + 单测 + Lint（更快）
```

- **红了就地修，不要推**；命中缓存则秒级。
- **不打 release APK**（R8 在本机要数小时）→ release 只由 CI 出。
- 只构建自己那条线的模块时用 `scripts/build.sh :core:tunnel:testDebugUnitTest` 这类目标，别整树构建。
- 环境细节、内存与磁盘口径见 `design.md` §7.2 与本文件 §5.2。

### 2.2 唯一交付证据 = GitHub Actions

改代码 → 本地自检绿 → 只 add 自己的目录 → commit（中文）→ push 分支 → 开 PR → 等 Actions。

| Workflow | 触发 | 干什么 |
| --- | --- | --- |
| `ci.yml` | 每次 push / PR | 构建 + 单测 + Lint |
| `ci-emulator.yml` | 里程碑 / 手动 / 主分支 | CI 模拟器截图取证（通道 E，手动备用） |
| `apk.yml` | tag / 手动 | 隧道版 APK（release） |

### 2.3 证据口径

| 要证明的事 | 唯一可接受的证据 |
| --- | --- |
| 编译通过 | Actions `ci.yml` 的 build job 绿 |
| 单测通过 | 同一次 run 的 `testDebugUnitTest` 绿 + artifact `hubdeck-reports` |
| Lint 无新增问题 | 同一次 run 的 `lintDebug` 绿 + artifact `hubdeck-reports` |
| APK 产出 | artifact `hubdeck-emulator-apk`（或 `apk.yml` 产物） |
| 界面渲染 / 启动不崩 | artifact `hubdeck-emulator-shots`（`01-launch.png` + `01-launch-ui.xml` + `summary.txt`） |
| 崩溃 / ANR 现场 | 同一 artifact 里的 `logcat-warn.txt` |

- Artifact 四件套：`hubdeck-reports`（单测 / Lint 报告）、`hubdeck-emulator-apk`、`hubdeck-emulator-shots`、`hubdeck-build-logs`。
- 评论写法：**run 链接 + artifact 名 + 一句话结论**；不贴整段构建日志、原始 adb 输出。
- 迁移与往返用例的证据 = Actions run 链接 + `hubdeck-reports` 里的测试报告。
- **不写「本地跑过了」当交付证据** —— 交付证据只认 Actions artifact，本地结果只能用来自查。
- 仍然算「⛔ 待验证」（不许写成通过）：真机深浅色 / 动态字体 / TalkBack 主流程 / 对比度、浏览器完整能力、真实 tailnet 下的 V1–V7。

### 2.4 绿色构建会骗人

模块 `NO-SOURCE` 也是绿的。收口前必须确认目标模块 `compileDebugKotlin` **有命中行**，
并按 `~/project-2611-docs/docs/incidents/2026-11-10-m1-hard-kill.md` §6 的 **G1–G9 逐条**给「通过 / 不通过 + 证据」。

### 2.5 验收结论写法

- 结果只允许三种：`✅ 通过` / `❌ 失败`（附错误输出）/ `⛔ 待验证`（写明阻塞项与解除条件）。**只记录真跑出来的**。
- 每条验收至少齐四样：复现命令 / 实际输出（原文不改写）/ 界面或日志证据 / 设备信息。
- 截图命名：`~/project-2611-docs/docs/shots/<验收项编号>-<场景>-<序号>.png`（如 `1.1-inner-web-01.png`）；截图与 APK **不提交进仓库**。
- 完整验收证据规范（截图命名细则 / logcat 截取 / 结论表格式 / 设备通道手册）见 `design.md` §7.7 与 §7.6。

## 3. 交付流程（九步）

```
① git status / git log --oneline -3      确认没有别人的未提交改动
② 只改自己那条线的目录
③ git add <自己的目录>                   禁止 git add -A / checkout . / reset --hard
④ bash scripts/local-build.sh             本地自检；红了就地修，不要推
⑤ git commit -m "feat(xxx): 中文说明"
⑥ git push origin <分支名>                推远端前确认任务是否授权
⑦ 开 PR（正文贴 CI 链接与证据）
⑧ 等 Actions：ci.yml（构建+单测+Lint）、ci-emulator.yml（模拟器取证，手动）
⑨ 任务评论贴：Actions run 链接 + artifact 名 + 一句话结论
```

## 4. 通用纪律（每次交付都要满足）

1. **完成定义**：按各角色专属定义执行。
2. **证据要求**：每项变更 / 验收配 Actions run 链接 + artifact 名 + 结论行。
3. **交接契约**：接口或实体语义要变，**先在任务下留言给相关 owner**，由技术负责人升 `design.md` §4 契约版本，再动手（§8.3）。
4. **禁做清单**：
   - 不在代码、测试夹具或文档里写真实凭据；
   - 不擅自改动已公示的实体字段语义；
   - 不本地打 release APK、不下载 JDK / SDK / system-image、不起本地模拟器；
   - 不把任务个人工作文件（任务清单 / todos / 草稿 / 抓取日志等）写进代码仓库 —— 统一落到项目文档区 `~/project-2611-docs`，并在 `.gitignore` 加忽略项兜底（工作区元数据目录、`logs.zip` 等）。
5. **阻塞与升级**：真阻塞要写明**阻塞原因 + owner + 下一步**；需要跨角色配合时提前留言给对应 owner，别硬扛。
6. **沟通**：中文思考与回复；结论先行，不贴裸日志或工具输出残渣；超长内容写进任务文档。
7. **共享规范归属**：只在本文件维护，只有技术负责人能改；角色文件不复制共享内容。

---
---

# 第二部分 · 注意事项（别踩这些坑）

## 5. 红线

### 5.1 安全姿态

- **不终止 TLS**：代理只做 CONNECT 转发，App 看不到明文；但 App **确实在流量路径上**（能看到目标域名 / 端口 / 流量特征）→ 「关于」里写清楚。
- **不存密码**：引擎的密码保存能力不主动开启。
- **auth key 不落明文**：Keystore AES-256-GCM（alias `myapps.authkey.v1`）；不导出任何含 key 的文件；一键清除。
- **权限最小**：只申请 `INTERNET` + `ACCESS_NETWORK_STATE`；**不申请** VPN / 定位 / 存储。
- 数据全本地：无遥测、无账号、无服务器。
- 详细条款见 `design.md` §2 ADR-204 与 §4.5 C-04。

### 5.2 构建与磁盘

- `gradle.properties` 已配 `-Xmx2560m` / `-XX:MaxMetaspaceSize=768m`（GeckoView 的 dex 合并需要 ≥2.5G 堆，1G 会 OOM）；本机 5.8GB RAM / 2 核。
- **磁盘不足时只清**：`android/**/build`、`.build-logs/`、Gradle 缓存里可重建的部分。
- **禁止删除**：平台 CLI 缓存目录（≈3GB）、`~/.hermes`（≈2.9GB）—— 平台 CLI 与 Hermes 工具链。
- `~/.cache/hubdeck-toolchain` 已于 2026-11-13 删除（≈11GB），**不得恢复**。

### 5.3 工程坑

- **GeckoView 无公开代理 API** → G-1 接线可能踩坑；备选顺序：引擎偏好 → `GeckoSessionSettings` 代理配置 → 必要时退回「本地代理 + 系统浏览器打开」。
- **与官方 Tailscale App 双开可能互相干扰 MagicDNS** → 清单里优先写 FQDN 或 `100.x` IP，**不用短名**；错误文案引导。
- **Private DNS（DoH）开启时 MagicDNS 查询被拦** → 同上，清单写 FQDN / IP，文案引导关闭。
- **userspace 转发慢于内核 TUN** → 只承诺「面板类访问」；大文件建议用系统 App。
- **文档与代码会漂移** → 每次里程碑收口做一次「文档 ↔ 代码」核对；契约变更必须升版。
- 完整平台限制清单见 `design.md` §11.3。

## 6. 关键数据流（记住这三条就不会改错）

**打开一个内网服务**

```
导航页点卡片 → 读 DataStore 里的 YAML 文本 → InternalService
  → 切浏览器页签 → GeckoSession.loadUri(url) → Gecko 按 PAC 路由
       ├─ tailnet 目标 → 127.0.0.1:1080 → LocalProxyServer → tsnet 隧道
       └─ 其余 → 直连
```

**隧道生命周期**

```
App 启动 → TunnelManager.startIfConfigured()
  ├─ 无 auth key → TunnelState.Disconnected（公网直连，浏览器照常用）
  └─ 有 key → tsnet node.start()
        ├─ NEEDS_LOGIN → TunnelState.Authorizing(loginUrl) → 外部浏览器授权
        ├─ RUNNING → LocalProxyServer.start(node) → TunnelState.Connected
        └─ 出错 / 掉线 → TunnelState.Error → 停代理，浏览器回退直连
```

**PAC 路由规则（唯一事实源）**

| 目标 | 返回 |
| --- | --- |
| `100.64.0.0/10` | `PROXY 127.0.0.1:1080; DIRECT` |
| `*.ts.net` | `PROXY 127.0.0.1:1080; DIRECT` |
| 单段主机名（MagicDNS 短名） | `PROXY 127.0.0.1:1080; DIRECT` |
| 其余（公网 / LAN） | `DIRECT` |

> 完整签名与不变量见 `design.md` §4（C-01~C-09）。

## 7. 真机验收基线（V1–V7）

> 验收项与预期基本不变；**每条的当前状态在 `todo.md`**；方法与判定见 `design.md` §11.4。

| # | 验证项 | 预期 |
| --- | --- | --- |
| V1 | **内网地址在 App 内浏览器打开**（核心） | 页面正常加载，流量经 `127.0.0.1:1080` |
| V2 | 公网不受影响 | 正常加载（PAC `DIRECT` 回退） |
| V3 | 隧道断开后浏览器仍可用 | 公网正常，内网给明确错误 |
| V4 | Private DNS 影响 | 记录失败模式并优化文案 |
| V5 | 授权流程 | 外部浏览器授权后回 App 自动连上 |
| V6 | 包体 / 冷启动 / 隧道启动耗时 | 对照阈值给数字（包体 +25MB / 冷启动 +150ms） |
| V7 | 转发性能边界 | 与系统 TUN 的差距有量化结论 |

---
---

# 第三部分 · 权威文档索引

## 8. 文档地图

```
~/project-2611/                       ← 代码仓库根
├── AGENTS.md                           ← 本文件：工程说明书 + 权威文档索引
├── design.md                           ← ★ 应用设计方案（权威正本，随代码进仓）
├── todo.md                             ← 进展与待办（只存本地，.gitignore 已忽略）
├── android/                            ← Gradle 工程
└── README.md                           ← 对外说明

~/project-2611-docs/                  ← 项目文档区（不进代码仓库）
├── docs/incidents/                     ← 事故复盘（G0–G9 门禁）
├── docs/shots/                         ← 设备验收截图（不进仓库）
├── archive-*/                          ← 历史快照，可追溯
└── （根目录）                          ← 任务级资料：PR 草稿、任务清单、抓取日志
```

### 8.1 我要查什么 → 看哪一节

| 我要查什么 | 去哪查 |
| --- | --- |
| **当前进展 / 待办 / 决策状态 / 验收状态** | `todo.md`（仓库根，只存本地） |
| 产品是什么、目标 / 非目标、ADR 全文与被否决方案 | `design.md` §1–§2 |
| 模块结构、依赖方向、关键数据流 | `design.md` §3 |
| **契约完整签名与全部不变量** | `design.md` §4（C-01~C-09） |
| 契约变更流程、变更记录 CHG-xxx、已知缺口 G-1~G-10 | `design.md` §4.11–§4.13 |
| 数据与配置格式（YAML / 设置项 / 凭据） | `design.md` §5 |
| 功能范围 F1–F10、质量保障现状 | `design.md` §6 |
| 本机环境、交付工作流、证据口径、磁盘纪律 | `design.md` §7.1–§7.5 |
| 设备验收通道（A 真机 / B Limrun / E CI 模拟器）、标准四连 | `design.md` §7.6 |
| 验收证据规范（截图命名 / logcat / 结论表 / 最小证据） | `design.md` §7.7 |
| 里程碑 M0–M4、M1 收口清单 | `design.md` §8 |
| 风险登记册 R1–R10、工具链版本矩阵、技术栈 | `design.md` §9 |
| 未决问题、待拍板决策 D1–D6 | `design.md` §10 / `todo.md` §5 |
| 术语、Tailscale 能力盘点、Android 平台限制、V1–V7 | `design.md` §11 |
| 事故复盘与 G0–G9 门禁 | `docs/incidents/2026-11-10-m1-hard-kill.md` §6 |
| 仓库对外说明 | 仓库内 `README.md` |

> **旧文档说明**：原 `design.md`（产品/ADR/模块）、`contracts.md`（契约）、`plan.md`（里程碑/验收）、`delivery.md`（交付/设备）四份已合并为单一的 `design.md`（2026-11-25）。
> 旧版本留档在 `~/project-2611-docs/archive-20261125/pre-merge-4-docs/`。**源码注释与脚本里出现的 `design.md §N` 均指仓库根的 `design.md`**；`docs/contracts.md`、`docs/plan.md`、`docs/delivery.md` 已不存在。

### 8.2 目录归属表（谁能直接改）

* 每个路径前缀**只出现在一行**——没有双重所有权。「所有者」= 可以直接修改该路径下文件的人。
* 别人要改你目录里的东西 → 走 §8.3 流程，由你执行。

| 路径 | 内容 | 所有者 |
| --- | --- | --- |
| `android/core/model/**` | 领域模型 + 契约本体（C-01） | **技术负责人** |
| `android/core/data/**` | YAML 清单、App 设置、Keystore 凭据、书签存储 | **数据线** |
| `android/core/tunnel/**` | tsnet 节点生命周期、本地代理、PAC（C-05 / C-06） | **网络线** |
| `android/feature/browser/**` | GeckoView 浏览器底座（C-07） | **界面线** |
| `android/feature/home/**` | 内网导航页（C-08） | **界面线** |
| `android/feature/settings/**` | 设置页（C-09） | **界面线** |
| `android/app/src/main/java/app/ui/**` | 导航宿主、主题 | **界面线**（主题）/ **技术负责人**（导航） |
| `android/app/src/main/java/app/di/**` | Hilt 模块、EntryPoint | **技术负责人** |
| `android/app/src/main/java/app/{MainActivity,MyAppsApplication}.kt` | 应用入口 | **技术负责人** |
| `android/app/build.gradle.kts`、`proguard-rules.pro`、`AndroidManifest.xml` | app 模块构建与清单 | **技术负责人** |
| `android/core/*/build.gradle.kts`、`android/feature/*/build.gradle.kts` | 各模块构建脚本 | **技术负责人** |
| `android/settings.gradle.kts`、`android/build.gradle.kts`、`android/gradle/**` | 工程与依赖版本目录 | **技术负责人** |
| `android/gradle.properties` | JVM 内存等构建参数 | **技术负责人** |
| `android/.github/workflows/**` | CI | **工程线** |
| `scripts/**` | 构建 / 自检 / 取证脚本 | **工程线** |
| `README.md`、`AGENTS.md` | 仓库说明、共享规范 | **工程线**（`AGENTS.md` 内容变更需技术负责人拍板） |
| `android/benchmark/**` | 性能基准占位 | **工程线**（M4 接 macrobenchmark） |
| 项目文档区 `docs/incidents/`、`docs/shots/` | 事故复盘、验收截图 | **技术负责人**（其他线只读；要改先留言） |

### 8.3 契约变更流程

| 级别 | 定义 | 流程 |
| --- | --- | --- |
| **A 级**（非破坏性） | 新增方法 / 新增带默认值的字段 / 新增带默认值的可选参数 / 文案微调 | 线负责人**可直接实现**，但要在任务下留一条告知评论（写明契约编号 + 前后对比） |
| **B 级**（破坏性） | 改签名 / 改返回值类型 / 改默认值 / 删字段 / 改枚举取值 / 改语义 / 改模块归属 | **必须**走四步：① 提案（契约编号 + 现状→期望签名 + 影响面 + 迁移方案）② 技术负责人回复「批准（CHG-xxx）」（**没有「批准（CHG-xxx）」字样不算批准**）③ 所有者实现，commit 带 `契约 CHG-xxx`，旧签名保留一个迭代（`@Deprecated`）④ 留「契约已变更」评论，技术负责人在 `design.md` §4.12 追加一行 |

**速查**：给 `AppSettings` 加带默认值字段 = A；改 `TunnelState` 字段集合 / 改 PAC 路由规则 / 改 `parse` 拒绝策略 / 改状态机 / 加模块 = B。
`:core:model`（契约本体）与 `design.md` 本身只有技术负责人能改。
完整流程见 `design.md` §4.11。

## 9. 角色职责速查

| 线 | 拥有 | 关键约束 |
| --- | --- | --- |
| **技术负责人** | 架构、模块边界、契约裁决、发布就绪判断 | 不写实现代码；不擅自改已公示契约 |
| **数据线** | `:core:data`：YAML 清单 / DataStore 设置 / Keystore 凭据 | 不改 `core:tunnel`、`feature/*`；不写真实凭据 |
| **网络线** | `:core:tunnel`：tsnet 节点 / 本地代理 / PAC | 不做流量中间人解密；不存 VPN 凭据 |
| **界面线** | `:feature/*`：浏览器底座 / 导航页 / 设置页 | 不自造浏览器能力；不在 App 内做 JS bridge |
| **工程线** | CI、Gradle、测试基线、APK 产出 | 不把「CI 验证不了」写成「已通过」 |
| **设计线** | 设计令牌、组件规范、交互流程、无障碍标注 | 不写 Compose 实现代码；令牌变更需技术负责人评技术可行性 |

> 各线的划分依据是**模块归属**（见 §8.2），与具体由谁承担无关 —— 换人、并行、增员都不影响这些边界。
> 每个角色的完整定义、证据要求、禁做清单见各自的角色说明文件（agent 级与 `instructions/` 级的 `AGENTS.md`）。

> 每个角色的完整定义、证据要求、禁做清单见各自的角色说明文件（agent 级与 `instructions/` 级的 `AGENTS.md`）。
> **当前各线的实际归属与空缺**（谁在接、谁没人接）看 `todo.md`。

## 10. 引用方式

在各角色（agent 级与 `instructions/` 级）的 AGENTS.md 文件开头添加：

```markdown
# 引用项目共享规范
见：`~/project-2611/AGENTS.md`（唯一正本，随代码进仓）

本文件仅包含该角色**独有**的职责、完成定义、禁做清单与参考文档；
共享的工作区 / 交付 / 设计规范一律以正本为准，不要在本文件里复制。
```

**共享规范只在本文件维护，只有技术负责人能改**；改了共享规范 → 只改本文件 → 同步各角色文件顶部的引用块。
