# HubDeck · Android 工程

Kotlin + Jetpack Compose (Material 3) + Room + Hilt + OkHttp 的原生 Android 工程。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin | 8.7.x |
| Gradle | 8.9+（用 wrapper 固定） |
| compileSdk / targetSdk | 35 |
| minSdk | 26（Android 8.0，与官方 Tailscale 客户端支持范围一致） |

## 构建

```bash
cd android
./gradlew :app:assembleDebug      # 构建
./gradlew :app:testDebugUnitTest  # 单元测试
./gradlew :app:lintDebug          # Lint
```

> **状态**：本脚手架由设计阶段产出，**尚未在具备 JDK / Android SDK 的环境执行过 Gradle 构建**。首次构建预计需要处理依赖版本对齐与少量 API 差异，属于 roadmap M1 的任务 1.1。

## 模块与包结构

```
app/src/main/java/com/hubdeck/app/
├── MainActivity.kt              # 单 Activity 入口（Compose）
├── HubDeckApplication.kt        # @HiltAndroidApp
├── core/
│   ├── model/                   # 纯 Kotlin 领域模型（Models / TailnetState）
│   ├── data/
│   │   ├── db/                  # Room 实体、DAO、数据库
│   │   ├── importer/            # hubdeck.yaml 导出/解析、Homarr YAML 导入
│   │   └── repo/                # ServiceRepository（唯一数据入口）
│   ├── network/                 # ServiceProbe（TCP / HTTP 探测）
│   ├── security/                # CredentialStore（Keystore + AES-GCM）
│   └── tailscale/               # Local API / 控制平面 / EndpointResolver / 网络上下文判定
├── di/                          # Hilt 模块（DB、OkHttp、客户端装配）
└── ui/                          # Compose 界面（dashboard / service / settings / tailscale）
```

## 关键契约

| 组件 | 契约 |
| --- | --- |
| `TailscaleLocalApiClient` | `GET http://127.0.0.1:41112/api/v2/status`，`Authorization: Bearer <LocalAPI Secret>`；任何失败都返回 `null`，绝不抛出 |
| `TailscaleControlApiClient` | `GET https://api.tailscale.com/api/v2/tailnet/{tailnet}/devices`；只读、最小 scope |
| `EndpointResolver` | 按网络上下文选择端点；首选端点不可达时回退并标记 `fellBack`；永不拒绝打开 |
| `EndpointUrlRenderer` | 渲染 `{host}`/`{port}`；Tailnet 短名自动补 MagicDNS 后缀 |
| `NetworkContextResolver` | 手动锁定 > Local API > Wi-Fi SSID > 探测结果 |
| `ServiceProbe` | TCP / HTTP HEAD；401/403 视为"服务活着但需要登录"；失败带可读原因 |

## 测试

`app/src/test/` 覆盖纯逻辑：端点选择与回退、地址渲染、网络上下文判定、Homarr 导入映射。
网络层建议补 MockWebServer 用例（见 roadmap M2 任务 2.3）。
