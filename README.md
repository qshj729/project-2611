# MyApps · 自用的 Tailscale 套壳浏览器

给 [Firefox 引擎（GeckoView）](https://geckoview.dev/) **套上 Tailscale**：
在外面访问家里的内网服务，不再需要同时开「浏览器」和「Tailscale App」两个应用。

> 工作名 **MyApps**（`applicationId = top.quup.myapps`，源码根包 `app`）。
> 设计期代号 HubDeck；工程标识（`hubdeck.yaml` 等）暂时沿用旧名，见 `contracts.md` G-6。

---

## 文档索引

> 设计与规范文档已迁出仓库，统一存放在项目文档区 `/home/jack/project-2611-docs/docs/`（见 `AGENTS.md` §5）。

| 文档 | 内容 |
| --- | --- |
| [`design.md`](/home/jack/project-2611-docs/docs/design.md) | **主设计文档（v2）**：产品定位、架构决策（ADR）、瘦身后的模块结构、数据格式、现状与遗留、风险 |
| [`contracts.md`](/home/jack/project-2611-docs/docs/contracts.md) | **冻结契约与模块归属（v2.0）**：C-01 ~ C-09、模块归属表、依赖方向、变更流程、已知缺口 |
| [`roadmap.md`](/home/jack/project-2611-docs/docs/roadmap.md) | **路线图**：M0 ~ M4、当前进度快照、风险执行表、待拍板决策 |
| [`tailscale-integration.md`](/home/jack/project-2611-docs/docs/tailscale-integration.md) | Tailscale 能力盘点与 ADR 台账（**ADR-001/006/007 已被实现期反转，勿直接照用**） |
| [`ci-only-policy.md`](/home/jack/project-2611-docs/docs/ci-only-policy.md) | **交付口径**：本地自检 + 唯一交付证据 = GitHub Actions |
| [`device-test-channel.md`](/home/jack/project-2611-docs/docs/device-test-channel.md) | 设备通道手册（真机 / 云模拟器 / CI 模拟器） |
| [`acceptance-template.md`](/home/jack/project-2611-docs/docs/acceptance-template.md) | 验收模板 |
| [`incidents/`](/home/jack/project-2611-docs/docs/incidents/) | 事故复盘与 G0–G9 门禁 |

---

## 产品是什么

| 能力 | 来源 |
| --- | --- |
| 书签 / 多标签 / 下载 / 历史 / 无痕 / 地址栏搜索 | **GeckoView（Firefox 引擎）** |
| 内网可达（tailnet 地址） | **App 内置 tsnet 隧道 + 本地 HTTP 代理 + PAC** |
| 内网服务导航页 | **一份 YAML** 渲染的静态卡片页 |
| 凭据（tsnet auth key） | **Android Keystore** 加密 |

不做：服务目录 CRUD、健康探测、多端点地址模型、Widget、账号体系、密码管理、服务器依赖。

---

## 工程结构（瘦身后）

```
android/
├── app/                装配壳：Application / MainActivity / 导航 / DI
├── core/
│   ├── model/          契约本体：InternalService / TunnelState / SecretStore（纯 Kotlin）
│   ├── data/           YAML 清单 / App 设置 / Keystore 凭据 / 书签
│   └── tunnel/         tsnet 节点 + 本地代理（127.0.0.1:1080）+ PAC
├── feature/
│   ├── browser/        GeckoView 浏览器底座
│   ├── home/           内网服务导航页
│   └── settings/       设置
└── benchmark/          性能基准占位（M4 接 macrobenchmark）
```

**技术栈**：Kotlin 2.2.21 · AGP 8.12 · Gradle 8.13 · Compose/Material 3 · Hilt（KSP）· GeckoView 147 · tsnet 0.14.0 · DataStore · kaml

---

## 怎么跑

```bash
# 本地自检（编译 + 单测 + Lint + debug APK）
bash scripts/local-build.sh          # 约 25 分钟（GeckoView 体积大）
bash scripts/local-build.sh --no-apk # 跳过打包，快一些

# CI 是唯一交付证据
# .github/workflows/ci.yml（构建 + 单测 + Lint）
# .github/workflows/apk.yml（APK）
# .github/workflows/ci-emulator.yml（模拟器取证）
```

> **本机构建约束**：GeckoView 的 dex 合并需要 ≥2.5G 堆（`gradle.properties` 已配）；
> 磁盘需留 ≥3GB（GeckoView AAR 244MB + 构建产物）。

---

## 已知缺口（详见 `contracts.md` §4）

| 编号 | 问题 |
| --- | --- |
| **G-1** | **PAC 尚未接线到 GeckoView** —— 隧道与代理就绪，但引擎仍直连，内网打不开（M1 第一任务） |
| G-2 | 浏览器完整能力未接线（多标签 / 书签 / 下载 / 历史 / 无痕） |
| G-3 | 隧道状态条未做 |
| G-4 | `com.klethron:tsnet` 非官方包，来源与许可未评估 |
| G-5 | 包体与冷启动未实测（debug APK 580MB，含 4 个 ABI） |
| G-10 | CI 尚未在新的工具链版本上验证 |

---

## 下一步

1. **M1-1.1**：把 PAC 接到 GeckoView（决定这个 App 到底能不能替代两个 App）。
2. **M1-1.2**：用引擎能力补齐标签 / 书签 / 下载 / 历史 / 无痕（不自造浏览器）。
3. 真机 + 真实 tailnet 走一遍通道 A 验收（`device-test-channel.md` §2）。
4. M3：tsnet 依赖评估 + 包体 / 冷启动基准（`roadmap.md` §1 M3）。
