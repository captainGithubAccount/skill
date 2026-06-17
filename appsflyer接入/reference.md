# AppsFlyer 接入 — 技术参考

金样工程：`videodownload`（路径以你本机 `FrameDemo/vsave/.../videodownload` 为准）。

## 金样文件路径

| 文件 | 职责 |
|------|------|
| `Event/src/main/java/com/isi/telemetry/AppsFlyerTracker.kt` | AF 唯一对外能力封装 |
| `Event/src/main/java/com/isi/telemetry/internal/AppsFlyerSdkUtil.kt` | 历史 API 委托 |
| `Event/src/main/java/com/isi/telemetry/DeferredAttributionBridge.kt` | init / 前台 / FCM 桥接 |
| `Event/src/main/java/com/isi/telemetry/KiteAnalytics.kt` | Firebase + AF 2 事件分流 |
| `AdBridge/.../AdEventUtils.kt` | onPaid 三路收入 |
| `AdBridge/.../AdTracker.kt` | impression/click + rememberAdNetwork |
| `AdBridge/.../bridge/AdBridgeIntegration.kt` | AdRevenueBridge + 全屏 onAdImpression |
| `admob/.../AdjustHelper.kt` | 各广告类型挂 onPaid |
| `app/.../push/AppsFlyerFcmService.kt` | 卸载衡量 |
| `app/.../base/clazz/BaseApplication.kt` | init 时序 |
| `app/.../util/v3/VsaveV3FeatureKit.kt` | 前台 startSession |
| `docs/APPSFLYER_INTEGRATION.md` | 工程内简短说明 |

## Gradle 片段

### libs.versions.toml

```toml
[versions]
appsflyerSdk = "6.17.6"

[libraries]
other-appsflyerSdk = { group = "com.appsflyer", name = "af-android-sdk", version.ref = "appsflyerSdk" }
```

### Event/build.gradle.kts

```kotlin
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun p(key: String, default: String = ""): String =
    localProperties.getProperty(key, default)?.trim()?.takeIf { it.isNotEmpty() } ?: default

android {
    defaultConfig {
        buildConfigField("String", "APPSFLYER_APP_ID", "\"${p("appsflyer.app.id")}\"")
        buildConfigField("String", "APPSFLYER_DEV_KEY", "\"${p("appsflyer.dev.key")}\"")
    }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation(libs.other.appsflyerSdk)
    implementation(libs.google.ads) // logAdRevenue 需要 AdValue / ResponseInfo
}
```

## KiteAnalytics 分流（关键）

```kotlin
fun emit(events: String, bundle: Bundle? = null) {
    when (events) {
        TelemetryCatalog.ad_impression -> AppsFlyerTracker.logAdImpression(bundle)
        TelemetryCatalog.ad_click -> AppsFlyerTracker.logAdClick(bundle)
    }
    firebaseAnalytics?.logEvent(events, bundle)
}
```

## AdEventUtils 收入（关键）

```kotlin
fun loadAdLoadedReport(adValue: AdValue, responseInfo: ResponseInfo?) {
    AdjustSdkUtil.trackAdMobPaidEvent(adValue, responseInfo)
    AppsFlyerTracker.logAdMobPaidEvent(adValue, responseInfo)
    // ... Firebase paid_ad_impression + rememberAdNetwork
}
```

## AppsFlyerTracker 公开 API

| 方法 | 用途 |
|------|------|
| `init(context, debug)` | Application 一次 |
| `startSession(context)` | 进程前台 |
| `logAdImpression(bundle)` | 展示 |
| `logAdClick(bundle)` | 点击 |
| `logAdMobPaidEvent(adValue, responseInfo)` | 收入 |
| `updateUninstallToken(context, token)` | 卸载衡量 |
| `logEvent(context, name, params)` | 扩展用，产品默认不需要 |

## 扩展自定义 AF 事件（慎用）

仅当产品 **明确要求** 增加 AF 事件时使用：

```kotlin
AppsFlyerTracker.logEvent(
    context,
    "custom_event",
    mapOf("foo" to "bar"),
)
```

默认广告链路 **不要** 绕过 `AdTracker` / `KiteAnalytics`。

## 与 Adjust skill 的关系

- Adjust：[../adjust上报接入/SKILL.md](../adjust上报接入/SKILL.md) — `ad_show`、Adjust 收益
- AF 本 skill — 仅 `ad_impression`/`ad_click` + `logAdRevenue`
- 二者共用 `DeferredAttributionBridge` / `AdEventUtils.loadAdLoadedReport`，接入 AF 时 **保留** Adjust 调用

## ProGuard

AF SDK 一般自带 consumer rules；若 Release 混淆后无数据，对照 [AppsFlyer 官方 ProGuard 文档](https://dev.appsflyer.com/hc/docs/install-android-sdk#proguard) 补充 `Event/proguard-rules.pro`。
