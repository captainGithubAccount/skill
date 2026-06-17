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
| `preloadAd(sense, scene)` | 后台预加载（协程 fire-and-forget） |
| `preloadAdAwait` | 协程内 await 预加载 |
| `loadAd(sense, timeoutMs, scene)` | 同步加载不展示（开屏用） |
| `takeCachedAd(sense)` | 取缓存不请求 |
| `showAd(sense, scene) { shown }` | 插屏=仅缓存；开屏=loadAd 路径 |
| `bindNativeAd(sense, container, useLargeLayout, scene)` | 原生绑定；无缓存隐藏容器 |
| `showNativeAd` | 同 bind，带 onComplete |
| `ensureInterstitialCached` | 展示前确保插屏 SDK 有货 |

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

### 原生

```
preloadAd(NATIVE_X)
onResume → bindNativeAd(NATIVE_X, frameLayout, useLargeLayout)
  ├─ 有缓存 → 填入容器
  └─ 无缓存 → hideNativeAdContainer() + showSkippedNoCache
onDestroy → NativeAd.destroy()（扩展内已注册）
```

`SHARED_LARGE_NATIVE`：列表 1/6/11/16 穿插；无缓存 400ms 后重试 bind。

### 开屏（启动页）

```kotlin
// SplashLaunchPipeline
lifecycleScope.launch {
  val ad = loadAd(LOADING_SPLASH, timeoutMs = 10_000, scene = "冷启开屏")
  ad?.show(activity) { splashAd.destroy(); onNavigateNext() }
    ?: onNavigateNext()
}
```

冷启须 `isInit && isUmpResolved`（见 ump skill）。UMP 后 10s 硬截止可丢弃 splashAd 直跳。

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
- 曝光成功：`AdBridgeIntegration.onImpression` → `AdReplenishCoordinator` 补货
- 展示失败：`onShowFailed` → `ad_no_show` + `cancelReplenish`
- 切后台：`AppForegroundMonitor` 约 200ms 自动 dismiss 插屏（`background_dismiss_ms`）
