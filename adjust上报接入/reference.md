# Adjust 上报接入 — 详细参考

## 目录

1. [Gradle 配置](#gradle-配置)
2. [AdjustSdkUtil](#adjustsdkutil)
3. [DeferredTrackBridge](#deferredtrackbridge)
4. [PromoEventRelay（Firebase 付费展示）](#promoeventrelay-firebase-付费展示)
5. [Application 初始化](#application-初始化)
6. [广告位接入示例](#广告位接入示例)
7. [事件对照](#事件对照)

---

## Gradle 配置

见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)。

`libs.versions.toml` 关键片段：

```toml
[versions]
adjustSdk = "5.4.6"

[libraries]
other-adjustSdk = { group = "com.adjust.sdk", name = "adjust-android", version.ref = "adjustSdk" }
```

库模块 `build.gradle.kts` 关键片段：

```kotlin
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun p(key: String, default: String = ""): String {
    val raw = localProperties.getProperty(key, default) ?: default
    return raw.trim().takeIf { it.isNotEmpty() } ?: default
}

android {
    defaultConfig {
        buildConfigField("String", "ADJUST_APP_TOKEN", "\"${p("adjust.app.token")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.other.adjustSdk)
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation(libs.google.firebaseAnalytics) // DeferredTrackBridge 双路需要
}
```

---

## AdjustSdkUtil

完整模板：[templates/AdjustSdkUtil.kt.template](templates/AdjustSdkUtil.kt.template)

职责：
- `init(context, debug)` — 读取 `BuildConfig.ADJUST_APP_TOKEN`，调用 `Adjust.initSdk`
- `trackEvent(eventToken)` — 上报 Adjust 自定义事件
- `trackAdMobPaidEvent(adValue, responseInfo)` — AdMob 收益回传 Adjust（`AdjustAdRevenue` + source `"admob_sdk"`）
- `isReady` — 初始化成功标志

关键常量：

```kotlin
private const val AD_REVENUE_SOURCE_ADMOB = "admob_sdk"
```

收益计算：`adValue.valueMicros / 1_000_000.0`，货币 `adValue.currencyCode`，网络 `responseInfo.loadedAdapterResponseInfo.adSourceName`。

---

## DeferredTrackBridge

完整模板：[templates/DeferredTrackBridge.kt.template](templates/DeferredTrackBridge.kt.template)

```kotlin
object DeferredTrackBridge {
    val isAdjustReady: Boolean get() = AdjustSdkUtil.isReady

    fun init(context: Context, debug: Boolean) {
        AdjustSdkUtil.init(context, debug)
    }

    /** Adjust 事件 token + Firebase 同名事件双路上报 */
    fun emit(event: String) {
        AdjustSdkUtil.trackEvent(event)
        VdAnalyticsHub.emit(event, Bundle())
    }

    /** 广告展示事件（Adjust + Firebase 双路） */
    const val AD_SHOW = "ad_show"
}
```

若 Adjust 控制台 token 与 Firebase 事件名不同，拆分 emit：

```kotlin
fun emitAdShow() {
    AdjustSdkUtil.trackEvent("控制台AdjustToken")
    VdAnalyticsHub.emit("ad_show", Bundle())
}
```

---

## PromoEventRelay（Firebase 付费展示）

Adjust 收益与 Firebase 付费展示分开上报，此类仅负责 Firebase：

```kotlin
object PromoEventRelay {

    const val ADMOB_SDK = "admob_sdk"
    private const val EVENT_PAID_AD_IMPRESSION = "paid_ad_impression"

    /** AdMob OnPaidEventListener 中调用，与 AdjustSdkUtil.trackAdMobPaidEvent 配对 */
    fun loadAdLoadedReport(adValue: AdValue, responseInfo: ResponseInfo?) {
        runCatching {
            val valueMicros = adValue.valueMicros
            val currencyCode = adValue.currencyCode
            val adSourceName = responseInfo?.loadedAdapterResponseInfo?.adSourceName

            val bundle = Bundle().apply {
                putString("ad_platform", ADMOB_SDK)
                putString("currency", currencyCode)
                putDouble("value", valueMicros / 1_000_000.0)
                if (!adSourceName.isNullOrEmpty()) {
                    putString("ad_source", adSourceName)
                }
            }
            VdAnalyticsHub.emit(EVENT_PAID_AD_IMPRESSION, bundle)
        }.onFailure { it.printStackTrace() }
    }
}
```

---

## Application 初始化

```kotlin
override fun onCreate() {
    super.onCreate()
    // 1. Firebase 先初始化
    FirebaseApp.initializeApp(this)
    VdAnalyticsHub.wire(installId, emitInstallStart)

    // 2. Adjust 初始化（广告展示前）
    DeferredTrackBridge.init(this, BuildConfig.DEBUG)

    // 3. 广告预热 / 加载...
}
```

---

## 广告位接入示例

### Native 广告

```kotlin
AdLoader.Builder(context, adUnitId)
    .forNativeAd { nativeAd ->
        nativeAd.setOnPaidEventListener { adValue ->
            AdjustSdkUtil.trackAdMobPaidEvent(adValue, nativeAd.responseInfo)
            PromoEventRelay.loadAdLoadedReport(adValue, nativeAd.responseInfo)
        }
        // ...
    }
    .withAdListener(object : AdListener() {
        override fun onAdImpression() {
            DeferredTrackBridge.emit(DeferredTrackBridge.AD_SHOW)
        }
    })
    .build()
    .loadAd(AdRequest.Builder().build())
```

### 插屏 / 开屏广告

```kotlin
interstitialAd.onPaidEventListener = OnPaidEventListener { adValue ->
    AdjustSdkUtil.trackAdMobPaidEvent(adValue, interstitialAd.responseInfo)
    PromoEventRelay.loadAdLoadedReport(adValue, interstitialAd.responseInfo)
}

// FullScreenContentCallback 或 AdListener
override fun onAdImpression() {
    DeferredTrackBridge.emit(DeferredTrackBridge.AD_SHOW)
}
```

需覆盖的广告 Fetcher 类型（按项目实际）：
- `InlinePromoFetcher`（Native）
- `LaunchPromoFetcher`（开屏）
- `OverlayPromoFetcher`（插屏）

---

## 事件对照

| 场景 | Adjust | Firebase | 调用入口 |
|------|--------|----------|----------|
| 广告展示 | `trackEvent("ad_show")` | `logEvent("ad_show")` | `DeferredTrackBridge.emit(AD_SHOW)` |
| 广告收益 | `trackAdRevenue(AdjustAdRevenue)` | `paid_ad_impression` | `OnPaidEventListener` 内分别调用 |
| 自定义事件 | `trackEvent(token)` | `logEvent(token)` | `DeferredTrackBridge.emit(token)` |

### VdAnalyticsHub 依赖说明

`DeferredTrackBridge` 双路依赖 `VdAnalyticsHub`（Firebase Analytics 封装）。目标项目若无此类，可：

1. 从源项目复制 `VdAnalyticsHub.kt` + `TrackEventKeys.kt`
2. 或改为调用项目已有的 Analytics 封装，保持 `emit(event, bundle)` 签名一致
