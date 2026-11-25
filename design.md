# MyApps · 应用设计方案（权威设计文档）

> **定位**：本项目**唯一的设计权威文档**。产品形态、架构决策、模块结构、冻结契约、数据格式、里程碑、交付与验收口径，全部在这里。
> **入口**：`AGENTS.md`（同目录）—— 工程说明书与本文件的索引（读哪一节、怎么改、归谁管）。
> **状态**：`todo.md`（同目录，只存本地、不入版本库）—— 当前进展、未闭合项、决策待办、验收状态（会变的东西）。
> **维护**：技术负责人维护；其他线只读，要改先留言。
> **位置**：本文件在仓库根，随代码进仓；事故复盘（`incidents/`）与验收截图（`shots/`）仍在项目文档区 `~/hubdeck-docs/docs/`。
> **替代**：本文件整合自原 `design.md` / `contracts.md` / `plan.md` / `delivery.md` 四份文档（2026-11-25 合并），旧文件不再被引用。

---

## 目录

| 章 | 内容 | 谁最常看 |
| --- | --- | --- |
| §1 | 产品定义（目标 / 非目标 / 场景） | 所有人 |
| §2 | 架构决策 ADR-201 ~ ADR-204 | 所有人 |
| §3 | 系统架构（模块 / 数据流 / 依赖方向） | 所有人 |
| §4 | 冻结契约 C-01 ~ C-09 | 各实现线 |
| §5 | 数据与配置格式 | 数据线 / 网络线 |
| §6 | 功能范围与质量保障 | 所有人 |
| §7 | 交付与验收口径 | 工程线 / QA |
| §8 | 里程碑 M0 ~ M4 | 所有人 |
| §9 | 风险登记册 R1 ~ R10 | 所有人 |
| §10 | 未决问题与决策待办 | 技术负责人 |
| §11 | 附录（术语 / Tailscale 要点 / 真机验证 V1–V7） | 验收时 |

---

## 1. 产品定义

### 1.1 一句话

**MyApps = Firefox 内核的浏览器 + 内置 tailnet 隧道**。公网照常浏览，家里 / 公司的内网服务在同一个 App 里直接打开，
不用再切到系统级 Tailscale App。

| 项 | 值 |
| --- | --- |
| 产品名 | MyApps（`applicationId = top.quup.myapps`，源码根包 `app`） |
| 形态 | 自用 Android 应用（Kotlin + Jetpack Compose），minSdk 34 / targetSdk 35 / compileSdk 35 |
| 定位 | **自用工具**，非商业产品；不做多用户 / 账号 / 团队协作 |
| 浏览器底座 | GeckoView（Firefox 引擎）147.0.20260212191108 + Mozilla 官方组件（MPL-2.0） |
| 隧道 | `com.klethron:tsnet:0.14.0`（userspace tailnet 节点）+ 本地 HTTP 代理 + PAC |
| 存储 | DataStore preferences（设置 / 清单 / 书签）+ Android Keystore（凭据） |
| 代码仓库 | `~/hubdeck`（`android/` 为 Gradle 工程） |

### 1.2 使用场景（真实动线）

| 场景 | 动线 | 是否满足 |
| --- | --- | --- |
| 在家刷网页 | 打开 App → 浏览 | ✅ 与普通浏览器无差别 |
| 在外面访问家里 NAS 面板 | 打开 App → 首次授权 → 之后直接访问 `https://nas.tailnet-xxx.ts.net` | ✅ 隧道常驻，无需第二个 App |
| 收藏常用内网服务 | 启动页「内网」页签 → 读 YAML 渲染卡片 → 点一下进浏览器 | ✅ 静态页 + 可点击 |
| 下载文件 | 浏览器内正常下载 | ✅ 引擎原生能力 |
| 多标签 / 历史 / 无痕 | 浏览器原生 | ✅ 引擎原生能力（待接线，见 §6 F5） |

### 1.3 目标（可验证）

| 编号 | 目标 | 达成标准 |
| --- | --- | --- |
| G1 | **一个 App 顶两个** | 蜂窝网络下，App 内可直接打开 tailnet 地址，无需启动系统级 Tailscale App |
| G2 | **浏览器该有的都有** | 书签、多标签、下载、历史、无痕、地址栏搜索 —— 全部由引擎提供，不自己造 |
| G3 | **内网导航够快** | 启动即看到 YAML 里的内网服务分组卡片，一次点击进浏览器 |
| G4 | **安全默认** | 不终止 TLS、不存密码、auth key 走 Keystore、不申请 VPN / 定位 / 存储权限 |
| G5 | **零服务器依赖** | 无后端、无账号；断网也能打开浏览器与导航页 |

### 1.4 非目标（明确不做）

- ❌ 服务目录的增删改查 UI、健康探测、分类标签、Widget —— 清单是**只读 YAML 文件**，改文件或改导入即可。
- ❌ 多端点地址模型（LAN / tailnet / public 三套地址）、❌「在家 / 在外」判定 —— 隧道常驻，不需要判断位置；YAML 里直接写最终地址。
- ❌ Local API / 控制平面客户端（隧道自包含，不需要读官方客户端状态）、❌ Homarr 导入、❌ Serve / Funnel。
- ❌ 容器编排、部署、反向代理、隧道服务。
- ❌ 密码管理器 / 账号体系 / 多用户 / 团队共享。
- ❌ 自研浏览器引擎、❌ fork 完整浏览器 App。
- ❌ 分流 / 全局代理开关：隧道只服务「访问自家 tailnet」这一件事。

---

## 2. 架构决策

### ADR-201：浏览器底座 = GeckoView（不重写浏览器）

**决策**：用 `org.mozilla.geckoview:geckoview` 作为渲染与浏览器能力底座，App 只写「壳」。

**理由**：
1. **能力现成**：书签、历史、下载、无痕、密码提示、Session 恢复都在引擎里；自己写 WebView 浏览器永远追不上。
2. **可控的体积**：GeckoView AAR 244MB（解压后含 omni.ja），最终 APK 体积在 M4 实测；对比 Chromium WebView 方案体积小，但功能要自研；对比 fork 完整浏览器（Chromium 多进程）体积与构建成本都不可接受。
3. **代理能力可用**：Gecko 支持 PAC（Proxy Auto-Config），正好用来表达「只有 tailnet 目标走隧道」。

**代价**：AAR 244MB → 构建慢（本机 dex 合并需 2.5G 堆）、APK 大；GeckoView 没有公开的代理设置 API（只能用偏好设置，见 §10.1）。

**被否决的备选**：

| 备选 | 否决理由 |
| --- | --- |
| 系统 WebView + 自己写 | 「不能重写一个浏览器」—— 标签 / 下载 / 书签全要自己造 |
| fork Chromium 系浏览器 App（Bromite / Kiwi…） | 「太重」：多进程构建、150MB+ |
| Firefox for Android 源码 fork | 更重，且几乎无法定制成「只服务自己的 tailnet」 |

### ADR-202：隧道 = App 内 tsnet userspace 节点 + 本地 HTTP 代理 + PAC

**决策**：
1. App 内跑一个 tsnet（userspace）tailnet 节点 —— 不占系统 VPN 槽，可与官方 Tailscale App 共存。
2. App 内跑一个 HTTP 代理（`127.0.0.1:1080`），把 **tailnet 目标**（`100.64.0.0/10`、`*.ts.net`、MagicDNS 短名）的流量转进隧道，其余直连。
3. 浏览器通过 **PAC**（`http://127.0.0.1:1080/proxy.pac`）自行决定路由 —— App 不参与逐请求判断。

**为什么用 PAC 而不是把代理写死**：
- 隧道没连上时代理端口不可达，PAC 的 `PROXY …; DIRECT` 自动回退直连 → **公网浏览永远可用**，这是体验底线。
- 路由规则集中在一处（`PacGenerator`），可单测。
- 浏览器不需要 App 的参与，App 崩溃 / 隧道断开不影响浏览公网。

**已知代价**：
- tsnet AAR 的来源与许可**尚未评估**（`com.klethron:tsnet` 非 Tailscale 官方包）→ 见 §9 R1，M4 前必须给结论。
- userspace 转发性能低于内核 TUN（大文件 / 长连接场景偏慢）→ 只承诺「面板类访问」。
- 包体与冷启动开销**未实测** → M4 出基准。

### ADR-203：内网服务 = 只读 YAML + 静态导航页

**决策**：内网服务清单是**一份 YAML 文件**（`services.yaml` 形态），App 只读它渲染导航页；用户要改就改文件或在设置里粘贴导入。

**理由**：「顶多出个内网服务的静态页面」—— 自用场景不需要数据库 + CRUD + 同步。

**格式**（`services.yaml`）：

```yaml
version: 1
services:
  - title: Jellyfin
    url: https://jellyfin.tailnet-xyz.ts.net
    icon: "🎬"
    group: 媒体
  - title: Grafana
    url: https://grafana.tailnet-xyz.ts.net
    icon: "📊"
    group: 监控
```

- 只允许 `http://` / `https://`（拒绝 `javascript:` / `file:` / `intent:`）。
- 允许 MagicDNS 短名（`http://jellyfin:8096`）—— 由隧道代理端解析。
- `group` 缺省归入「未分组」。

### ADR-204：安全姿态 —— 转发点，不是内容中间人

| 项 | 结论 |
| --- | --- |
| TLS | 代理只做 CONNECT 转发，**不终止 TLS** → App 看不到明文内容 |
| 但要诚实 | App **确实在流量路径上**（能看到目标域名 / 端口 / 流量特征）→ 必须在「关于」里写清楚 |
| 密码 | 不存；引擎的密码保存能力不主动开启 |
| auth key | Keystore（AES-256-GCM）加密后存 SharedPreferences，**不落明文** |
| 权限 | 只申请 `INTERNET` + `ACCESS_NETWORK_STATE`；不申请 VPN / 定位 / 存储 |
| 数据 | 全部本地；无遥测、无账号、无服务器 |

---

## 3. 系统架构

### 3.1 模块结构

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

依赖方向（只往下，无环）：

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

> **为什么 `:core:tunnel` 不依赖 `:core:data`**：auth key 的**接口**在 `:core:model`（C-01），
> Keystore 实现只在 `:app` 注入 —— 隧道模块因此不碰 Android 存储实现，可在 JVM 单测里注入假实现。

### 3.2 关键数据流

**打开一个内网服务**

```
用户在「内网」导航页点卡片
  │
  ├─ 读 services.yaml（DataStore 里的 YAML 文本）→ InternalService
  │
  └─ 切到「浏览器」页签，把 url 交给 BrowserScreen
        └─ GeckoSession.loadUri(url)
              └─ Gecko 按 PAC 决定路由：
                   ├─ tailnet 目标 → 127.0.0.1:1080 → LocalProxyServer → tsnet 隧道
                   └─ 其它 → 直连
```

**隧道生命周期**

```
App 启动 → TunnelManager.startIfConfigured()
  ├─ SecretStore.authKey()（Keystore）
  ├─ 无 key → TunnelState.Disconnected（公网直连，浏览器照常用）
  └─ 有 key → tsnet node.start()
        ├─ NEEDS_LOGIN → TunnelState.Authorizing(loginUrl) → 外部浏览器完成授权
        ├─ RUNNING → LocalProxyServer.start(node) → TunnelState.Connected
        └─ 出错 / 掉线 → TunnelState.Error → 代理停止，浏览器回退直连
```

**代理路由（PAC）**

| 目标 | 路由 |
| --- | --- |
| `100.64.0.0/10` | `PROXY 127.0.0.1:1080; DIRECT` |
| `*.ts.net` | `PROXY 127.0.0.1:1080; DIRECT` |
| 单段主机名（MagicDNS 短名） | `PROXY 127.0.0.1:1080; DIRECT` |
| 其余（公网 / LAN） | `DIRECT` |

---

## 4. 冻结契约 C-01 ~ C-09

> 本章是各实现线**共同依赖的唯一事实来源**。开工前先读 §4.1 总览与自己的那条；
> 写代码时对着签名逐条对齐，交代码前对着「不变量」自查。
> 完整变更流程见 §4.11。

### 4.1 总览

| 编号 | 契约 | 所在模块 | 包 / 类型 | 调用方 | 所有者 |
| --- | --- | --- | --- | --- | --- |
| C-01 | 领域模型（`InternalService` / `TunnelState` / `SecretStore`） | `:core:model` | `app.core.model` | 全部 | 技术负责人 |
| C-02 | `InternalServicesStore` + `InternalServicesConfig`（YAML 清单） | `:core:data` | `app.core.data.config` | `:feature:home`、`:feature:settings` | 数据线 |
| C-03 | `SettingsStore` + `AppSettings` | `:core:data` | `app.core.data.settings` | `:feature:settings`、`:app` | 数据线 |
| C-04 | `SecretStore` + `KeystoreSecretStore`（auth key） | `:core:model` / `:core:data` | `app.core.model` / `app.core.data.security` | `:core:tunnel`、`:feature:settings` | 数据线 |
| C-05 | `TunnelManager` + `TunnelStateProvider`（隧道生命周期） | `:core:tunnel` | `app.core.tunnel` | `:app`、`:feature:*` | 网络线 |
| C-06 | `LocalProxyServer` + `PacGenerator`（本地代理 + PAC） | `:core:tunnel` | `app.core.tunnel` | `:feature:browser`（PAC URL）、`:app` | 网络线 |
| C-07 | `GeckoRuntimeProvider` + `BrowserScreen`（浏览器底座） | `:feature:browser` | `app.feature.browser` | `:app` | 界面线 |
| C-08 | `HomeScreen`（内网导航页） | `:feature:home` | `app.feature.home` | `:app` | 界面线 |
| C-09 | `SettingsScreen`（设置） | `:feature:settings` | `app.feature.settings` | `:app` | 界面线 |

### 4.2 C-01 领域模型

**归属**：`:core:model` · 包 `app.core.model` · 所有者：技术负责人

```kotlin
data class InternalService(
    val title: String,
    val url: String,            // 完整地址（含 scheme）；允许 MagicDNS 短名
    val icon: String? = null,   // emoji
    val group: String? = null,  // 导航页分组
    val note: String? = null,
) {
    val isSafeUrl: Boolean get() = url.startsWith("http://") || url.startsWith("https://")
}

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Starting : TunnelState
    data class Authorizing(val loginUrl: String) : TunnelState
    data class Connected(
        val nodeName: String,
        val tailnetIps: List<String>,
        val magicDnsSuffix: String? = null,
    ) : TunnelState
    data class Error(val reason: String) : TunnelState
    val isReady: Boolean get() = this is Connected
}

interface SecretStore {
    suspend fun authKey(): String?
    suspend fun saveAuthKey(value: String)
    suspend fun clearAuthKey()
    suspend fun hasAuthKey(): Boolean
}
```

**不变量**

1. **C-01 是唯一的契约本体**：`TunnelState` 只有这一份定义。`:core:tunnel` 与 `:feature:*` 不得自定义同名状态类型。
2. `TunnelState` 不得携带 Android 类型（保持纯 Kotlin，可在 JVM 单测构造）。
3. `InternalService.url` 必须能被 `isSafeUrl` 判定；不满足的条目在**解析层**就被拒绝（见 C-02）。
4. `SecretStore` 是**纯接口**，Android 实现（Keystore）在 `:core:data`；JVM 单测注入假实现。

### 4.3 C-02 内网服务清单（YAML）

**归属**：`:core:data` · 包 `app.core.data.config` · 文件 `InternalServicesConfig.kt`、`InternalServicesStore.kt` · 所有者：数据线

```kotlin
interface InternalServicesStore {
    fun observe(): Flow<List<InternalService>>
    suspend fun current(): List<InternalService>
    suspend fun replaceAll(services: List<InternalService>)
    suspend fun importYaml(text: String): Int   // 返回导入条数；解析失败抛异常
    suspend fun exportYaml(): String
}

data class ServicesParseResult(
    val services: List<InternalService>,
    val rejected: List<RejectedEntry>,   // RejectedEntry(title, url, reason)
)

object InternalServicesConfig {
    fun parse(text: String): ServicesParseResult
    fun render(services: List<InternalService>): String
}
```

**YAML 格式**

```yaml
version: 1
services:
  - title: Jellyfin
    url: https://jellyfin.tailnet-xyz.ts.net
    icon: "🎬"
    group: 媒体
```

**不变量**

1. 存储形式是**一段 YAML 文本**（DataStore preferences 的单个 string key），不是结构化表。
2. 只接受 `http://` / `https://`；其它 scheme 一律进 `rejected`（防 `javascript:` / `file:` / `intent:`）。
3. `title` 为空 → 进 `rejected`。
4. `version` 缺省为 1；未知字段忽略（`kaml` 默认宽容解析由 `Yaml.default` 决定，不得抛异常给用户）。
5. `render` 与 `parse` 必须互为逆运算（往返无损）。

### 4.4 C-03 App 设置

**归属**：`:core:data` · 包 `app.core.data.settings` · 所有者：数据线

```kotlin
data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val autoConnectTunnel: Boolean = true,
    val openHomeOnLaunch: Boolean = true,
    val useTailnetProxy: Boolean = true,
)

interface SettingsStore {
    fun observe(): Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setThemePreference(value: ThemePreference)
    suspend fun setAutoConnectTunnel(value: Boolean)
    suspend fun setOpenHomeOnLaunch(value: Boolean)
    suspend fun setUseTailnetProxy(value: Boolean)
}
```

**不变量**：设置**语义**放在纯 Kotlin 的 `AppSettings`（可 JVM 单测），DataStore 只负责读写；
新增设置项必须是**带默认值**的字段（A 级变更）。

### 4.5 C-04 凭据（tsnet auth key）

**归属**：接口在 `:core:model`（见 C-01），实现 `:core:data` · 包 `app.core.data.security` · 文件 `KeystoreSecretStore.kt` · 所有者：数据线

**不变量**

1. **绝不明文落盘**：auth key 必须用 Keystore 的 AES-256-GCM 密钥加密，密文与 IV 以 base64 存进 SharedPreferences。
2. 密钥只存在于 AndroidKeyStore（alias `myapps.authkey.v1`），App 卸载 / 清数据即失效。
3. 解密失败返回 `null`，**不得抛异常**给调用方。
4. 导出的任何文件**都不得包含** auth key。

### 4.6 C-05 隧道生命周期

**归属**：`:core:tunnel` · 包 `app.core.tunnel` · 文件 `TunnelManager.kt`、`TunnelStateProvider.kt` · 所有者：网络线

```kotlin
class TunnelManager(
    private val context: Context,
    private val secretStore: SecretStore,
    private val localProxyServer: LocalProxyServer,
    private val scope: CoroutineScope,
    private val nodeStarter: ((String) -> TailnetNode?)? = null,  // 单测注入
) : TunnelStateProvider {
    fun startIfConfigured()
    fun start(authKey: String)
    fun stop()
    fun restart(authKey: String)
    override val tunnelState: StateFlow<TunnelState>
    override val authUrl: StateFlow<String?>
}

interface TunnelStateProvider {
    val tunnelState: StateFlow<TunnelState>
    val authUrl: StateFlow<String?>
}
```

**不变量**

1. 状态机：`Disconnected → Starting → (Authorizing →) Connected | Error`；`stop()` 回到 `Disconnected`；出错后允许重试。
2. `startIfConfigured()` 只在**有** auth key 时启动；没有则保持 `Disconnected`（公网直连，浏览器照常可用）。
3. 出错 / 掉线 → 停止本地代理（tailnet 目标返回 503），浏览器回退直连。
4. `nodeStarter` 为 null 时（单测）只驱动状态机，不触碰 native 库。
5. 不得抛异常到 UI；所有失败都转成 `TunnelState.Error(reason)`。

### 4.7 C-06 本地代理与 PAC

**归属**：`:core:tunnel` · 包 `app.core.tunnel` · 文件 `LocalProxyServer.kt`、`PacGenerator.kt` · 所有者：网络线

```kotlin
class LocalProxyServer {
    fun start(context: Context, node: TailnetNode?)  // node 可为 null：PAC 仍可取
    fun stop()
    internal fun isTailnetHost(host: String): Boolean
    internal fun isTailnetIp(host: String): Boolean
}

object PacGenerator {
    const val PAC_PATH = "/proxy.pac"
    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 1080
    fun build(proxyHost: String = PROXY_HOST, proxyPort: Int = PROXY_PORT): String
}
```

**PAC 规则（唯一事实源，浏览器据此路由）**

| 目标 | 返回 |
| --- | --- |
| `100.64.0.0/10` | `PROXY 127.0.0.1:1080; DIRECT` |
| `*.ts.net` | `PROXY 127.0.0.1:1080; DIRECT` |
| 单段主机名（MagicDNS 短名） | `PROXY 127.0.0.1:1080; DIRECT` |
| 其余 | `DIRECT` |

**不变量**

1. 代理只监听 `127.0.0.1:1080`，**不得**设置任何 JVM 全局代理属性（`http.proxyHost` 等）。
2. **CONNECT 隧道不解密 TLS**（只转发字节流）；不得实现 TLS 终止。
3. `node == null` 时：PAC 端点仍返回脚本；tailnet 目标返回 **503**；其余直连。
4. 隧道内连接失败返回 **502**；两种错误都以 HTTP 响应呈现，**不得抛异常到 UI**。
5. `LocalProxyServer.isTailnetHost` 与 PAC 规则必须一致（同一套判定）。
6. PAC 里必须带 `; DIRECT` 回退 —— 隧道没连上时公网浏览不受影响。

### 4.8 C-07 浏览器底座

**归属**：`:feature:browser` · 包 `app.feature.browser` · 文件 `GeckoRuntimeProvider.kt`、`BrowserScreen.kt` · 所有者：界面线

```kotlin
object GeckoRuntimeProvider {
    fun get(context: Context): GeckoRuntime   // 全局单例，必须主线程调用
}

@Composable
fun BrowserScreen(
    initialUrl: String,
    onUrlConsumed: () -> Unit = {},
)
```

**不变量**

1. GeckoRuntime 是**单例**（多实例会重复初始化引擎）。
2. WebView 宿主必须用 `AndroidView` 包裹 `GeckoView`，并 `setSession(...)` 绑定会话。
3. 打开的 URL 只允许 `http/https`（`loadUrl` 里补 scheme 的逻辑不得接受 `javascript:` 等）。
4. **不在 App 内做任何 JS bridge**（不注入 JavaScript 接口）。
5. 浏览器能力的扩展（标签 / 书签 / 下载 / 历史 / 无痕）必须**用引擎或 Mozilla 组件**实现，不得自造浏览器。

### 4.9 C-08 内网导航页

**归属**：`:feature:home` · 包 `app.feature.home` · 文件 `HomeScreen.kt` · 所有者：界面线

```kotlin
@Composable
fun HomeScreen(
    servicesStore: InternalServicesStore,
    onOpenService: (String) -> Unit,   // url → 交给浏览器
    modifier: Modifier = Modifier,
)
```

**不变量**

1. 导航页**只读**：不提供增删改查入口；修改清单走设置页导入 / 导出。
2. 分组来自 `InternalService.group`，缺省为「未分组」；组内保持 YAML 顺序。
3. 点击卡片只把 URL 交给上层（`:app` 切到浏览器页签），**不在导航页内嵌 WebView**。

### 4.10 C-09 设置页

**归属**：`:feature:settings` · 包 `app.feature.settings` · 文件 `SettingsScreen.kt` · 所有者：界面线

```kotlin
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
)
```

**不变量**

1. 设置页必须能完成：隧道授权 / 断开 / 清除凭据、清单导入导出、隧道开关、主题切换。
2. 隧道状态展示必须直接来自 C-05 的 `tunnelState`（不得自造状态）。
3. 清除凭据必须同时清 `SecretStore` 与 `TunnelManager`（顺序：先停隧道再清凭据）。

### 4.11 冻结契约的变更流程

#### 4.11.1 谁可以改

| 改什么 | 谁能直接改 | 需要什么 |
| --- | --- | --- |
| 自己目录内的**实现细节**（不改变冻结签名与语义） | 该目录的所有者 | 无需批准 |
| 冻结契约的**签名 / 默认值 / 语义** | 该契约的所有者**在技术负责人批准后** | B 级流程 |
| `:core:model`（契约本体，C-01） | 技术负责人 | B 级流程；其他线需书面指派 |
| 本设计文档 | 技术负责人 | 其他线只读；要改就在任务下留言 |

> **硬规则**：没有技术负责人书面批准的改动，一律视为越权，发现即回退，并在评论里说明原因。

#### 4.11.2 变更分级

| 级别 | 定义 | 流程 |
| --- | --- | --- |
| **A 级**（非破坏性） | 新增方法 / 新增数据类字段（带默认值）/ 新增可选参数（带默认值）/ 文案微调 | 线负责人**可以直接实现**，但必须在任务下留一条告知评论（写明契约编号 + 前后对比） |
| **B 级**（破坏性） | 改签名 / 改返回值类型 / 改默认值 / 删字段 / 改枚举取值 / 改语义 / 改模块归属 | **必须**走 §4.11.3 的四步流程 |

#### 4.11.3 B 级变更的四步流程

1. **提案**：在任务下留评论，内容必须包含：契约编号（如 `C-05`）；现状签名 → 期望签名；影响面；迁移方案。
2. **裁决**：技术负责人回复 `批准（CHG-xxx）` / `拒绝` / `改为新增方法`。**没有「批准（CHG-xxx）」字样不算批准。**
3. **实现**：被批准后由该契约的所有者实现，commit message 必须包含 `契约 CHG-xxx`。B 级变更应保留旧签名至少一个迭代（`@Deprecated` + 默认实现过渡）。
4. **通知**：实现方留「契约已变更」评论；技术负责人在 §4.12 追加一行。

#### 4.11.4 什么时候必须走流程（速查）

| 场景 | 级别 | 动作 |
| --- | --- | --- |
| 给 `AppSettings` 加一个带默认值的字段 | A | 直接改 + 留言 |
| 改 `TunnelState` 的字段集合 | B | 提案 → 批准 |
| 改 PAC 的路由规则 | B | 提案 → 批准（影响内网可达性） |
| 改 `InternalServicesConfig.parse` 的拒绝策略 | B | 提案 → 批准 |
| 改 `TunnelManager` 的状态机 | B | 提案 → 批准 |
| 加一个自己的模块 | B | 提案 → 批准 |

#### 4.11.5 并行协作的其它硬规则

1. **只构建自己负责的模块**：`scripts/build.sh :core:tunnel:testDebugUnitTest` 之类，别整树构建。
2. **commit 只包含自己的文件**：`git add` 按路径加，不要 `git add .`。
3. **不要顺手改别人的目录**。确实需要改，走 §4.11.3 流程。
4. **接口先行的顺序**：界面层可以用 fake / mock 仓储把骨架做出来，但最终必须按 C-02 ~ C-07 的签名接真实现，否则集成验收会判定漂移。
5. **交叉线程写同一个文件会丢改动**：动别人文件前先 `git status`。

### 4.12 变更记录

| 编号 | 日期 | 契约 | 变更 | 批准人 | 影响线 |
| --- | --- | --- | --- | --- | --- |
| CHG-006 | 2026-11-22 | 全部 | 重构为「Tailscale 套壳浏览器」：作废服务目录 / 探测 / 多端点 / Local API / 手写浏览器等契约；新增 C-01 ~ C-09 | 技术负责人 | 全部 |
| CHG-007 | 2026-11-22 | 工具链 | Kotlin 2.2.21 + KSP 2.2.21-2.0.5 + Hilt 2.56.2 + AGP 8.12 + Gradle 8.13 + GeckoView 147（Hilt 改走 KSP） | 技术负责人 | 全部 |
| CHG-008 | 2026-11-22 | C-04 | auth key 改走 Keystore（AES-256-GCM） | 技术负责人 | 数据线、网络线 |

### 4.13 已知缺口与待修清单

| 编号 | 问题 | 现状 | 归属 | 期望修法 |
| --- | --- | --- | --- | --- |
| **G-1** | **PAC 未接线到 GeckoView** | 隧道与本地代理就绪，但引擎仍直连 → 内网打不开 | 网络线 + 界面线 | 通过引擎偏好 / `GeckoSessionSettings` 代理配置实现；需 POC 验证 |
| **G-2** | 浏览器完整能力未接线 | 只有地址栏 + 单页 + 导航页；标签 / 书签 / 下载 / 历史 / 无痕缺失 | 界面线 | 用 GeckoView 原生 API 或 Mozilla android-components 实现（**不自造**） |
| **G-3** | 隧道状态条未做 | 用户看不到隧道是否已连接 | 界面线 | 在浏览器页顶部加状态条（C-05 状态） |
| **G-4** | `com.klethron:tsnet` 来源与许可未评估 | 非 Tailscale 官方包 | 技术负责人 | M3 出结论；必要时换官方包或退回「依赖官方 App」模式 |
| **G-5** | 包体与冷启动未实测 | debug APK 580MB（含 4 个 ABI 的 GeckoView native 库） | 工程线 | M3 基准；release 限 arm64-v8a |
| **G-6** | 内部标识仍是 `hubdeck.*` | 与产品名 MyApps 不一致 | 技术负责人 | 明确改名或保持 |
| **G-7** | `GeckoRuntimeProvider` 未配置 PAC 偏好 | 见 G-1 | 界面线 | 与 G-1 一并解决 |
| **G-8** | 设置页旧 UI 测试失效 | `SettingsScreenTest` 引用已删除的 API | 界面线 | 重写 |
| **G-9** | YAML 解析缺单测 | C-02 只有实现没有测试 | 数据线 | 补合法 / 非法协议 / 空标题 / 分组 / 往返用例 |
| **G-10** | CI 未随工具链升级验证 | 本地绿，CI 未跑过新工具链 | 工程线 | 推分支跑 `ci.yml` 验证（Kotlin 2.2 / AGP 8.12 / Gradle 8.13 / GeckoView 147） |

---

## 5. 数据与配置

### 5.1 内网服务清单（YAML）

- 存储：DataStore preferences 的**一个 string key**（YAML 原文）。
- 解析：kaml（kotlinx.serialization）。
- 校验：只接受 `http/https`；标题为空或协议非法 → 拒绝并计入提示。
- 导入 / 导出：设置页粘贴 YAML / 导出 YAML 文本。

### 5.2 App 设置（DataStore preferences）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `theme_preference` | `system` | 跟随系统 / 浅色 / 深色 |
| `auto_connect_tunnel` | `true` | 启动时自动连隧道 |
| `open_home_on_launch` | `true` | 启动时先显示内网导航页 |
| `use_tailnet_proxy` | `true` | 是否让浏览器走 PAC（关掉则全直连） |

### 5.3 凭据（Keystore）

| 凭据 | 存储 | 备注 |
| --- | --- | --- |
| tsnet auth key | Keystore AES-256-GCM 加密 → SharedPreferences base64 | 密钥不出 Keystore |
| LocalAPI secret / OAuth token | — | 不需要（隧道自包含） |

---

## 6. 功能范围与质量保障

### 6.1 功能范围（P0 / P1）

| ID | 功能 | 优先级 | 状态 |
| --- | --- | --- | --- |
| F1 | GeckoView 底座：单页浏览、地址栏、前进 / 后退 / 刷新 | P0 | ✅ 骨架完成 |
| F2 | 内嵌 tsnet 隧道 + 本地代理 + PAC | P0 | ⚠️ 代理 / 隧道完成，**PAC 未接线到引擎**（G-1） |
| F3 | 内网静态导航页（YAML → 分组卡片） | P0 | ✅ 完成 |
| F4 | 设置：隧道授权 / 清单导入导出 / 隧道开关 / 主题 | P0 | ✅ 完成 |
| F5 | 浏览器完整能力：多标签、书签、下载、历史、无痕 | P0 | ❌ 待接线（用 GeckoView / Mozilla 组件，**不要自己写**） |
| F6 | 隧道状态条（未连接 / 连接中 / 已连接 / 需授权） | P1 | ❌ 待做 |
| F7 | 书签与导航页合并（把书签也放进首页） | P1 | ❌ 待评估 |
| F8 | 无痕模式 | P1 | ❌ 待做 |
| F9 | 桌面 Widget（快捷进内网） | P2 | ❌ 不做 |
| F10 | 控制平面设备目录（从 tailnet 设备建卡片） | P2 | ❌ 不做 |

> **F5 是本产品的核心价值**，实现方式必须是「用引擎 / Mozilla 组件」，不是「自己写」。

### 6.2 质量保障

| 层 | 内容 | 现状 |
| --- | --- | --- |
| 单元测试 | PAC 规则、代理路由判断、隧道状态机、YAML 解析与校验 | ✅ 已有（18 条）；YAML 解析测试待补（G-9） |
| 集成测试 | MockWebServer 模拟隧道授权响应 | ❌ 待补 |
| UI 测试 | Compose：导航页渲染、地址栏交互、设置项 | ❌ 待补（settings 的旧 UI 测试已失效，需重写，G-8） |
| 真机验收 | 真实 tailnet 下打开内网服务（通道 A） | ❌ 待做 |
| 静态检查 | ktlint / detekt | ❌ 尚未接入 |
| CI | `ci.yml`（构建 + 单测 + Lint）、`ci-emulator.yml`、`apk.yml` | ✅ 存在，需随工具链升级验证（G-10） |

---

## 7. 交付与验收口径

### 7.1 一句话

**本地必须先自查再提交；交付物（APK）与验收证据，只有 GitHub Actions 的结果算数。**

### 7.2 本地环境与自检

| 项 | 值 | 说明 |
| --- | --- | --- |
| JDK | `~/tools/jdk17`（Temurin 17.0.21） | **不要**再装第二份 |
| Android SDK | `~/Android/Sdk`（platform 35 + build-tools 35.0.1） | **不要**再下 system-image |
| Gradle | wrapper 8.13（`~/.gradle/wrapper/dists`） | wrapper 自带 10s 超时，本机慢网必超时 → `scripts/local-build.sh` 用 curl 兜底 |
| 入口 | `scripts/local-build.sh` | 编译 + 单测 + Lint + debug APK（约 25 分钟，GeckoView 体积大） |
| 内存 | `gradle.properties` 已配 2560m 堆 / 768m metaspace | GeckoView 的 dex 合并需要 ≥2.5G 堆，否则 OOM |
| 磁盘 | 需留 ≥3GB | GeckoView AAR 244MB + 构建产物 |

```bash
bash scripts/local-build.sh            # 全量自检（含 APK）
bash scripts/local-build.sh --no-apk   # 只编译 + 单测 + Lint（更快）
```

**本地自检的边界**：本地绿 ≠ 交付绿。本地只证明「编译 / 单测 / Lint 逻辑没问题」，交付证据（APK、真机截图）仍然只认 Actions 的 artifact。

### 7.3 交付工作流

```
① git status / git log --oneline -3     确认没有别人的未提交改动
② 只改自己那条线的目录
③ git add <自己的目录>                  禁止 git add -A / checkout . / reset --hard
④ bash scripts/local-build.sh            本地自检；红了就地修，不要推
⑤ git commit -m "feat(xxx): 中文说明"
⑥ git push origin <分支名>               推远端前确认任务是否授权
⑦ 开 PR（正文贴 CI 链接与证据）
⑧ 等 Actions：ci.yml（构建+单测+Lint）、ci-emulator.yml（模拟器取证，手动）
⑨ 任务评论贴：Actions run 链接 + artifact 名 + 一句话结论
```

**跨线提交**：一次提交只动一条线的目录（守卫会拦）。确实需要跨线（例如架构级收口）时，由项目负责人用 `HUB_DECK_ALLOW_CROSS_MODULE=1 git commit ...`。

### 7.4 证据口径

| 要证明的事 | 唯一可接受的证据 |
| --- | --- |
| 编译通过 | Actions `ci.yml` 的 build job 绿 |
| 单测通过 | 同一次 run 的 `testDebugUnitTest` 绿 + artifact `hubdeck-reports` |
| Lint 无新增问题 | 同一次 run 的 `lintDebug` 绿 + artifact `hubdeck-reports` |
| APK 产出 | artifact `hubdeck-emulator-apk`（或 `apk.yml` 的产物） |
| 界面渲染 / 启动不崩 | artifact `hubdeck-emulator-shots`（`01-launch.png` + `01-launch-ui.xml` + `summary.txt`） |
| 崩溃 / ANR 现场 | 同一 artifact 里的 `logcat-warn.txt`（`summary.txt` 会写有无 FATAL EXCEPTION / ANR） |

**评论写法**：贴 run 链接 + artifact 名 + 一句话结论。不贴整段构建日志、原始 adb 输出、二进制截图。

**仍然算「待验证」**（不许写成通过）：真机上的深浅色 / 动态字体 / TalkBack 主流程 / 对比度、浏览器完整能力（标签 / 书签 / 下载）、真实 tailnet 下的 V1–V7。CI 模拟器结论要显式标注「CI 模拟器结论」。

**绿色构建会骗人**：模块 `NO-SOURCE` 也是绿的。收口前必须确认目标模块 `compileDebugKotlin` 有命中行，并按 `incidents/2026-11-10-m1-hard-kill.md` §6 的 G1–G9 逐条给「通过 / 不通过 + 证据」。

### 7.5 纪律红线与磁盘口径

1. 禁止本地构建 **release APK**（R8 在本机要数小时）；release 只由 CI 出。
2. 禁止装第二份 JDK / Android SDK / system-image；禁止起本地模拟器。
3. 禁止 `git add -A` / `git checkout .` / `git reset --hard`；共享仓库 pre-commit 守卫会拦跨线提交。
4. 禁止把别的线的目录混进自己的提交。
5. 禁止用「本地跑过了」当**交付**证据。
6. 禁止删 `~/.gradle` / `~/tools/jdk17` / `~/Android/Sdk` 里的东西（本地自检的前提）。

**磁盘**：只清本地编译环境这一类（项目 `android/**/build`、`.build-logs/`、Gradle 缓存里可重建的部分）。
**禁止删除**：`平台 CLI 缓存目录`（≈3GB）、`~/.hermes`（≈2.9GB）—— 那是平台 CLI 与 Hermes 工具链。
`~/.cache/hubdeck-toolchain` 已于 2026-11-13 删除（≈11GB），**不得恢复**。

> GeckoView 让构建产物显著变大（AAR 244MB、debug APK 580MB）：磁盘不足时优先清 `android/**/build` 与 `.build-logs/`，不要动上面的禁删目录。

### 7.6 设备验收通道

#### 7.6.1 通道状态

| 通道 | 工具侧 | 外部依赖 | 状态 |
| --- | --- | --- | --- |
| **A. 真机 adb** | ✅ adb / platform-tools 已装 | 一台开启 USB / 无线调试的 Android 14+ 设备 | 🟡 **主设备通道**：由人工接真机，操作走 `scripts/device-session.sh` |
| **B. Limrun 云模拟器** | ✅ `lim` CLI 已装 | Limrun 账号 + API key | ❌ **缺凭据**（`lim android list` 报 `Not authenticated`） |
| **C. 无设备** | ✅ GitHub Actions | 无 | ✅ **可用**：单测 + Lint + APK 产出全部在 CI |
| **D. 本地 AVD** | ❌ 已下线（2026-11-13） | — | ⛔ **不再使用**（本机不编译、不起模拟器） |
| **E. CI 模拟器** | ✅ `ci-emulator.yml` | runner 分钟数 | 🟠 **手动备用**（`workflow_dispatch`）；托管 runner 无 `/dev/kvm`，只有 TCG 软模拟，慢且不稳 |

> **2026-11-21 决策**：设备验收改由**真机（通道 A）**承担，通道 E 不再随推送自动跑。深浅色 / 动态字体 / TalkBack 的结论**只认真机**。

#### 7.6.2 标准动作（通道 A / B / E 共用）

```bash
scripts/build.sh :app:assembleDebug          # 产出 app-debug.apk
scripts/device-session.sh install             # adb install -r
scripts/device-session.sh launch              # 启动 top.quup.myapps.debug
scripts/device-session.sh shot 01-home.png    # 截图
scripts/device-session.sh element-tree        # uiautomator dump，语义断言用
scripts/device-session.sh logcat 300          # 崩溃 / ANR 现场
```

#### 7.6.3 标准四连验收清单

| # | 验收项 | 判定依据 |
| --- | --- | --- |
| 1 | 应用能装上 | `install` 返回 0，`pm path top.quup.myapps.debug` 有路径 |
| 2 | 能正常启动 | 前台窗口是 `top.quup.myapps.debug/app.MainActivity` |
| 3 | 启动不崩 | logcat 无 `FATAL EXCEPTION` / `ANR in top.quup.myapps.debug` |
| 4 | 界面渲染正常 | 截图是内网导航页 / 浏览器，不是白屏 |
| 5 | 语义结构正确 | UI 树 XML 里 `package="top.quup.myapps.debug"`，文案符合预期 |
| 6 | 内网可达（V1） | 授权后打开 `https://<host>.tailnet-xxx.ts.net` 成功，且 logcat 显示流量经 `127.0.0.1:1080` |

**注意事项**：`applicationId` = `top.quup.myapps`，debug 包 = `top.quup.myapps.debug`（带后缀，可与正式包共存）；设备上若装过旧包 `com.hubdeck.app.debug`，先 `adb uninstall` 清掉；证据留档放**项目文档区**，**不进代码仓库**。

#### 7.6.4 通道 E（CI 模拟器，手动备用）的已知边界

- 托管 runner **没有 `/dev/kvm`**，只能 TCG 软模拟：冷启动 ~12 分钟、单轮 30–120 分钟、平台随时可能强杀。
- 模拟器冷启动期间 SystemUI 会弹「isn't responding」，截图可能拍到弹窗 → `scripts/ci-emulator-probe.sh` 的做法：`hide_error_dialogs=1` → 轮询 `dumpsys window` 前台包名（≤120s）→ 必要时按掉弹窗 → 确认前台是我们的包才截图。
- CI 模拟器是 API 34 / x86_64，**深浅色、动态字体、TalkBack 的结论仍需真机**。

#### 7.6.5 没有设备时的边界

**可以做**（已在本仓库落实）：JVM 单元测试、debug APK 产出、Lint —— 全在 CI 跑。
**不能做**（保持「待验证」）：真机上的深浅色 / 动态字体 / TalkBack / 对比度、浏览器完整能力（标签 / 书签 / 下载）、真实 tailnet 下的 V1–V7。

> 原则：设备相关结论一律不猜。通道没到位时，验收表里写「待验证（阻塞于：缺真机 / 缺 API key）」。

### 7.7 验收证据规范

**原则**：只记录**实际跑出来**的输出。没跑的写「⛔ 待验证 + 阻塞原因 + 解除条件」，不写通过；设备相关结论一律不猜。

**截图命名规则**

```
docs/shots/<验收项编号>-<场景>-<序号>.png
```

- **验收项编号**：M1 任务号（`1.1` / `1.3`）或验证清单号（`V1`–`V7`）；通道自检写 `channel`。
- **场景**：小写短横线英文（`home`、`browser`、`dark-mode`）。**序号**：两位（`01`、`02`）。
- 示例：`docs/shots/1.1-inner-web-01.png`、`docs/shots/channel-boot-01.png`。
- 截图与 APK **不提交进仓库**（仓库只留模板与文字结论），证据走任务附件或文档引用。

**logcat 关键行截取**

```bash
scripts/device-session.sh logcat 300 > /tmp/logcat-tail.txt
grep -nE -A2 -B2 'FATAL EXCEPTION|ANR in|AndroidRuntime:|Process: top.quup.myapps' /tmp/logcat-tail.txt
```

- 报告里贴**关键行**（行号 + 前后各 2 行上下文 + 产生它的命令）；完整日志走附件。
- 通道自检（软模拟）会混入系统噪音，**只有 `ANR in top.quup.myapps.debug` 才算本应用问题**。

**结论表格式**

| 验收项 | 通道 | 结果 | 证据（命令 + 输出摘要 / 截图） | 备选通道 | 影响里程碑 |
| --- | --- | --- | --- | --- | --- |
| 例：标准四连 install→launch→shot→logcat | A 真机 | ✅ 通过 | `device-session.sh status` → `xxx device`；截图 `channel-boot-01.png` | E CI 模拟器（手动） | M1 |

- **结果**只允许三种：`✅ 通过` / `❌ 失败`（附错误输出）/ `⛔ 待验证`（写明阻塞项与解除条件）。
- **备选通道**：本条不通时可替代它的通道，以及它还缺什么（凭据 / 设备 / 磁盘）。
- **影响里程碑**：该条卡住哪个 M1 条目（对不上就写「无」）。

**最小证据清单（每条验收至少齐这四样）**

1. **复现命令**：一行可直接复制执行。
2. **实际输出**：关键段原文（不改写、不美化）。
3. **界面或日志证据**：UI 相关给截图 / `element-tree`；崩溃相关给 logcat 关键行。
4. **设备信息**：`scripts/device-session.sh status` 的通道类型、Android 版本 / SDK、ABI、序列号。

---

## 8. 里程碑 M0 ~ M4

| 里程碑 | 内容 | 出口标准 |
| --- | --- | --- |
| **M0 · 底座与骨架** | 明确产品形态；底座选型 GeckoView 147；GeckoView 骨架；隧道（tsnet 节点 + 本地代理 + PAC）；导航页；设置页；auth key 改走 Keystore；工具链定版；本地自检绿 | ✅ 已完成 |
| **M1 · 能用**（核心闭环） | ① G-1 PAC 接线 ② G-2 浏览器完整能力（标签 / 书签 / 下载 / 历史）③ G-3 隧道状态条 ④ 隧道异常兜底 ⑤ YAML 单测 ⑥ 设置页 UI 测试重写 ⑦ CI 随工具链升级验证 | 真机 + 蜂窝下，**一个 App** 打开家里内网服务；浏览器功能齐；公网浏览不受影响 |
| **M2 · 打磨** | 导航页增强（分组折叠 / 图标自定义 / 从书签生成条目）；首屏策略；错误文案（隧道未连接 / DNS 解析失败 / 私有 DNS 冲突）；无障碍（TalkBack / 动态字体 / 对比度）；稳定性 | 自用连续用一周，不需要再打开系统级 Tailscale App |
| **M3 · 评估与决策** | G-4 tsnet 依赖评估；G-5 包体与冷启动基准；转发性能实测；命名一致性 | 给出「保留 / 换官方包 / 退回依赖官方 App」的结论与量化数字 |
| **M4 · 收尾** | 隐私说明（关于页）；开源协议；文档同步；release 打包（arm64-v8a 单一 ABI） | 文档与代码一致（`doc-maintenance` 五条漂移判据逐条过） |

### 8.1 M1 收口清单（可直接拆任务）

| # | 任务 | 验收标准 | 归属 | 关联 |
| --- | --- | --- | --- | --- |
| 1.1 | **PAC 接线到 GeckoView**（G-1 / G-7） | 真机 + 蜂窝网络下，浏览器直接打开 `https://<host>.tailnet-xxx.ts.net` 成功；公网站点同时正常（V1 / V2） | 网络线 + 界面线 | ADR-202 |
| 1.2 | **浏览器完整能力**（G-2） | 多标签、书签（增删查）、下载、历史、无痕 —— 全部可用；实现方式必须是引擎 API 或 Mozilla 组件 | 界面线 | ADR-201 |
| 1.3 | **隧道状态条**（G-3） | 未连接 / 连接中 / 已连接 / 需授权 四态可见，点击可授权 | 界面线 | ADR-202 |
| 1.4 | **隧道异常兜底** | 隧道断开时浏览器自动回退直连，公网不受影响（V3） | 网络线 | ADR-202 |
| 1.5 | **YAML 清单单测**（G-9） | 合法 / 非法协议 / 空标题 / 分组 / 往返 五类用例 | 数据线 | ADR-203 |
| 1.6 | **设置页 UI 测试重写**（G-8） | Compose UI 测试跑通 | 界面线 | §6.2 |
| 1.7 | **CI 随工具链升级验证**（G-10） | `ci.yml` 在 Kotlin 2.2 / AGP 8.12 / Gradle 8.13 / GeckoView 147 上绿（构建 + 单测 + Lint） | 工程线 | §9.2 |

> 各项的当前状态见 `todo.md`。

---

## 9. 风险登记册

### 9.1 风险与缓解

| ID | 风险 | 影响 | 概率 | 缓解 |
| --- | --- | --- | --- | --- |
| **R1** | `com.klethron:tsnet` 非官方包，来源 / 许可 / 供应链未评估 | 自用风险低，但审计与长期维护风险实在 | 中 | M3 出结论；必要时换官方 gobind 绑定或退回「依赖官方 App」模式（代码里保留开关） |
| **R2** | 包体与冷启动开销未实测 | 装机转化、启动体验 | 中 | M3 基准；超阈值则评估拆变体 |
| **R3** | userspace 转发性能低于内核 TUN | 大文件 / 长连接慢 | 中 | 只承诺面板类访问；文档写明 |
| **R4** | 与官方 Tailscale App 的 MagicDNS 互相干扰 | 部分内网域名打不开 | 中 | 清单里写 FQDN 或 `100.x` IP，不用短名；探测直连成功则不强走隧道 |
| **R5** | GeckoView 无公开代理 API，PAC 接线可能踩坑 | 核心能力 G1 落不了地 | 中高 | M1 第一优先级；备选：`GeckoSessionSettings` 代理配置 / 引擎偏好 / 必要时退回「仅本地代理 + 系统浏览器」 |
| **R6** | 手写浏览器已删，短期内功能不完整 | 体验低于系统浏览器 | 高 | M1 补齐 G-2；在此之前保留系统浏览器作为临时出口 |
| **R7** | tsnet auth key 泄露 = 交出 tailnet 权限 | 严重安全事故 | 低 | Keystore 加密；不导出；一键清除；M1 前完成安全复核 |
| **R8** | 构建变慢（25 分钟/次，dex 合并吃内存） | 迭代效率 | 中 | CI 缓存；本地按需构建（`--no-apk`）；必要时 GeckoView 单独依赖缓存 |
| **R9** | 文档与代码漂移 | 协作方按错文档干活 | 中 | 每次里程碑收口做一次「文档 ↔ 代码」核对；契约变更必须升版 |
| **R10** | 与官方 App 双开时路由冲突 | 内网偶发不可达 | 低 | 清单用 FQDN / IP；错误文案引导 |

### 9.2 工具链版本矩阵（为什么锁这一组）

| 组件 | 版本 | 约束 |
| --- | --- | --- |
| Kotlin | 2.2.21 | 2.3.x 的 metadata（2.3.0）超出 Hilt 2.56.2 支持上限（2.2.0） |
| KSP | 2.2.21-2.0.5 | 必须与 Kotlin 同版本；Hilt 走 KSP |
| Hilt | 2.56.2 | 2.60+ 要求 AGP 9.0 |
| AGP | 8.12.0 | KSP 2.3+ 要求 AGP ≥ 8.12 |
| Gradle | 8.13 | AGP 8.12 要求 ≥ 8.13 |
| GeckoView | 147.0.20260212191108 | 148+ 用 Kotlin 2.3 metadata，与上面的 Kotlin 2.2.21 冲突 |

> KSP 与 Hilt 插件必须**声明在同一作用域**（root `build.gradle.kts` 里都 `apply false`），否则 Gradle 报 "KSP plugin … task class could not be found"。

### 9.3 技术栈速查

| 层 | 选型 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.2.21 / AGP 8.12.0 / Gradle 8.13 / Version Catalog |
| UI | Jetpack Compose + Material 3；ViewModel + StateFlow |
| 浏览器 | GeckoView 147（Firefox 引擎，MPL-2.0） |
| 隧道 | `com.klethron:tsnet:0.14.0`（userspace 节点）+ 自建本地 HTTP 代理 |
| 存储 | DataStore preferences（设置 / 清单 / 书签）+ Android Keystore（凭据） |
| YAML | kaml |
| 序列化 | kotlinx.serialization |
| DI | Hilt（KSP） |
| 测试 | JUnit + kotlinx-coroutines-test + Compose UI Test |

---

## 10. 未决问题与决策待办

### 10.1 未决问题（技术层面）

1. **G-1 的 PAC 接线具体走哪条 API？**（引擎偏好 / `GeckoSessionSettings` / 其它）—— 需要一次 POC。
2. **G-2 用 GeckoView 原生 API 还是引入 Mozilla android-components？** 后者功能全但依赖重、版本耦合紧。
3. **内部标识是否统一改名**（`hubdeck.*` → `myapps.*`，涉及数据文件与 DataStore 名）。
4. **首屏是导航页还是浏览器页？**（当前默认导航页，可在设置里改）。
5. **是否需要「内网服务清单直接从 tailnet 设备列表生成」**（控制平面能力，暂不做）。
6. **APK 的分发方式**：自用侧载即可，还是需要 Play 内测？（影响隐私政策与无障碍要求）

### 10.2 决策待办（需拍板）

| # | 决策 | 选项 | 建议 | 需时点 | 状态 |
| --- | --- | --- | --- | --- | --- |
| D1 | 产品正式名 | MyApps / HubDeck / 其他 | **MyApps**（代码已是 `top.quup.myapps`） | — | ✅ 已定 |
| D2 | 开源协议 | Apache-2.0 / AGPL-3.0 / 不开源 | Apache-2.0（无服务端、无 copyleft 顾虑） | M4 前 | ⏔ 待拍板 |
| D3 | APK 分发方式 | 侧载自用 / Play 内测 | **侧载自用**（不上架 → 无隐私政策 / 无障碍 / 供应链审查压力） | M1 前 | ⏔ 待拍板 |
| D4 | tsnet 依赖去留 | 保留 / 换官方包 / 退回依赖官方 App | M3 出数据再定；代码保留切换开关 | M3 | ⏔ 待评估 |
| D5 | 内部标识 `hubdeck.*` 是否改名 | 改名 / 保持 | 保持（改名要动 DataStore 与文件格式，收益低） | M3 | ⏔ 待定 |
| D6 | 是否引入 Mozilla android-components | 引入 / 用引擎原生 API | 先用引擎原生 API；确有缺口再引（依赖重、版本耦合紧） | M1 | ⏔ 待定 |

> 状态以 `todo.md` 为准。

---

## 11. 附录

### 11.1 术语

| 术语 | 含义 |
| --- | --- |
| tailnet | Tailscale 网络；设备之间通过 WireGuard 组网 |
| MagicDNS | Tailscale 的内部 DNS 名（`host.tailnet-xyz.ts.net`） |
| userspace 隧道 | 不占内核 TUN 的用户态网络栈（tsnet），App 内的 tailnet 节点即此 |
| 本地代理 | App 内的 HTTP 代理（`127.0.0.1:1080`），只把 tailnet 目标转进隧道 |
| PAC | Proxy Auto-Config —— 浏览器据此决定路由的脚本 |
| CONNECT 隧道 | HTTP 代理的隧道语义；本 App 只转发不解密，TLS 仍端到端 |
| 导航页 | 内网服务静态卡片页（YAML 驱动） |

### 11.2 Tailscale 能力盘点（哪些能用、哪些用不上）

| 能力 | 对本项目的价值 | 本项目的用法 |
| --- | --- | --- |
| **tsnet**（userspace 嵌入） | App 自身成为 tailnet 节点，不依赖系统级客户端 | ✅ **主通道**（`:core:tunnel`） |
| **MagicDNS** | 内部 DNS 名（`host.tailnet-xyz.ts.net`） | ✅ 内网清单里直接写 FQDN；短名由隧道代理端解析 |
| **Local API**（`127.0.0.1:41112`） | 读官方客户端状态 | ❌ 不用（隧道自包含） |
| **控制平面 API** | 设备目录、在线状态 | ❌ 不用（自用场景不值当引入 OAuth 凭据） |
| **Serve / Funnel** | 把本机服务暴露给 tailnet / 公网 | ❌ 不用（不暴露本机） |
| **官方 Android App** | 系统级 VPN 模式 | ⭕ 可共存（本 App 不占 VPN 槽），但**不是前置依赖** |

### 11.3 Android 平台限制与规避

| 限制 | 影响 | 本项目的规避 |
| --- | --- | --- |
| 第三方 App 读不到官方 Tailscale App 的私有存储 | 拿不到 Local API secret | **不需要**（隧道自包含，不读官方客户端） |
| Android 9+ 读 Wi-Fi SSID 需定位权限 | 「在家 / 在外」判定不准 | **不需要**（不做网络上下文判定，隧道常驻） |
| Private DNS（DoH）开启时 MagicDNS 查询被拦 | 部分内网域名解析失败 | 清单写 FQDN 或 `100.x` IP；错误文案引导关闭 Private DNS |
| 后台任务被杀 | 隧道断连 | 隧道状态常驻在前台；断连时浏览器自动回退直连 |
| userspace 转发慢于内核 TUN | 大文件 / 长连接偏慢 | 只承诺「面板类访问」；大文件建议用系统 App |
| 与官方 App 同时运行 | MagicDNS 解析可能互相干扰 | 清单优先用 FQDN / IP，不用短名 |

### 11.4 真机验证清单 V1–V7

| # | 验证项 | 方法 | 预期 | 现状 |
| --- | --- | --- | --- | --- |
| **V1** | **内网地址在 App 内浏览器打开**（核心） | 真机 + 真实 tailnet + 已授权 → 浏览器打开 `https://<host>.tailnet-xxx.ts.net` | 页面正常加载，且 logcat 显示流量经 `127.0.0.1:1080` | ⛔ 待验证（**依赖 G-1 PAC 接线**） |
| **V2** | **公网不受影响** | 同一时刻访问公网站点 | 正常加载（PAC 的 `PROXY …; DIRECT` 回退） | ⛔ 待验证 |
| **V3** | **隧道断开后浏览器仍可用** | 断开隧道 → 访问公网 | 公网正常；内网地址报明确错误 | ⛔ 待验证 |
| **V4** | **Private DNS 影响** | 系统开 Private DNS 后复测内网访问 | 记录失败模式并优化文案 | ⛔ 待验证 |
| **V5** | **授权流程** | 首次进入 App → 设置 → 开始授权 | 外部浏览器完成授权后回到 App 自动连上 | ⛔ 待验证 |
| **V6** | **包体 / 冷启动 / 隧道启动耗时** | 基准构建 + 真机计时 | 给出数字，对照阈值（包体 +25MB / 冷启动 +150ms） | ⛔ 待验证（M3） |
| **V7** | **转发性能边界** | 内网大文件下载测速 | 与系统 TUN 的差距有量化结论 | ⛔ 待验证（M3） |

> 旧的 V1–V7（Local API 可达性 / 控制平面 scope / `tailscale serve` 形态）随对应能力一起作废。
> 每条的当前状态以 `todo.md` 为准。

### 11.5 相关脚本与工作流

| 文件 | 作用 |
| --- | --- |
| `scripts/local-build.sh` | **本地自检入口**（编译 + 单测 + Lint + APK） |
| `scripts/build.sh` | CI 内唯一构建入口 |
| `scripts/device-session.sh` | 设备通道探测 + 安装 / 启动 / 截图 / 元素树 / 日志的统一入口 |
| `scripts/ci-emulator-probe.sh` | 通道 E 取证脚本（装 → 启 → 截图 → UI 树 → logcat） |
| `scripts/ci-emulator-probe.selftest.sh` | 取证脚本判定逻辑自检（假 adb，约 40 秒，不需要模拟器） |
| `scripts/hubdeck-env.sh` | CI 环境注入 |
| `.github/workflows/ci.yml` | 构建 + 单测 + Lint（PR / main push） |
| `.github/workflows/ci-emulator.yml` | CI 模拟器取证（手动备用） |
| `.github/workflows/apk.yml` | APK 产出（tag / 手动触发） |

### 11.6 事故复盘

| 文件 | 内容 |
| --- | --- |
| `incidents/2026-11-10-m1-hard-kill.md` | M1 hard kill 复盘；§6 是 **G0–G9 收口门禁**，收口前逐条给结论 |
