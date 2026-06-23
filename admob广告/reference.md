# AdMob 广告层 — 技术参考

**现行金样**：`/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf`  
**对照**：videodownload v1.2.0（10 位、热启开屏 id=4）

## 模块职责

| 模块 | 职责 |
|------|------|
| AdBridge | 对外 API、Loader、RemoteConfig、UMP 封装、AdRequestLog |
| admob | Splash/Inter/Native Helper、缓存 |
| AdBase | SPI：`FloorziqAd` 实际 show（含 Banner） |
| Event | `AdEventUtils`、`TelemetryCatalog` 埋点与收入 |

## API 一览（FragmentActivity）

| 函数 | 用途 |
|------|------|
| `MonetizationKit.runWhenSdkInitializedOnce(block)` | **Splash/UMP 后**：首次 `isInit=true` 时执行 block 一次；已 init 则同步执行。详见 [sdk-init-callback.md](sdk-init-callback.md) |
| `MonetizationKit.init(context) { }` | **仅 Application**：单点 `MobileAds.initialize`、置 `isInit` |
| `preloadAd(sense, scene)` | 后台预加载（fire-and-forget）；**开屏**须在 SDK 回调后调用 |
| `preloadAdAwait` | 协程内 await；**禁止** Splash 开屏路径；仅 Coordinator 后台 |
| `SplashAdLoader.isReady(sense)` | 开屏放行闸：Loader/SDK 是否有货 |
| `SplashAdLoader.obtainForShow(activity, sense, scene)` | 开屏展示：**只读缓存**，无货返回 null |
| `loadAd(sense, timeoutMs, scene)` | **开屏管线禁用**；非 Splash 特殊场景 |
| `takeCachedAd(sense)` | 取缓存不请求 |
| `showAd(sense, scene) { shown }` | 插屏=仅缓存 |
| `bindNativeAd(...)` | 原生绑定（**仅消费缓存**；ConvertFinish 等仍用） |
| `loadNativeForPageBind(sense, scene)` | bind 用：先缓存 → 无则页内 `loadAd`（`skipReplenishOnImpression=true`）→ 再 `preloadAdAwait` |
| `bindNativeAdInstantIfNeeded(...)` | 列表/二级页：封装 `loadNativeForPageBind` + 展示 |
| `ensureInterstitialCached` | 语言确认等单点补货 |

Fragment 有同名扩展（`FragmentAdExt.kt`）。

Banner 不走 Loader 预加载，由宿主调用 `FloorziqAd.showBanner`（见 `MainBannerController`）。

## 展示模式

### 插屏

```
onCreate/onResume → preloadAd(INTER_X)
用户触发 → showAd(INTER_X) { shown -> navigate() }
  ├─ 有缓存 → show → 曝光后 AdReplenishCoordinator 补货
  └─ 无缓存 → showSkippedNoCache，直接 navigate（不阻塞）
```

PDF enter/back：`AdNavigationCoordinator` 叠概率（`enter_inter_show_probability` / `back_inter_show_probability`）；enter 冷启首次必尝试。

### 原生（PDF 2026-06 · 即时 bind）

```
preloadAd(NATIVE_X)                    // 进页备货（仍保留）
onResume / 列表 bind →
  loadNativeForPageBind / bindNativeAdInstantIfNeeded
  ├─ 有缓存 → 填入容器；曝光后 AdReplenishCoordinator 补货
  ├─ 无缓存 → loadAd「页内即时load」+ skipReplenishOnImpression=true → 展示；曝光 **不**补货
  └─ load 仍失败 → preloadAdAwait「页内await」→ 再取缓存（缓存路径可补货）
onDestroy → NativeAd.destroy()（扩展内已注册）

bindNativeAd(...)                      // 仅缓存 bind（如 ConvertFinishActivity）
  └─ 无缓存 → hide + showSkippedNoCache（不页内 load）
```

**禁止**：`postDelayed(400ms)` 轮询 bind（已删除）。

`SHARED_LARGE_NATIVE`：列表穿插；Language / File / Tools / Bookmarks 用 instant bind；ConvertFinish 仍 `bindNativeAd` 仅缓存。

### Banner（PDF 2026-06 · load/show 两阶段）

```
requestLoad(applicationContext, scene)   // 应用级网络请求（不绑 Language destroy）
  触发点：
  ① 已配语言冷启：Splash SDK 批 scene=SDK就绪-直达主页Banner
  ② 首次语言页 initView：scene=语言页-折叠Banner预加载
  ③ Main initView bannerHost.post / onResume：scene=进入主页-折叠Banner预加载 或 onResume展示
showCollapsibleBanner(...)               // Main 容器 attach；Tab 切换复用 bannerReady
  ├─ bannerReady 且同 adId → 仅显示，不重复 request
  ├─ loadInFlight 且容器空 → postDelayed(400ms) 等 load 完成再 attach（UI 等待，非 bind 重试）
  └─ forceReload → requestInterrupted + clearAppScopeBanner + 再 requestLoad
二级页返回 / AB 升 B → forceReload scene=主页Banner-返回热启重建 / AB面升级重建
LanguageActivity.onDestroy → MainBannerController.onEarlyLoadHostDestroyed()
```

Banner **不走** `ApplicationAdRequests` / Loader preload；独立 `MainBannerController.requestLoad`。

### 开屏（Loading 页 · PDF 金样）

详见 **[splash-loading.md](splash-loading.md)**、**[sdk-init-callback.md](sdk-init-callback.md)**。

```
UMP 结束 → runWhenSdkInitializedOnce {
             preloadAfterUmpConsent（语言位，不含 enter/back）
             preloadAd(LOADING_SPLASH) ×1
             [已配语言] preloadBannerOnSplashSdkReady
           }
         → 放行闸：≥2s 且 (isReady 或 UMP+10s)
         → obtainForShow：有缓存 show，无缓存跳页
         → B 其它位：进主页 / 各页 RESUMED 分发（无 Loading 批）
```

```kotlin
// templates/splash-snippet.kt.template + sdk-init-callback-snippet.kt.template
private fun scheduleSplashPreloadOnceWhenSdkReady() {
    MonetizationKit.runWhenSdkInitializedOnce {
        requestSplashPreloadIfNeeded()
    }
}

private fun requestSplashPreloadIfNeeded() {
    if (splashPreloadRequested) return
    if (!canShowAd(LOADING_SPLASH)) return
    splashPreloadRequested = true
    activity.preloadAd(LOADING_SPLASH, "UMP后开屏单次请求")
}
```

冷启须 `isInit && isUmpResolved`。曝光后补货走 `AdReplenishCoordinator`，不在 Loading 后再 `preloadAdAwait(开屏)`。

**接入门禁**：开屏 preload/load 若工程内有多处，须先 [开屏调用点清点](SKILL.md#开屏调用点清点门禁接入前强制--须用户确认)，用户「确认调用点」后再改代码。

## SDK 单点初始化

**原则（PDF 2026-06 · 方案 A）**：全进程 **仅** `MonetizationKit.init` 内调用一次 `MobileAds.initialize`；`isInit=true` 只在该回调里置位。

| 入口 | 是否调用 `MobileAds.initialize` | 是否置 `isInit` |
|------|----------------------------------|-----------------|
| `MonetizationKit.prepareBeforeConsent` | ❌ | ❌ |
| `MonetizationKit.warmUpMobileAds` | ❌（仅 Debug 测试设备配置） | ❌ |
| `MonetizationKit.init` | ✅ **唯一** | ✅ |
| `AdBridgeIntegration.install` → `FloorziqAd.initAd()` → `AdmobListenerImpl.init` | ❌（SPI 占位） | ❌ |

**时序**：

```
Application IO 协程
  prepareBeforeConsent → AdTestDeviceIdLog.applyDebugConfigurationBeforeInit
  applyDefaultLocalAssetsA
  init → MobileAds.initialize 回调 → isInit=true → FloorziqAd.initAd() → initialized()
  PdfAppAdsBootstrap.run（并行 AB）
```

**Logcat 验收**：

- 冷启仅 **1 条** `【SDK初始化】AdMob MobileAds.initialize 完成 isInit=true`
- Debug 另见 `【AdMob测试设备】MobileAds.initialize 回调完成`（在 init 回调内）
- **不应**在 prepare/warmUp 阶段出现 initialize 完成日志

**竞态与补发**：UMP 与 Application 异步 init 并行 → 须 [runWhenSdkInitializedOnce](sdk-init-callback.md)，勿 UMP 后立即 preload。

**禁止**：

- `AdmobListenerImpl.init` 内再 `MobileAds.initialize`
- Splash 内再调 `MonetizationKit.init`（与 Application 竞态）
- UMP 后立即 `preloadAd(开屏)` 而不走 `runWhenSdkInitializedOnce`
- 用 Logcat 有 `FloorziqAd.initAd` 字样代替 `isInit=true` 验收

## SDK 就绪一次性回调

开屏与 UMP∥init 竞态的**标准接入**：见 **[sdk-init-callback.md](sdk-init-callback.md)**（`runWhenSdkInitializedOnce`、注册时机、防重、`init { }` 与监听的差异）。

### Banner（PDF 主页）

```
MainActivity.onResume + B面升级/RC刷新监听
  → MainBannerController.showCollapsibleBanner
  → FloorziqAd.showBanner(collapsible=bottom)
  → Tab 切走 hide(GONE)；切回复用已加载 AdView
```

## 远程 JSON

**本地**：`assets/ad_remote_config_default_a.json`（A 面兜底）  
**Firebase**：`pdf_ad_config_a` / `pdf_ad_config_b`

生效顺序：

1. Application：`applyDefaultLocalAssetsA` → 全员 A assets 兜底
2. bootstrap `applyByMode(isModeB)`：
   - A 面：再应用 A assets → 远程 A 覆盖
   - B 面：**跳过本地 assets** → 仅远程 B；远程空则沿用步骤 1 的 A assets
3. 远程 merge：同 `ad_sense` 远程优先，本地补缺失位

```json
{
  "version": "1.0.0",
  "scheme": "A",
  "app_level_limit": {
    "max_impressions_per_day": 30,
    "max_clicks_per_day": 10,
    "expire_splash_default_minutes": 232,
    "expire_inter_default_minutes": 52,
    "expire_native_default_minutes": 52,
    "background_dismiss_ms": 200,
    "enter_inter_show_probability": 50,
    "back_inter_show_probability": 50,
    "nav_click_show_inter_interval": 1
  },
  "ads": [
    {
      "ad_name": "loading_splash",
      "ad_sense": 1,
      "ad_type": "splash",
      "ad_id": "ca-app-pub-xxx",
      "priority": 1,
      "enable": true,
      "expire_minutes": 232,
      "request_timeout_ms": 8000
    }
  ]
}
```

`ad_type` 支持：`splash` | `interstitial` | `native` | `banner`

`AdRemoteConfigManager.allowsAdSense`：当前 scheme 的 `ads[]` 里**有该 ad_sense 且 enable** 才允许请求。

## PDF vs videodownload AdSense 对照

| PDF id | PDF AdSense | videodownload 近似 |
|--------|-------------|-------------------|
| 1 | LOADING_SPLASH | 1 冷启 + 4 热启（PDF 合并为 1） |
| 2 | LANGUAGE_NATIVE | 2 |
| 3 | LANGUAGE_INTERSTITIAL | 3 |
| 4 | HOME_COLLAPSIBLE_BANNER | —（PDF 新增 Banner） |
| 5 | ENTER_INTERSTITIAL | —（PDF 新增 enter 路由） |
| 6 | BACK_INTERSTITIAL | —（PDF 新增 back 路由） |
| 7 | BOTTOM_NAV_INTERSTITIAL | 6 |
| 8 | SHARED_LARGE_NATIVE | 5/7/9 合并大原生 |

## 新增 AdSense 步骤

1. `enum class AdSense` 追加 `MY_PAGE_NATIVE(9, AdType.NATIVE, "my_page_native")`
2. JSON 增加 `ad_sense: 9`；B 专属则加入 `PdfAppAdsBootstrap.modeBExclusive`
3. 页面代码 preload + bind/show；Banner 则写 Controller
4. 埋点 `platformKey` 与枚举一致

## Gradle 要点

- `play-services-ads` 25.3.0+（与金样对齐）
- AdBridge `BuildConfig`：`AD_SPLASH_ID` 等（测试占位，**展示以 JSON ad_id 为准**）
- Manifest：`com.google.android.gms.ads.APPLICATION_ID`
- `local.properties`：`ad.app.id` 等

## 复制文件清单

**必选目录**：

- `AdBridge/src/main/java/com/isi/monetization/**`
- `AdBridge/src/main/res/layout/master_ad_*`
- `admob/**`
- `AdBase/**`
- `app/src/main/assets/ad_remote_config_default_a.json`

**PDF 宿主按需**：

- `app/.../bootstrap/PdfAppAdsBootstrap.kt`
- `app/.../ads/AdPreloadCoordinator.kt`
- `app/.../ads/AdNavigationCoordinator.kt`
- `app/.../ads/MainBannerController.kt`
- `Event/**`（完整埋点）

## 日志排查

| Tag | 用途 |
|-----|------|
| `TAG-->>AdRequest` | 预加载/加载/展示/缓存/跳过 |
| `TAG-->>vmodify` | enableFor、AB 结算、广告 RC |
| `TAG-->>UMP合规` | Debug 测试设备 GAID |

常见：

- `showSkippedNoCache` → 未 preload 或缓存过期
- `远程JSON未开放该ad_sense` → A 面 JSON 无此位，或 B 面 FC 未拉到 `pdf_ad_config_b`
- `UMP流程未完成` → 早于 `markUmpResolved` 发请求
- `SDK未init` → Application 未 `MonetizationKit.init` 回调
- `canShowAd 未通过(AB 面或 enableFor 闸门)` → Banner 在 A 面或 B 远程未生效

## 插屏类共性（PDF）

- 展示：**只** `takeCachedAd`，不现场 load
- 曝光成功：`AdBridgeIntegration.onImpression` → `AdReplenishCoordinator.onFullScreenImpression` → `展示消耗后预加载`（**含语言插屏 LANGUAGE_INTERSTITIAL**，与 enter/back/底栏相同）
- **同 ad_id 去重**：B 面 `pdf_ad_config_b` 中语言插屏 (3) 与 enter (5) 常共用 ad_id → 后 preload 可能 `requestSkipped`（Loader `shouldSkipInterstitialPreload`）
- 展示失败：`onShowFailed` → `ad_no_show` + `cancelReplenish`
- 切后台：`AppForegroundMonitor` 约 200ms 自动 dismiss 插屏（`background_dismiss_ms`）


## 应用级请求（2026-06）

详见 [application-level-requests.md](application-level-requests.md)。
