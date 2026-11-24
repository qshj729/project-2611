# MyApps（HubDeck）· 项目共享 AGENTS.md

> **唯一正本**：`/home/jack/project-2611/AGENTS.md` —— 随代码进仓、随 commit 走。
> 旧副本（各 agent 工作区下的 `_default/AGENTS.md`）已于 2026-11-24 废弃并删除，**不要再读那个路径**。
> **冲突裁决**：本文件 > 文档区（`/home/jack/project-2611-docs/docs/`）> 角色文件。发现文档与本文件不一致 → 以本文件为准，并留言让技术负责人改文档。
> **维护**：共享规范只在本文件维护，**只有技术负责人（Kai）能改**；角色文件只写自己独有的部分，不复制共享内容。

**目录**

| 部分 | 章节 | 什么时候看 |
| --- | --- | --- |
| 速览 | §0 | **每个任务开始先看这 30 秒** |
| 第一部分 · 开发规范 | §1–§5 | 协作、工作区、构建、交付、纪律 |
| 第二部分 · 设计规范 | §6–§10 | 产品是什么、架构、模块、契约、数据格式 |
| 第三部分 · 重要注意事项 | §11–§13 | 当前阶段、遗留项、风险红线、待拍板 |
| 第四部分 · 引用文档 | §14–§16 | 长尾细则在文档区哪一份哪一节 |

---

## 0. 30 秒速览（每个任务开始先看）

| 项 | 事实 |
| --- | --- |
| 产品 | **MyApps**：给 GeckoView（Firefox 引擎）**套上 Tailscale** 的自用安卓浏览器 —— 一个 App 顶「浏览器 + Tailscale App」两个 |
| 形态 | Kotlin + Compose，minSdk 34 / targetSdk 35，`applicationId = top.quup.myapps`，源码根包 `app`；**自用不上架**，无账号、无服务器、无遥测 |
| 仓库 | `/home/jack/project-2611`（`android/` 是 Gradle 工程），远端 `github.com/qshj729/hubdeck` |
| 当前阶段 | **M1 · 能用**（M0 骨架已完成）。核心未闭合 = **G-1：PAC 没接进引擎 → 内网地址现在打不开** |
| 模块 | 8 个：`:app` + `:core:model` / `:core:data` / `:core:tunnel` + `:feature:browser` / `:feature:home` / `:feature:settings` + `:benchmark` |
| 文档区 | `/home/jack/project-2611-docs/docs/`（design / contracts / plan / delivery / incidents） |
| 交付 | 本地 `bash scripts/local-build.sh` 绿 → push → PR → **证据只认 Actions artifact** |
| 你的活 | 你能碰哪个目录看 §8.2 归属表；改契约看 §9.3 流程；交证据看 §3–§4 |

**三条最容易踩的坑**

1. **别自造浏览器能力**：标签 / 书签 / 下载 / 历史 / 无痕必须用 GeckoView 或 Mozilla 组件实现，**不要自己写**（ADR-201）。
2. **契约本体只有一份**：`InternalService` / `TunnelState` / `SecretStore` 定义在 `:core:model`，别的模块不得重定义同名类型（C-01）。
3. **绿色构建会骗人**：模块 `NO-SOURCE` 也是绿的；收口前确认目标模块 `compileDebugKotlin` 有命中行（§3.4）。

---
---

# 第一部分 · 开发规范（怎么干活）

## 1. 协作协议

### 1.1 等待用户输入
需要用户拍板才能继续时：先在任务下发起提问（建 interaction）并把状态置为待审（`in_review`），然后停手等回复；不要反复轮询催问。

### 1.2 语言
**用中文思考，用中文回复**；评论、提交信息、文档一律中文。

### 1.3 心跳与结案
- 遵循平台的任务心跳流程与结案路径（有任务就开工，没任务就报告待命）。
- 评论遵循公司 `agent-communication-standards`（acs-v2，A 档 ≤ 12 行，四行块：结论 / 变更与证据 / 风险与阻塞 / 下一步）。
- **评论里不贴裸日志或工具输出残渣**；超长内容写进任务文档（`plan` / `spec` / `design` / `test-plan` / `retrospective` / `runbook`）。
- 工单号写成可点链接：`[<工单号>](/<板块>/issues/<工单号>)`。

## 2. 权威工作区与 Git 纪律

### 2.1 工作区

- **唯一权威工作区** = `/home/jack/project-2611`（代码唯一正本）。直接在这里改代码、提交。
- **每个任务有自己的 git worktree**（分支隔离、互不污染）；在权威工作区或自己的 worktree 里正常作业。
- **不要** `git init` / 重新克隆 / `git checkout .` / `git reset --hard` 别人的改动。
- 开工前先跑 `git status` 与 `git log --oneline -3`，确认没有别人的未提交改动。
- 远端 `https://github.com/qshj729/hubdeck.git`；**push 前确认任务是否授权推远端**。

### 2.2 本机环境（每个新 shell 先确认）

- Bash 工具环境里 **`$HOME` 默认可能为空** → 先 `export HOME=/home/jack` 再跑 git。
- 首次遇到 `fatal: detected dubious ownership` → 一次性执行
  `git config --global --add safe.directory /home/jack/project-2611`。
- 本机有 JDK 17（`~/tools/jdk17`）、Android SDK 35（`~/Android/Sdk`）、Gradle 缓存（`~/.gradle`）；
  **不要**再装第二份 JDK / SDK / system-image，**不要**起本地模拟器。

### 2.3 提交纪律

- **只 `git add` 自己负责的目录**（路径级 add）；禁止 `git add -A` / `git add .` / `git checkout .` / `git reset --hard`。
- 一次提交只动一条线的目录；共享仓库的 pre-commit 守卫会拦跨线提交。
- 确实需要跨线（架构级收口）：由 CEO 用 `HUB_DECK_ALLOW_CROSS_MODULE=1 git commit ...`。
- 提交信息用中文：`feat(data): ...` / `feat(ui): ...` / `feat(network): ...` / `fix(build): ...` / `chore(build): ...` / `design(tokens): ...` / `docs(repo): ...`。
- 契约变更的 commit 必须带 `契约 CHG-xxx`（见 §9.3）。

## 3. 构建与验证

### 3.1 本地自检（提交前必过）

```bash
bash scripts/local-build.sh            # 全量：编译 + 单测 + Lint + debug APK
bash scripts/local-build.sh --no-apk   # 只编译 + 单测 + Lint（更快）
```

- 全量约 25 分钟（GeckoView 体积大），命中缓存则秒级；**红了就地修，不要推**。
- **不打 release APK**（R8 在本机要数小时）→ release 只由 CI 出。
- 只构建自己那条线的模块时用 `scripts/build.sh :core:tunnel:testDebugUnitTest` 这类目标，别整树构建。

### 3.2 唯一交付证据 = GitHub Actions

改代码 → 本地自检绿 → 只 add 自己的目录 → commit（中文）→ push 分支 → 开 PR → 等 Actions。

| Workflow | 触发 | 干什么 |
| --- | --- | --- |
| `ci.yml` | 每次 push / PR | 构建 + 单测 + Lint |
| `ci-emulator.yml` | 里程碑 / 手动 / 主分支 | CI 模拟器截图取证（通道 E） |
| `apk.yml` | tag / 手动 | 隧道版 APK（release） |

### 3.3 证据口径

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

### 3.4 绿色构建会骗人

模块 `NO-SOURCE` 也是绿的。收口前必须确认目标模块 `compileDebugKotlin` **有命中行**，
并按 `/home/jack/project-2611-docs/docs/incidents/2026-11-10-m1-hard-kill.md` §6 的 **G1–G9 逐条**给「通过 / 不通过 + 证据」。

### 3.5 验收结论写法

- 结果只允许三种：`✅ 通过` / `❌ 失败`（附错误输出）/ `⛔ 待验证`（写明阻塞项与解除条件）。**只记录真跑出来的**。
- 每条验收至少齐四样：复现命令 / 实际输出（原文不改写）/ 界面或日志证据 / 设备信息。
- 截图命名：`/home/jack/project-2611-docs/docs/shots/<验收项编号>-<场景>-<序号>.png`（如 `1.1-inner-web-01.png`）；截图与 APK **不提交进仓库**。

## 4. 交付流程（九步）

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

## 5. 通用纪律（每次交付都要满足）

1. **完成定义**：按各角色专属定义执行。
2. **证据要求**：每项变更 / 验收配 Actions run 链接 + artifact 名 + 结论行。
3. **交接契约**：接口或实体语义要变，**先在任务下留言给相关 owner**，由技术负责人升 `contracts.md` 版本，再动手（§9.3）。
4. **禁做清单**：
   - 不在代码、测试夹具或文档里写真实凭据；
   - 不擅自改动已公示的实体字段语义；
   - 不本地打 release APK、不下载 JDK / SDK / system-image、不起本地模拟器；
   - 不把任务个人工作文件（任务清单 / todos / 草稿 / 抓取日志等）写进代码仓库 —— 统一落到项目文档区 `/home/jack/project-2611-docs`，并在 `.gitignore` 加忽略项兜底（工作区元数据目录、`logs.zip` 等）。
5. **阻塞与升级**：真阻塞用 `blockedByIssueIds` 或带 owner 的 `unblockDescriptor`，写清下一步；需要跨角色配合时留言给对应 owner。
6. **沟通**：见 §1。
7. **共享规范归属**：只在本文件维护，只有技术负责人能改；角色文件不复制共享内容。

---
---

# 第二部分 · 设计规范（项目长什么样）

## 6. 产品定义

### 6.1 一句话

**MyApps = Firefox 内核的浏览器 + 内置 tailnet 隧道**。公网照常浏览，家里 / 公司的内网服务在同一个 App 里直接打开，
不用再切到系统级 Tailscale App。

### 6.2 目标（可验证）

| 编号 | 目标 | 达成标准 |
| --- | --- | --- |
| G1 | **一个 App 顶两个** | 蜂窝网络下，App 内可直接打开 tailnet 地址，无需启动系统级 Tailscale App |
| G2 | **浏览器该有的都有** | 书签、多标签、下载、历史、无痕、地址栏搜索 —— 全部由引擎提供，不自己造 |
| G3 | **内网导航够快** | 启动即看到 YAML 里的内网服务分组卡片，一次点击进浏览器 |
| G4 | **安全默认** | 不终止 TLS、不存密码、auth key 走 Keystore、不申请 VPN / 定位 / 存储权限 |
| G5 | **零服务器依赖** | 无后端、无账号；断网也能打开浏览器与导航页 |

### 6.3 非目标（明确不做）

- ❌ 服务目录的增删改查 UI、健康探测、分类标签、Widget —— 清单是**只读 YAML**，改文件或改导入即可。
- ❌ 多端点地址模型（LAN / tailnet / public 三套地址）、❌「在家 / 在外」判定 —— 隧道常驻，YAML 里直接写最终地址。
- ❌ Local API / 控制平面客户端（隧道自包含）、❌ Homarr 导入、❌ Serve / Funnel。
- ❌ 容器编排、部署、反向代理、隧道服务。
- ❌ 密码管理器 / 账号体系 / 多用户 / 团队共享。
- ❌ 自研浏览器引擎、❌ fork 完整浏览器 App。
- ❌ 分流 / 全局代理开关 —— 隧道只服务「访问自家 tailnet」这一件事。

## 7. 架构决策（ADR）

| ADR | 决策 | 关键理由 | 代价 / 边界 |
| --- | --- | --- | --- |
| **ADR-201** | 浏览器底座 = **GeckoView**（不重写浏览器） | 书签 / 历史 / 下载 / 无痕引擎自带；支持 PAC 表达「只有 tailnet 走隧道」 | AAR 244MB → 构建慢、APK 大；**无公开代理设置 API**（只能走引擎偏好，G-1） |
| **ADR-202** | 隧道 = **App 内 tsnet userspace 节点 + 本地 HTTP 代理 + PAC** | 不占系统 VPN 槽，可与官方 App 共存；PAC 的 `PROXY…; DIRECT` 保证隧道没连上时公网照常 | tsnet 是非官方包（待评估）；userspace 转发慢于内核 TUN → 只承诺「面板类访问」 |
| **ADR-203** | 内网服务 = **只读 YAML + 静态导航页** | 自用场景不需要数据库 + CRUD + 同步 | 改清单要改文件或走设置页导入 |
| **ADR-204** | 安全姿态 = **转发点，不是内容中间人** | 代理只做 CONNECT 转发，不终止 TLS；auth key 走 Keystore；不申请 VPN / 定位 / 存储权限 | App **确实在流量路径上**（能看到目标域名 / 端口 / 流量特征）→ 「关于」里必须写清楚 |

**被否决的方案**（别再提）：系统 WebView + 自己写浏览器（书签 / 下载全要自己造）；fork Chromium 系浏览器 App（多进程、150MB+）；Firefox for Android 源码 fork（更重、无法定制）；读写官方 Tailscale Local API（第三方 App 读不到私有存储，Android 9+ 读 SSID 还要定位权限）。

## 8. 模块结构与归属

### 8.1 模块与依赖方向

```
:app                装配壳：Application / MainActivity / 导航 / DI / EntryPoint
:core:model         契约本体：InternalService、TunnelState、SecretStore（纯 Kotlin，无 Android）
:core:data          内网服务 YAML 解析 + App 设置（DataStore）+ Keystore 凭据 + 书签存储
:core:tunnel        tsnet 节点生命周期 + 本地 HTTP 代理 + PAC 生成
:feature:browser    GeckoView 浏览器底座（地址栏 / 导航页 / WebView 宿主）
:feature:home       内网服务静态导航页
:feature:settings   设置（隧道授权、清单导入导出、隧道开关、主题）
:benchmark          性能基准占位（M4 接 macrobenchmark）
```

```
:core:model   ← 所有人依赖它，它不依赖任何人
    ↑            ↑
:core:data :core:tunnel
    ↑            ↑
:feature:home  :feature:browser  :feature:settings
    └──────────────┬───────────────┘
                   ↓
                 :app（DI 装配 / 端口适配 / 导航）
```

| 模块 | 允许依赖 | 禁止依赖 |
| --- | --- | --- |
| `:core:model` | 仅 Kotlin 标准库 | 任何 Android SDK / androidx / 其他模块 |
| `:core:data` | `:core:model`（api） | `:core:tunnel`、`:feature:*` |
| `:core:tunnel` | `:core:model`（api）、tsnet AAR | `:core:data`、`:feature:*`、`:app` |
| `:feature:*` | `:core:model`（api）、需要的 `:core:*`（implementation） | 其它 `:feature:*`、`:app` |
| `:app` | 全部 | — |

> **为什么 `:core:tunnel` 不依赖 `:core:data`**：auth key 的**接口**在 `:core:model`，Keystore 实现只在 `:app` 注入 —— 隧道模块因此不碰 Android 存储实现，可在 JVM 单测里注入假实现。

### 8.2 目录归属表（谁能直接改）

* 每个路径前缀**只出现在一行**——没有双重所有权。「所有者」= 可以直接修改该路径下文件的人。
* 别人要改你目录里的东西 → 走 §9.3 流程，由你执行。

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
| 项目文档区 `docs/*.md` | 设计 / 契约 / 计划 / 交付文档 | **技术负责人**（其他线只读；要改先留言） |

## 9. 冻结契约速查（C-01 ~ C-09）

> 完整签名、不变量与变更记录见 `/home/jack/project-2611-docs/docs/contracts.md` §2–§9。**签名以那里为准**，下表是速查。

| 编号 | 契约 | 模块 / 包 | 一句话 | 关键不变量 |
| --- | --- | --- | --- | --- |
| **C-01** | 领域模型 | `:core:model` / `app.core.model` | `InternalService(title,url,icon,group,note)`、`sealed TunnelState`、`interface SecretStore` | 契约本体**唯一**；纯 Kotlin 不带 Android 类型；URL 只允许 http/https |
| **C-02** | YAML 清单 | `:core:data` / `app.core.data.config` | `InternalServicesStore.observe/current/replaceAll/importYaml/exportYaml` + `InternalServicesConfig.parse/render` | 存的是**一段 YAML 文本**不是表；非 http(s) / 空标题进 `rejected`；`render∘parse` 往返无损 |
| **C-03** | App 设置 | `:core:data` / `app.core.data.settings` | `AppSettings(themePreference,autoConnectTunnel,openHomeOnLaunch,useTailnetProxy)` + `SettingsStore` | 语义放纯 Kotlin，DataStore 只读写；新增项必须带默认值（A 级） |
| **C-04** | 凭据（auth key） | 接口 `:core:model` / 实现 `:core:data` `app.core.data.security` | `SecretStore.authKey/saveAuthKey/clearAuthKey/hasAuthKey` | **绝不明文落盘**：Keystore AES-256-GCM（alias `myapps.authkey.v1`）→ SharedPreferences base64；解密失败返回 `null` 不抛异常；导出文件不得含 key |
| **C-05** | 隧道生命周期 | `:core:tunnel` / `app.core.tunnel` | `TunnelManager.startIfConfigured/start/stop/restart` + `TunnelStateProvider.tunnelState/authUrl` | 状态机 `Disconnected→Starting→(Authorizing→)Connected｜Error`；**无 key 就保持 Disconnected**（公网照常）；失败一律转 `TunnelState.Error`，不抛到 UI |
| **C-06** | 本地代理 + PAC | `:core:tunnel` / `app.core.tunnel` | `LocalProxyServer.start/stop` + `PacGenerator.build`（`/proxy.pac`，代理在 `127.0.0.1:1080`） | 只监听本地端口，**不得设 JVM 全局代理属性**；只 CONNECT 转发**不解密 TLS**；node 为空时 tailnet 目标返回 503、其余直连；PAC 必须带 `; DIRECT` 回退 |
| **C-07** | 浏览器底座 | `:feature:browser` / `app.feature.browser` | `GeckoRuntimeProvider.get(context)` + `BrowserScreen(initialUrl, onUrlConsumed)` | GeckoRuntime **单例**；WebView 宿主用 `AndroidView` + `setSession`；只允许 http/https；**不做 JS bridge**；扩展浏览器能力只能用引擎 / Mozilla 组件 |
| **C-08** | 内网导航页 | `:feature:home` / `app.feature.home` | `HomeScreen(servicesStore, onOpenService)` | **只读**，不给 CRUD 入口；分组来自 `group`，缺省「未分组」，组内保持 YAML 顺序；点击只交 URL，不内嵌 WebView |
| **C-09** | 设置页 | `:feature:settings` / `app.feature.settings` | `SettingsScreen(onBack, viewModel)` | 必须能完成授权 / 断开 / 清凭据 / 清单导入导出 / 隧道开关 / 主题；隧道状态**直取 C-05**；清凭据 = **先停隧道再清凭据** |

### 9.1 PAC 路由规则（唯一事实源）

| 目标 | 返回 |
| --- | --- |
| `100.64.0.0/10` | `PROXY 127.0.0.1:1080; DIRECT` |
| `*.ts.net` | `PROXY 127.0.0.1:1080; DIRECT` |
| 单段主机名（MagicDNS 短名） | `PROXY 127.0.0.1:1080; DIRECT` |
| 其余（公网 / LAN） | `DIRECT` |

### 9.2 数据与配置格式

**内网服务清单（YAML，存进 DataStore preferences 的单个 string key）**

```yaml
version: 1
services:
  - title: Jellyfin
    url: https://jellyfin.tailnet-xyz.ts.net
    icon: "🎬"
    group: 媒体
```

- 解析用 kaml（kotlinx.serialization）；只接受 `http://` / `https://`；`version` 缺省 1；未知字段忽略。
- `group` 缺省「未分组」；允许 MagicDNS 短名（`http://jellyfin:8096`）由隧道代理端解析。

**App 设置（DataStore preferences）**

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `theme_preference` | `system` | 跟随系统 / 浅色 / 深色 |
| `auto_connect_tunnel` | `true` | 启动时自动连隧道 |
| `open_home_on_launch` | `true` | 启动时先显示内网导航页 |
| `use_tailnet_proxy` | `true` | 是否让浏览器走 PAC（关掉则全直连） |

### 9.3 契约变更流程

| 级别 | 定义 | 流程 |
| --- | --- | --- |
| **A 级**（非破坏性） | 新增方法 / 新增带默认值的字段 / 新增带默认值的可选参数 / 文案微调 | 线负责人**可直接实现**，但要在任务下留一条告知评论（写明契约编号 + 前后对比） |
| **B 级**（破坏性） | 改签名 / 改返回值类型 / 改默认值 / 删字段 / 改枚举取值 / 改语义 / 改模块归属 | **必须**走四步：① 提案（契约编号 + 现状→期望签名 + 影响面 + 迁移方案）② 技术负责人回复「批准（CHG-xxx）」（**没有「批准（CHG-xxx）」字样不算批准**）③ 所有者实现，commit 带 `契约 CHG-xxx`，旧签名保留一个迭代（`@Deprecated`）④ 留「契约已变更」评论，技术负责人在 `contracts.md` §8 追加一行 |

**速查**：给 `AppSettings` 加带默认值字段 = A；改 `TunnelState` 字段集合 / 改 PAC 路由规则 / 改 `parse` 拒绝策略 / 改状态机 / 加模块 = B。
`:core:model`（契约本体）与 `contracts.md` 本身只有技术负责人能改。

## 10. 技术栈与工具链

| 层 | 选型 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.2.21 / AGP 8.12.0 / Gradle 8.13 / Version Catalog |
| UI | Jetpack Compose + Material 3；ViewModel + StateFlow |
| 浏览器 | GeckoView 147.0.20260212191108（Firefox 引擎，MPL-2.0） |
| 隧道 | `com.klethron:tsnet:0.14.0`（userspace 节点）+ 自建本地 HTTP 代理 |
| 存储 | DataStore preferences（设置 / 清单 / 书签）+ Android Keystore（凭据） |
| YAML / 序列化 | kaml / kotlinx.serialization |
| DI | Hilt 2.56.2（走 KSP，不用 kapt） |
| 测试 | JUnit + kotlinx-coroutines-test + Compose UI Test |

**版本为什么锁这一组**（改动前先想清楚，别随手升）

| 组件 | 版本 | 约束 |
| --- | --- | --- |
| Kotlin | 2.2.21 | 2.3.x 的 metadata（2.3.0）超出 Hilt 2.56.2 支持上限（2.2.0） |
| KSP | 2.2.21-2.0.5 | 必须与 Kotlin 同版本；Hilt 走 KSP |
| Hilt | 2.56.2 | 2.60+ 要求 AGP 9.0 |
| AGP | 8.12.0 | KSP 2.3+ 要求 AGP ≥ 8.12 |
| Gradle | 8.13 | AGP 8.12 要求 ≥ 8.13 |
| GeckoView | 147 | 148+ 用 Kotlin 2.3 metadata，与上面冲突 |

> KSP 与 Hilt 插件必须**声明在同一作用域**（root `build.gradle.kts` 里都 `apply false`），否则 Gradle 报 "KSP plugin … task class could not be found"。

---
---

# 第三部分 · 重要注意事项（别踩这些坑）

## 11. 当前阶段与未闭合项

**阶段**：M0 骨架 ✅ → **M1 · 能用（当前）** → M2 打磨 → M3 评估收口 → M4 收尾。

### 11.1 M1 收口清单

| # | 任务 | 归属 | 验收标准 |
| --- | --- | --- | --- |
| 1.1 | **G-1 PAC 接线到 GeckoView** | 网络线 + 界面线 | 真机 + 蜂窝下直接打开 `https://<host>.tailnet-xxx.ts.net` 成功；公网同时正常（V1 / V2） |
| 1.2 | **G-2 浏览器完整能力** | 界面线 | 多标签 / 书签（增删查）/ 下载 / 历史 / 无痕全部可用，**必须用引擎 API 或 Mozilla 组件** |
| 1.3 | **G-3 隧道状态条** | 界面线 | 未连接 / 连接中 / 已连接 / 需授权 四态可见，点击可授权 |
| 1.4 | 隧道异常兜底 | 网络线 | 隧道断开时浏览器自动回退直连，公网不受影响（V3） |
| 1.5 | G-9 YAML 清单单测 | 数据线 | 合法 / 非法协议 / 空标题 / 分组 / 往返 五类用例 |
| 1.6 | G-8 设置页 UI 测试重写 | 界面线 | Compose UI 测试跑通 |
| 1.7 | G-10 CI 随工具链升级验证 | 工程线 | `ci.yml` 在 Kotlin 2.2 / AGP 8.12 / Gradle 8.13 / GeckoView 147 上绿 |

**M1 出口标准**：**一个 App** 就能在外面打开家里内网服务，浏览器该有的功能都在，且公网浏览不受影响。

### 11.2 已知缺口（G-1 ~ G-10）

| 编号 | 问题 | 归属 | 期望修法 |
| --- | --- | --- | --- |
| **G-1** | **PAC 未接线到 GeckoView**（核心，内网现在打不开） | 网络线 + 界面线 | 引擎偏好（`network.proxy.pac_url` / `network.proxy.type=2`）或 `GeckoSessionSettings` 代理配置；**需 POC 验证** |
| **G-2** | 浏览器完整能力未接线（标签 / 书签 / 下载 / 历史 / 无痕） | 界面线 | GeckoView 原生 API 或 Mozilla android-components，**不自造** |
| **G-3** | 隧道状态条未做 | 界面线 | 浏览器页顶部加状态条（用 C-05 状态） |
| **G-4** | `com.klethron:tsnet` 来源 / 许可未评估 | 技术负责人 | M3 出结论；必要时换官方包或退回「依赖官方 App」模式 |
| **G-5** | 包体与冷启动未实测（debug APK 580MB，含 4 个 ABI 的 native 库） | 工程线 | M3 基准；release 限 arm64-v8a（阈值：包体 +25MB、冷启动 +150ms） |
| **G-6** | 内部标识仍是 `hubdeck.*` | 技术负责人 | 明确改名或保持（倾向保持） |
| **G-7** | `GeckoRuntimeProvider` 未配置 PAC 偏好 | 界面线 | 与 G-1 一并解决 |
| **G-8** | 设置页旧 UI 测试失效 | 界面线 | 重写 |
| **G-9** | YAML 解析缺单测 | 数据线 | 补合法 / 非法协议 / 空标题 / 分组 / 往返用例 |
| **G-10** | CI 未随工具链升级验证 | 工程线 | 推分支跑 `ci.yml` 验证新工具链 |

## 12. 风险与红线

### 12.1 安全姿态（红线）

- **不终止 TLS**：代理只做 CONNECT 转发，App 看不到明文；但 App **确实在流量路径上** → 「关于」里写清楚。
- **不存密码**：引擎的密码保存能力不主动开启。
- **auth key 不落明文**：Keystore AES-256-GCM；不导出任何含 key 的文件；一键清除。
- **权限最小**：只申请 `INTERNET` + `ACCESS_NETWORK_STATE`；**不申请** VPN / 定位 / 存储。
- 数据全本地：无遥测、无账号、无服务器。

### 12.2 构建与磁盘

- `gradle.properties` 已配 `-Xmx2560m` / `-XX:MaxMetaspaceSize=768m`（GeckoView 的 dex 合并需要 ≥2.5G 堆，1G 会 OOM）；本机 5.8GB RAM / 2 核。
- **磁盘不足时只清**：`android/**/build`、`.build-logs/`、Gradle 缓存里可重建的部分。
- **禁止删除**：平台 CLI 目录 `~/.paperclip/cli`（≈3GB）与 Hermes 工具链 `~/.hermes`（≈2.9GB）—— 删了会毁掉本机环境。
- `~/.cache/hubdeck-toolchain` 已于 2026-11-13 删除（≈11GB），**不得恢复**。

### 12.3 工程坑

- **GeckoView 无公开代理 API** → G-1 接线可能踩坑；备选顺序：引擎偏好 → `GeckoSessionSettings` 代理配置 → 必要时退回「本地代理 + 系统浏览器打开」。
- **与官方 Tailscale App 双开可能互相干扰 MagicDNS** → 清单里优先写 FQDN 或 `100.x` IP，**不用短名**；错误文案引导。
- **Private DNS（DoH）开启时 MagicDNS 查询被拦** → 同上，清单写 FQDN / IP，文案引导关闭。
- **userspace 转发慢于内核 TUN** → 只承诺「面板类访问」；大文件建议用系统 App。
- **文档与代码会漂移** → 每次里程碑收口做一次「文档 ↔ 代码」核对；契约变更必须升版。

### 12.4 角色空缺：界面线没人接

`contracts.md` 把 `:feature/browser` / `:feature:home` / `:feature:settings` 归给**界面线**，
但现有角色里**没有实现线 agent**：Iris 是设计师（明令不写 Compose 代码），Juno 是工程线 / QA，Kai 明令不写实现代码。
后果：M1 的 1.1（PAC 接线）、1.2（浏览器完整能力）、1.3（隧道状态条）、1.6（设置页 UI 测试重写）**都落在界面线，目前无默认 owner**。
→ 需要 CEO 指派界面线 owner，或明确由 Kai 兼任；在此之前，这四项任务的实现归属算**待定**，别抢别人的目录。

### 12.5 真机验证清单（V1–V7）

| # | 验证项 | 预期 | 现状 |
| --- | --- | --- | --- |
| V1 | **内网地址在 App 内浏览器打开**（核心） | 页面正常加载，流量经 `127.0.0.1:1080` | ⛔ 待验证（依赖 G-1） |
| V2 | 公网不受影响 | 正常加载（PAC `DIRECT` 回退） | ⛔ 待验证 |
| V3 | 隧道断开后浏览器仍可用 | 公网正常，内网给明确错误 | ⛔ 待验证 |
| V4 | Private DNS 影响 | 记录失败模式并优化文案 | ⛔ 待验证 |
| V5 | 授权流程 | 外部浏览器授权后回 App 自动连上 | ⛔ 待验证 |
| V6 | 包体 / 冷启动 / 隧道启动耗时 | 对照阈值给数字 | ⛔ 待验证（M3） |
| V7 | 转发性能边界 | 与系统 TUN 的差距有量化结论 | ⛔ 待验证（M3） |

> 旧的 V1–V7（Local API 可达性 / 控制平面 scope / `tailscale serve` 形态）随对应能力一起作废。

## 13. 待拍板决策

| # | 决策 | 建议 | 需时点 | 状态 |
| --- | --- | --- | --- | --- |
| D1 | 产品正式名 | MyApps（代码已是 `top.quup.myapps`） | — | ✅ 已定 |
| D2 | 开源协议 | Apache-2.0（无服务端、无 copyleft 顾虑） | M4 前 | ⏔ 待拍板 |
| D3 | APK 分发方式 | 侧载自用（不上架 → 无隐私政策 / 无障碍 / 供应链审查压力） | M1 前 | ⏔ 待拍板 |
| D4 | tsnet 依赖去留 | M3 出数据再定；代码保留切换开关 | M3 | ⏔ 待评估 |
| D5 | 内部标识 `hubdeck.*` 是否改名 | 保持（改名要动 DataStore 与文件格式，收益低） | M3 | ⏔ 待定 |
| D6 | 是否引入 Mozilla android-components | 先用引擎原生 API；确有缺口再引（依赖重、版本耦合紧） | M1 | ⏔ 待定 |

---
---

# 第四部分 · 引用文档（长尾在文档区）

## 14. 文档索引

**统一存放**（不进代码仓库，由技术负责人维护，其他线只读）：

```
/home/jack/project-2611-docs/docs/
```

| 我要查什么 | 看哪份哪一节 |
| --- | --- |
| 产品定义、目标 / 非目标、ADR 全文与被否决方案、附录 C（Tailscale 能力盘点 / Android 平台限制 / V1–V7） | `design.md` §1–§2、附录 C |
| 模块结构、关键数据流（打开内网服务 / 隧道生命周期 / PAC 路由） | `design.md` §3 |
| 数据与配置格式、工具链版本矩阵、构建资源 | `design.md` §4、§6.2–§6.3 |
| 功能范围 P0/P1（F1–F10）、风险登记册 R1–R10、未决问题 | `design.md` §5、§9–§10 |
| 契约**完整签名**、每条契约的全部不变量、模块依赖细则、变更流程全文、变更记录 CHG-xxx、架构冲突 | `contracts.md` §2–§9 |
| 里程碑 M0–M4 全文、当前进度快照、风险执行版（含责任人）、决策待办 D1–D6 | `plan.md` §1–§4 |
| 验收证据规范：截图命名、logcat 截取、结论表、最小证据清单 | `plan.md` §5 |
| 本机环境与自检、交付工作流、证据口径、磁盘纪律 | `delivery.md` §1–§5 |
| 设备验收通道：A 真机 / B Limrun 云模拟器 / E CI 模拟器操作步骤 | `delivery.md` §6 |
| 事故复盘与 G0–G9 门禁 | `incidents/2026-11-10-m1-hard-kill.md` §6 |

**其它位置**

| 位置 | 内容 |
| --- | --- |
| `/home/jack/project-2611-docs/`（根目录） | 任务级资料：PR 草稿、任务清单、抓取日志（见 §5.4） |
| `/home/jack/project-2611-docs/archive-20261122/`、`archive-20261123/` | 历史快照（对齐前 / 四文档合并前），可追溯 |
| `README.md`（仓库内） | 对外说明 + 文档索引 |

> 源码注释与 `scripts/`、`.github/workflows/` 里出现的 `docs/X.md §N` 均指向上方文档区的同名文件；仓库内不再保留 `docs/` 目录。

## 15. 角色职责速查

| 线 | 拥有 | 关键约束 |
| --- | --- | --- |
| **技术负责人（Kai）** | 架构、模块边界、契约裁决、发布就绪判断 | 不写实现代码；不擅自改已公示契约 |
| **数据线（Reed）** | `:core:data`：YAML 清单 / DataStore 设置 / Keystore 凭据 | 不改 `core:tunnel`、`feature/*`；不写真实凭据 |
| **网络线（Vela）** | `:core:tunnel`：tsnet 节点 / 本地代理 / PAC | 不做流量中间人解密；不存 VPN 凭据 |
| **界面线** | `:feature/*`：浏览器底座 / 导航页 / 设置页 | 不自造浏览器能力；不在 App 内做 JS bridge。**⚠️ 当前没有对应角色 agent（见 §12.4）** |
| **工程线（Juno）** | CI、Gradle、测试基线、APK 产出 | 不把「CI 验证不了」写成「已通过」 |
| **设计（Iris）** | 设计令牌、组件规范、交互流程、无障碍标注 | 不写 Compose 实现代码；令牌变更需 Kai 评技术可行性 |

> 每个角色的完整定义、证据要求、禁做清单见各自的角色说明文件（agent 级与 `instructions/` 级的 `AGENTS.md`）。

## 16. 引用方式

在各角色（agent 级与 `instructions/` 级）的 AGENTS.md 文件开头添加：

```markdown
# 引用项目共享规范
见：`/home/jack/project-2611/AGENTS.md`（唯一正本，随代码进仓）

本文件仅包含该角色**独有**的职责、完成定义、禁做清单与参考文档；
共享的协作 / 工作区 / 交付 / 设计规范一律以正本为准，不要在本文件里复制。
```

**共享规范只在本文件维护，只有技术负责人（Kai）能改**；改了共享规范 → 只改本文件 → 同步各角色文件顶部的引用块。
