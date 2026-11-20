# HubDeck · 自托管服务聚合台

把散落的自托管服务收进一个原生 Android 入口，并借助 Tailscale 让这些服务在**任何网络下**（家里 / 公司 / 蜂窝 / 国外酒店）都能一键直达。

> 本仓库是 **CMP-1「设计一个安卓应用，把自行架设的服务和应用集中起来，相当于统一入口，同时要加上 tailscale 功能」** 的交付物。

---

## 文档索引

> 设计与规范文档已迁出仓库，统一存放在项目文档区 `/home/jack/project-2611-docs/docs/`（见 `AGENTS.md` §5）。

| 文档 | 内容 |
| --- | --- |
| [`design.md`](/home/jack/project-2611-docs/docs/design.md) | **主设计文档**：问题与目标、竞品选型、功能清单、技术架构、数据模型、导入导出格式、安全模型、风险登记册 |
| [`tailscale-integration.md`](/home/jack/project-2611-docs/docs/tailscale-integration.md) | **Tailscale 集成深潜**：能力盘点、五种接入方式对比、7 条 ADR、Android 平台限制与规避、真机验证清单 |
| [`roadmap.md`](/home/jack/project-2611-docs/docs/roadmap.md) | **路线图**：M0–M5 里程碑与验收标准、任务拆分建议、KPI、风险执行表、待 CEO 拍板的决策项 |
| [`android/`](android/) | **代码脚手架**：Gradle + Kotlin + Jetpack Compose 工程，含数据层、Tailscale 客户端、探测器、端点解析等核心实现 |

---

## 一句话设计

**HubDeck = 原生启动器（目录） + Tailscale（通道）**。

- 目录是 HubDeck 的本职：服务、分类、标签、收藏、多个访问地址、健康状态，全部存在本地 Room 数据库里，可导出成人类可读的 `hubdeck.yaml`。
- 通道交给 Tailscale：HubDeck **不做 VPN、不做流量代理**，只在需要时读取本机 tailnet 状态（Local API）、把服务地址切换成 MagicDNS 名字，其余交给系统浏览器（Custom Tab）完成。

这样既满足"统一入口"，又满足"在外访问"，还不会变成一个持有明文凭据的中间人。

---

## 关键设计决策（摘要）

| # | 决策 | 一句话理由 |
| --- | --- | --- |
| 1 | 原生 App，而不是又一个 Web 面板 | 启动快、离线可用、有 Widget、能常驻桌面；且不要求自建服务器 |
| 2 | 不内置 VPN / 不代理流量 | Android 上已有成熟的官方 Tailscale 客户端；代理会带来凭据与合规风险 |
| 3 | Local API secret 是**可选增强** | Android 上第三方 App 读不到官方客户端私有存储 → secret 只能"粘贴一次"，因此核心功能不依赖它 |
| 4 | 服务地址用"多端点 + 语义主机名"建模 | LAN / Tailnet / 公网三套地址共存，切网自动切换，可手动覆盖 |
| 5 | 网络上下文用"多信号 + 用户可锁"判定 | Local API > Wi-Fi SSID > 探测结果，任何单一信号都会失效 |
| 6 | 控制平面 API 只读、scope 最小化 | token 权限远大于 Local API secret，泄露影响必须被限制 |
| 7 | 内嵌 tsnet 延后到 M5 且做可选构建变体 | Go 运行时包体、userspace 栈性能、供应链与官方 App 互斥，收益不匹配 |
| 8 | 数据文件化（YAML） | 可进 Git、可分享、可从 Homarr 导入，配置不锁死在任何一家 |

详见 [`tailscale-integration.md`](/home/jack/project-2611-docs/docs/tailscale-integration.md) 的 ADR-001 ~ ADR-007。

---

## 代码脚手架现状

已实现（核心算法与接口契约）：

- 数据层：Room 实体 / DAO / Repository、领域模型映射
- 配置互通：`hubdeck.yaml` 导出与解析、Homarr YAML 导入与字段映射
- Tailscale：Local API 只读客户端（`/api/v2/status`）、控制平面设备目录客户端、`TailnetState` 映射
- 网络：TCP / HTTP 探测（含"DNS 解析失败 / 需要登录 / 超时"等原因分类）
- 决策逻辑：`EndpointResolver`（选端点）、`EndpointUrlRenderer`（渲染 MagicDNS 地址）、`NetworkContextResolver`（在家/在外判定）
- 安全：`CredentialStore`（Keystore + AES-256-GCM）
- UI 骨架：仪表盘（搜索 / 分类 / 状态徽标）、服务详情（端点切换）、Tailscale 面板、设置（导入导出）
- 单元测试：端点解析、地址渲染、网络上下文判定、Homarr 导入

未完成（属于 M1 起的实现任务）：

- 首次 Gradle 编译（**本环境无 JDK / Android SDK，代码尚未通过编译验证**）
- 添加/编辑服务界面、Widget、导入预览向导、后台探测调度

---

## 下一步

1. 在具备 JDK 17 + Android SDK 的机器上完成首次构建，修正编译问题（roadmap M1 任务 1.1）。
2. 按 [`roadmap.md`](/home/jack/project-2611-docs/docs/roadmap.md) 的 M1 验收标准推进：目录 CRUD → 多地址打开 → 导入导出 → 搜索/分类 → Widget。
3. 带着真实 tailnet 走一遍 [`tailscale-integration.md`](/home/jack/project-2611-docs/docs/tailscale-integration.md) §7 的 V1–V7 验证清单。
