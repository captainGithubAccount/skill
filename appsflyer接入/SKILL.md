---
name: appsflyer-integration
description: >-
  Android AppsFlyer SDK 接入：初始化与归因、仅 ad_impression/ad_click 应用内事件、
  AdMob logAdRevenue 广告收入、FCM 卸载衡量。当用户提到 AppsFlyer、AF 归因、
  appsflyer.dev.key、logAdRevenue、ad_impression、ad_click、卸载衡量、AppsFlyerTracker 时自动应用。
---

# AppsFlyer 接入

将 AppsFlyer 以 **独立工具类 → 桥接层 → 业务埋点/广告回调** 接入 Android 项目。AF **不重复** Firebase 全量事件，仅承担归因 + 2 个广告事件 + 广告收入 + 卸载衡量。

- **参考实现（金样）**：`videodownload` → `Event/.../AppsFlyerTracker.kt`、`DeferredAttributionBridge.kt`、`KiteAnalytics.kt`、`AdEventUtils.kt`、`AppsFlyerFcmService.kt`
- **产品对照表**：[产品阅读.md](产品阅读.md)（已实现能力、表1 控制台、表2 事件字段）
- **链路图**：[流程图.md](流程图.md)
- **验收**：[checklist.md](checklist.md)

## 产品规则（必须遵守）

1. **凭证不入库**：`appsflyer.dev.key`、`appsflyer.app.id` 仅 `local.properties` → `BuildConfig`
2. **SDK 初始化**：`Dev Key` + **应用包名**（与 AF 控制台 Android 应用一致）；`app.id` 仅 Log 核对
3. **应用内事件仅 2 个**：`ad_impression`、`ad_click`；其它事件 **只走 Firebase**（经 `KiteAnalytics.emit`）
4. **广告收入**：必须用 `AppsFlyerLib.logAdRevenue`，**不要**用 `logEvent` 代替
5. **收入三路并行**：AF `logAdRevenue` + Adjust `trackAdRevenue` + Firebase `paid_ad_impression`（互不替代）
6. **展示/点击入口**：统一 `AdTracker.trackImpression` / `trackClick` → `TelemetryCatalog` → `KiteAnalytics` → AF
7. **卸载衡量**：`FirebaseMessagingService.onNewToken` → `updateServerUninstallToken`；需 `google-services.json` + AF 后台开启
8. **注释**：新增类/方法使用简体中文；Logcat 标签 `AppsFlyerTracker` / `AppsFlyerFcm`

## 接入前：扫描目标项目

| 检查项 | 路径/关键词 | 含义 |
|--------|-------------|------|
| Gradle | `af-android-sdk`、`appsflyerSdk` | SDK 依赖 |
| 配置 | `appsflyer.dev.key`、`appsflyer.app.id` | local.properties |
| BuildConfig | `APPSFLYER_DEV_KEY`、`APPSFLYER_APP_ID` | Event 模块注入 |
| 工具类 | `AppsFlyerTracker` | 是否已有封装 |
| 桥接 | `DeferredAttributionBridge.init` | Application 初始化 |
| 前台 start | `onAppForeground` / `startSession` | 归因会话 |
| 事件转发 | `KiteAnalytics.emit` 内 `ad_impression`/`ad_click` | AF 仅 2 事件 |
| 收入 | `AdEventUtils.loadAdLoadedReport` | onPaid 三路 |
| onPaid 挂载 | `AdjustHelper` / `AdRevenueBridge` | 开屏/插屏/原生 |
| 卸载 | `AppsFlyerFcmService` + Manifest `MESSAGING_EVENT` | FCM Token |
| Firebase | `google-services.json` | FCM 前置 |

**分支：**

- 无 AF 痕迹 → [全新接入](#全新接入工作流)
- 有 SDK 缺封装 → 复制 `AppsFlyerTracker` + 改 `KiteAnalytics` / `AdEventUtils`
- 已有封装缺 FCM → 补 `AppsFlyerFcmService` + Manifest
- 与 [checklist.md](checklist.md) 一致 → 仅改包名/模块路径

## 全新接入工作流

### Step 1：Gradle 与 local.properties

1. `gradle/libs.versions.toml` 增加 `appsflyerSdk`（参考 `6.17.6`）与 `other-appsflyerSdk` 坐标（见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)）
2. **Event**（或独立 telemetry 库）`build.gradle.kts`：
   - `buildFeatures { buildConfig = true }`
   - `buildConfigField` 读取 `appsflyer.app.id`、`appsflyer.dev.key`
   - `implementation(libs.other.appsflyerSdk)`
   - 若同模块已有 Adjust：`implementation(libs.google.ads)`（`logAdRevenue` 需要）
3. 根目录 `local.properties`（**勿提交**）：

```properties
appsflyer.app.id=你的_AF_应用ID
appsflyer.dev.key=你的_AF_Dev_Key
```

4. **Rebuild** 后 `BuildConfig` 才会生效

### Step 2：复制核心类

| 金样路径 | 目标 |
|----------|------|
| `Event/.../AppsFlyerTracker.kt` | 同包或 `{telemetry_package}/AppsFlyerTracker.kt` |
| `Event/.../internal/AppsFlyerSdkUtil.kt` | 可选兼容委托层 |
| `app/.../AppsFlyerFcmService.kt` | `{app_package}/push/AppsFlyerFcmService.kt` |

完整字段与 API 说明见 [reference.md](reference.md)。

### Step 3：桥接层

1. **DeferredAttributionBridge**（或合并进现有归因桥接）：
   - `init` → `AppsFlyerTracker.init`
   - `onAppForeground` → `AppsFlyerTracker.startSession`
   - 见 [templates/DeferredAttributionBridge-af-snippet.kt.template](templates/DeferredAttributionBridge-af-snippet.kt.template)

2. **Application**（Firebase 初始化 **之后**）：

```kotlin
DeferredAttributionBridge.init(this, BuildConfig.DEBUG)
```

见 [templates/BaseApplication-init-snippet.kt.template](templates/BaseApplication-init-snippet.kt.template)。

3. **进程前台**（`ProcessLifecycleOwner` 或应用门面）：

```kotlin
DeferredAttributionBridge.onAppForeground(context)
```

### Step 4：仅 2 个 AF 应用内事件

在 `KiteAnalytics.emit`（或等价 Firebase 入口）增加：

```kotlin
when (events) {
    TelemetryCatalog.ad_impression -> AppsFlyerTracker.logAdImpression(bundle)
    TelemetryCatalog.ad_click -> AppsFlyerTracker.logAdClick(bundle)
}
```

见 [templates/KiteAnalytics-af-snippet.kt.template](templates/KiteAnalytics-af-snippet.kt.template)。

业务侧继续 `AdTracker.trackImpression` / `trackClick`，**不要**直接散落 `AppsFlyerTracker.logEvent`（除非产品新增 AF 事件）。

### Step 5：广告收入 logAdRevenue

在 `AdEventUtils.loadAdLoadedReport`（或 `AdRevenueBridge` 实现）中：

```kotlin
AppsFlyerTracker.logAdMobPaidEvent(adValue, responseInfo)
```

并确保 **开屏 / 插屏 / 原生** load 成功时已挂 `OnPaidEventListener` → `AdRevenueBridge.report`（`AdjustHelper` 路径即可覆盖 admob 模块）。

见 [templates/AdEventUtils-af-snippet.kt.template](templates/AdEventUtils-af-snippet.kt.template)。

`AdTracker.rememberAdNetwork` 应在 onPaid 后写入，供 `ad_click` 补 `ad_network`。

### Step 6：卸载衡量

1. 复制 [templates/AppsFlyerFcmService.kt.template](templates/AppsFlyerFcmService.kt.template)
2. Manifest 注册 `MESSAGING_EVENT`（见 [templates/AndroidManifest-fcm-snippet.xml.template](templates/AndroidManifest-fcm-snippet.xml.template)）
3. AF 控制台开启 Uninstall measurement

### Step 7：验证

完成 [checklist.md](checklist.md)，对照 [流程图.md](流程图.md)。Logcat 过滤 **`AppsFlyerTracker`**、**`AppsFlyerFcm`**（不编译，仅 Lint）。

## 架构总览

```
Application.onCreate
    └── DeferredAttributionBridge.init → AppsFlyerTracker.init

进程前台
    └── onAppForeground → AppsFlyerTracker.startSession

AdTracker.trackImpression / trackClick
    └── KiteAnalytics.emit
            ├── AppsFlyerTracker.logAdImpression / logAdClick  → AF（仅 2 事件）
            └── FirebaseAnalytics.logEvent                    → Firebase（全量）

AdMob OnPaidEventListener
    └── AdEventUtils.loadAdLoadedReport
            ├── AdjustSdkUtil.trackAdMobPaidEvent
            ├── AppsFlyerTracker.logAdMobPaidEvent            → AF logAdRevenue
            └── KiteAnalytics.emit(paid_ad_impression)        → Firebase

FCM onNewToken
    └── AppsFlyerTracker.updateUninstallToken
```

## 常见坑

| 现象 | 原因 |
|------|------|
| 全部无 AF 数据 | 未 Rebuild / `appsflyer.dev.key` 为空 |
| 有展示无收入 | 测试广告无 onPaid |
| `ad_click` 缺 `ad_network` | 点击早于该 unit 的 onPaid |
| 卸载无 Token | 仅依赖 `onNewToken`，首装未刷新时可延迟上报 |
| `DeferredAttributionBridge.emit(AD_SHOW)` | **不走 AF**（仅 Adjust+Firebase），勿误以为 AF 漏报 |

## 相关文档

- [产品阅读.md](产品阅读.md) — 表1 / 表2 / 已实现能力
- [reference.md](reference.md) — 金样路径与扩展 API
- [checklist.md](checklist.md) — 集成检查清单
