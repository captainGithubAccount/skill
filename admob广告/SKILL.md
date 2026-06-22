---
name: admob-ad-monetization
description: >-
  Android AdMob 广告层可移植接入：AdBridge/admob/AdBase 模块、AdSense 广告位、
  preload/bindNativeAd/showAd/Banner、开屏 UMP+SDK一次性回调后单次 preload（sdk-init-callback/splash-loading）、
  A→B 升面仅页面 RESUMED 时分发（mode-b-page-gate/preload-timing）、禁止 commit/FC 后台整批 preload、
  远程 JSON、MonetizationKit 双闸门、开屏 load/preload 多入口须用户确认调用点后才能接入。
  当用户提到 AdMob、广告位、开屏、loading 开屏、preloadAd、bindNativeAd、showAd、A→B、接入广告 时应用。
---

# AdMob 广告实现（可移植层）

将 **AdBridge 定稿** 广告栈接入任意 Android 项目：**你只说明在哪些位置展示什么类型广告**，AI 按本 Skill 映射 `AdSense`、JSON、预加载与展示代码。

**现行金样（优先对照）**：

| 项目 | 路径 |
|------|------|
| **Nitro PDF（现行）** | `/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf` |
| videodownload v1.2.0（10 位参考） | `/Users/MacLuo/Desktop/D/study/android/workplace/FrameDemo/vsave/v1.2.0/videodownload` |

| 文件 | 路径 |
|------|------|
| AdSense 枚举 | `AdBridge/.../entity/AdSense.kt` |
| 对外 API | `AdBridge/.../ext/ActivityAdExt.kt`、`FragmentAdExt.kt` |
| `MonetizationKit` | 运行时闸门、`init` / `runWhenSdkInitializedOnce` |
| 远程 JSON 桥 | `AdBridge/.../config/AdRemoteConfigBridge.kt` |
| 请求链路日志 | `AdBridge/.../utils/AdRequestLog.kt` |
| AB/RC 编排 | `app/.../bootstrap/PdfAppAdsBootstrap.kt` |
| 预加载编排 | `app/.../ads/AdPreloadCoordinator.kt` |
| A→B 页面分发 | `app/.../ads/ModeBAdGateLifecycle.kt` |
| enter/back 插屏 | `app/.../ads/AdNavigationCoordinator.kt` |
| 主页 Banner | `app/.../ads/MainBannerController.kt` |
| 启动开屏 | `app/.../splash/SplashLaunchPipeline.kt` |
| Debug 测试设备 | `AdBridge/.../utils/AdTestDeviceIdLog.kt` |
| A 面默认 JSON | `app/src/main/assets/ad_remote_config_default_a.json` |

- **SDK 就绪回调 + 请求时机（必读）**：[sdk-init-callback.md](sdk-init-callback.md)
- **全部 preload 时机（大白话）**：[preload-timing.md](preload-timing.md)
- **A→B 升面页面分发（必读）**：[mode-b-page-gate.md](mode-b-page-gate.md)
- **开屏 Loading 策略（必读）**：[splash-loading.md](splash-loading.md)
- **详解**：[reference.md](reference.md)
- **产品对照**：[产品阅读.md](产品阅读.md)
- **验收**：[checklist.md](checklist.md)
- **你填位置**：[广告位清单模板.md](广告位清单模板.md)
- **UMP 时序**：[ump接入](../ump接入/SKILL.md)（可移植源码见 `ump接入/portable/`）
- **AB 面**：[ab面配置](../ab面配置/SKILL.md)（PDF 金样：FC 3 次重试、并行 bootstrap）

## 核心思想

```
用户描述展示位置 → AI 填「广告位清单」→ 扩展 AdSense + JSON → 在对应 Activity/Fragment 写 preload + show
```

广告层**不绑定**具体页面名；PDF 金样 8 个 `AdSense` 为现行参考，新项目可新增 id 9、10…

## 四种广告类型与 API

| 类型 | 典型场景 | 预加载 | 展示 | 注意 |
|------|----------|--------|------|------|
| **开屏 splash** | 启动 Loading 页 | UMP 后 **`runWhenSdkInitializedOnce` → preloadAd ×1**（见 [sdk-init-callback.md](sdk-init-callback.md)） | Loading 放行后 **`obtainForShow` 有缓存才 show** | **禁止** UMP 后立即 preload；禁止 Splash 内 `MonetizationKit.init` |
| **插屏 interstitial** | 跳转前、返回前 | **必须** `preloadAd` | `showAd`（**仅缓存**） | 无缓存不展示、不阻塞业务 |
| **原生 native** | 页面底部/列表穿插 | **必须** `preloadAd` | `bindNativeAd` / `showNativeAd` | onDestroy 自动 destroy |
| **Banner banner** | 主页底部可折叠 | **无** Loader 预加载 | `FloorziqAd.showBanner` 现场 load | PDF 用官方 collapsible 方案 |

扩展函数在 `ActivityAdExt.kt` / `FragmentAdExt.kt`（`FragmentActivity` 上调用）。

## 展示前四层闸门（PDF 定稿）

```
canShowAd(sense)  // PdfAppAdsBootstrap
  = MonetizationKit.enableFor(sense)
  && (非 B 专属位 或 已 commit 且 isModeB)

MonetizationKit.enableFor(sense)
  = isInit && !isSubs && isUmpResolved
  && JSON 含该 ad_sense（allowsAdSense）
  && 有 ad_unit_id
  && 未超日曝光/点击上限（canRequest）
```

**B 专属位**（A 面即使 JSON 有 id 也不展示）：`LANGUAGE_NATIVE`、`LANGUAGE_INTERSTITIAL`、`HOME_COLLAPSIBLE_BANNER`、`BOTTOM_NAV_INTERSTITIAL`、`SHARED_LARGE_NATIVE`。

**禁止** BuildConfig 私加第四级 ad_id 兜底；JSON 无该位则不请求。

## PDF 金样 8 位

| id | AdSense | 类型 | A/B | 预加载锚点 | 展示锚点 |
|----|---------|------|-----|------------|----------|
| 1 | LOADING_SPLASH | 开屏 | A/B | UMP 后 `runWhenSdkInitializedOnce` → preload ×1（Coordinator **不含**开屏） | Loading 放行后 `obtainForShow` → show 或跳页 |
| 2 | LANGUAGE_NATIVE | 原生 | 仅B | UMP 后/后台/Language initView；B commit 补货 | Language onResume bind |
| 3 | LANGUAGE_INTERSTITIAL | 插屏 | 仅B | 同上 + 确认前 ensure | 语言确认 showAd |
| 4 | HOME_COLLAPSIBLE_BANNER | Banner | 仅B | 无（Main 容器就绪现场 load） | MainActivity Banner |
| 5 | ENTER_INTERSTITIAL | 插屏 | A/B | UMP 后/后台 preload | navigateWithEnterAd |
| 6 | BACK_INTERSTITIAL | 插屏 | A/B | 同上 | finishWithBackAd |
| 7 | BOTTOM_NAV_INTERSTITIAL | 插屏 | 仅B | preloadOnMainEntry | Main 底栏 showAd |
| 8 | SHARED_LARGE_NATIVE | 大原生 | 仅B | preloadOnMainEntry + 各页 preload | bindNativeAd 多页 |

videodownload 10 位（含热启开屏 4、搜索/引导等）见 [reference.md](reference.md) 对照表。

## 接入前：扫描目标项目

| 检查项 | 含义 |
|--------|------|
| 已有 `:AdBridge` 等模块 | 增量加广告位 vs 整包复制 |
| `MonetizationKit.isInit` + `isUmpResolved` | 双闸门均 true 才发请求 |
| `AdRemoteConfigBridge` | JSON 是否已 apply；B 面需远程 `pdf_ad_config_b` |
| `PdfAppAdsBootstrap.canShowAd` | 是否叠 AB 面门控 |
| UMP / 开屏 | 冷启开屏见 [ump接入](../ump接入/SKILL.md) |

## 开屏调用点清点门禁（接入前强制 · 须用户确认）

凡按本 Skill **接入 / 修改开屏**（`LOADING_SPLASH`、`HOT_LOADING_SPLASH` 或等价 `ad_sense=1/4`），AI **必须先清点工程中所有 load / preload / 现场请求入口**，**列出表格给用户确认**，用户明确同意调用点方案后，**才允许**改代码、复制模板、写 Coordinator。

### 必须检索（实际执行或等价搜索）

```bash
# 开屏 preload / load / await
rg "LOADING_SPLASH|HOT_LOADING_SPLASH" --glob "*.kt"
rg "preloadAd\(|preloadAdAwait\(|loadAd\(" --glob "*.kt" -g "*splash*"
rg "preloadAd\(|preloadAdAwait\(|loadAd\(" --glob "*.kt" | rg -i "splash|loading_splash|开屏"

# 展示（不应再发网络请求，但须列入清单）
rg "obtainForShow|SplashAdLoader\.|FloorziqAd\.(preloadSplash|loadSplash|showSplash)" --glob "*.kt"

# 补货 / Coordinator 是否误含开屏
rg "LOADING_SPLASH" app/**/ads/
```

### 必须输出的「开屏调用点清单」（模板）

**未获用户确认前：禁止写代码。**

```markdown
## 开屏 load/preload 调用点清点（请确认）

| # | 文件:行 | 函数/触发时机 | 调用 API | 类型 | 金样是否保留 | 说明 |
|---|---------|---------------|----------|------|--------------|------|
| 1 | `…/SplashLaunchPipeline.kt:…` | UMP 后 `runWhenSdkInitializedOnce` | `preloadAd(LOADING_SPLASH)` | **preload** | ✅ 唯一网络 preload | … |
| 2 | `…/SplashLaunchPipeline.kt:…` | 放行闸后 | `obtainForShow` → `show` | **展示** | ✅ 只读缓存 | 非 load |
| … | … | … | … | … | … | … |

**统计**：preload 网络入口 **N** 处 · load 入口 **M** 处 · 展示 **K** 处

**金样目标（PDF）**：冷启开屏 **preload 网络仅 1 处**（`scheduleSplashPreloadOnceWhenSdkReady`）；`AdPreloadCoordinator` **不含**开屏；Splash **无** `loadAd(开屏)`。

**请你确认**：保留/删除/合并上表哪些行？回复「确认调用点」或指出要改的 # 号；**确认前我不改工程**。
```

### 判定规则（写入说明列）

| 情况 | AI 须告知用户 |
|------|----------------|
| **preload / load 多于 1 处** | 逐条说明是否会 duplicate 请求、Loader 去重能否兜底、建议保留哪一处 |
| **Coordinator 含 `LOADING_SPLASH`** | 标 ❌ 与金样冲突，建议移除或改为非开屏位 |
| **Splash 内 `loadAd` / `preloadAdAwait` 开屏** | 标 ❌ 旧误接，建议改为 `runWhenSdkInitializedOnce` + `obtainForShow` |
| **展示后 `AdReplenishCoordinator` 补货开屏** | 单独一行，scene 通常为展示消耗后；与 Loading 首次 preload **不是同一时机** |
| **仅注释/常量/枚举引用** | 标「非调用」，不计入 preload 统计 |

### 停止点

1. 输出上表 + 统计 + 金样目标对比  
2. **等待**用户「确认调用点」或修正意见  
3. 用户确认后，再进入 Step 2.5 / 模板复制 / 代码修改  

纯读代码解释、用户明确「先不要写代码」时可只输出清点表，同样须标注待确认项。

## 工作流 A：全新项目接入广告层

### Step 1：复制模块

```
:AdBridge   # MonetizationKit、Loader、Ext、RemoteConfig、UMP 封装
:admob      # AdMob Helper + SPI
:AdBase     # FloorziqAd、Banner/Native/Inter/Splash
:Event      # 埋点（收入/展示建议保留）
```

`settings.gradle.kts` 注册；`app` 使用 `api(project(":AdBridge"))`。

Gradle / Manifest 见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)。

### Step 2：Application 最小初始化

```kotlin
MonetizationKit.prepareBeforeConsent(context)          // RC 管家、前台监听、Debug 测试设备配置（不 initialize）
AdRemoteConfigBridge.applyDefaultLocalAssetsA(context) // A 面 assets 兜底
MonetizationKit.init(context) { /* isInit=true；全进程唯一 MobileAds.initialize */ }
// 并行：PdfAppAdsBootstrap.run(context) → commit 后 applyByMode
```

**SDK 单点初始化（方案 A · PDF 2026-06）**：全进程仅 `MonetizationKit.init` 内调用一次 `MobileAds.initialize`；`AdmobListenerImpl.init()` / `warmUpMobileAds` **不再** duplicate initialize。详见 [reference.md#sdk-单点初始化](reference.md#sdk-单点初始化)。

见 [templates/Application-init-snippet.kt.template](templates/Application-init-snippet.kt.template)。

### Step 2.5：Splash UMP 后统一 SDK 回调（开屏 + 首批 preload 必读）

**前置**：已完成 [开屏调用点清点门禁](#开屏调用点清点门禁接入前强制--须用户确认) 且用户已「确认调用点」。

UMP 结束后 **只注册一次** `runWhenSdkInitializedOnce`（`scheduleSplashPreloadOnceWhenSdkReady`），SDK 就绪后依次：

1. `AdPreloadCoordinator.preloadAfterUmpConsent` — 语言插屏/原生、enter/back（与 6a434ef4 同批）  
2. `requestSplashPreloadIfNeeded` — 开屏  

**禁止** `if (!isInit) return` 整批跳过 UMP 后预加载；**禁止** Splash 再调 `MonetizationKit.init`。

详见 [sdk-init-callback.md](sdk-init-callback.md)、[preload-timing.md](preload-timing.md)、[mode-b-page-gate.md](mode-b-page-gate.md)、[templates/sdk-init-callback-snippet.kt.template](templates/sdk-init-callback-snippet.kt.template)。

**禁止** commit / A→B / FC 后在 Bootstrap 里整批 `schedulePreloadAfterLoading*` 或 `preloadLanguageFunnel*`；升 B 走各页 `bindModeBAdGateWhileVisible`。

### Step 3：默认广告 JSON + Firebase

- 本地：`assets/ad_remote_config_default_a.json`（**仅 A 面**；B 面不读本地 assets）
- 远程：`pdf_ad_config_a` / `pdf_ad_config_b`（项目可改名，桥接类同步改 key）
- B 面远程未拉到 → 沿用 Application 阶段写入的 A assets（Banner 等 B 位会 `远程JSON未开放该ad_sense`）

模板：[templates/ad-remote-config.json.template](templates/ad-remote-config.json.template)

### Step 4：用户指定展示位置（核心）

请用户按 [广告位清单模板.md](广告位清单模板.md) 提供表格，或自然语言。

AI 对每个位置：

1. **分配** `AdSense`（新建或复用）+ `platformKey`
2. **JSON** 增加 `ads[]` 一项（`ad_sense`、`ad_type`、`ad_id`、`enable`）
3. **预加载时机**（`AdPreloadCoordinator` 或页面 onCreate/onResume）
4. **展示代码**（见 templates）
5. **开关**：展示前 `PdfAppAdsBootstrap.canShowAd(sense)` 或等价宿主门控

### Step 5：验证

完成 [checklist.md](checklist.md)；Logcat 见下方「日志排查」。

## 工作流 B：已有广告层，只加新位置

1. `AdSense` 枚举追加一项（id 不重复）
2. `ad_remote_config_*.json` + Firebase 同步
3. 若仅 B 面：加入 `modeBExclusive` 集合
4. 目标页面：`preloadAd` + `bindNativeAd` / `showAd` / Banner 控制器
5. 无需改 Loader（按 `adType` 自动路由）

## 日志排查（必读）

| Tag | 过滤 | 内容 |
|-----|------|------|
| `TAG-->>AdRequest` | `adb logcat -s "TAG-->>AdRequest:I"` | 预加载/加载/展示全链路 |
| `TAG-->>vmodify` | `adb logcat -s "TAG-->>vmodify:I"` | AB 结算、广告 RC、enableFor 判定 |
| `TAG-->>UMP合规` | Debug 测试设备 GAID/MD5 | AdMob RequestConfiguration |

**AdRequestLog 关键文案**：

- `【预加载跳过】原因=...` — 闸门未过或去重
- `【展示跳过】原因=无可用缓存` — 插屏/原生未 preload 成功
- `【预加载成功】` / `【缓存命中】` — 正常
- `【展示成功】` — SDK onImpression

**VModifyLog 关键文案**：

- `【广告位判定】→ 不可用 | 原因=远程JSON未开放该ad_sense` — A 面 JSON 无该位或 B 远程未拉到
- `【广告位判定】→ 不可用 | 原因=SDK未init` — init 未完成；开屏应走 [runWhenSdkInitializedOnce](sdk-init-callback.md)，勿 UMP 后立即 preload
- `【广告RC】远程key为空 | key=pdf_ad_config_b` — B 面 FC 失败，Banner 等 B 位不可用

## Debug 测试设备（与 UMP 区分）

| 机制 | API | 作用 |
|------|-----|------|
| **AdMob 测试广告** | `RequestConfiguration.setTestDeviceIds` | `AdTestDeviceIdLog.applyDebugConfigurationBeforeInit`，须在 `MobileAds.initialize` 前 |
| **UMP 联调** | `ConsentDebugSettings.addTestDeviceHashedId` | 仅 UMP gather；见 [ump接入](../ump接入/SKILL.md) |

二者 id 算法同源（GAID 去横线大写 MD5），但**职责不同**：前者让 AdMob 返回测试创意，后者让 UMP 按 EEA 地理联调。

## 代码片段

- 原生：[templates/native-snippet.kt.template](templates/native-snippet.kt.template)
- 插屏：[templates/interstitial-snippet.kt.template](templates/interstitial-snippet.kt.template)
- 开屏：[templates/splash-snippet.kt.template](templates/splash-snippet.kt.template)
- SDK 回调：[templates/sdk-init-callback-snippet.kt.template](templates/sdk-init-callback-snippet.kt.template)
- 开屏调用点清点（给用户确认）：[templates/splash-callsite-audit.md.template](templates/splash-callsite-audit.md.template)
- Banner：[templates/banner-snippet.kt.template](templates/banner-snippet.kt.template)

## 与其它 Skill 关系

| Skill | 关系 |
|-------|------|
| [ump接入](../ump接入/SKILL.md) | `isUmpResolved` 闸门；冷启开屏时序 |
| [ab面配置](../ab面配置/SKILL.md) | `PdfAppAdsBootstrap`、B 专属位、FC 重试 |
| [appsflyer接入](../appsflyer接入/SKILL.md) | 可选；`AdEventUtils` 收入上报 |

## 关键约定

0. **开屏 load/preload 多入口**：接入前必须 [开屏调用点清点](#开屏调用点清点门禁接入前强制--须用户确认)，**用户确认调用点后**再改代码
1. **UMP 后首批 preload（见 [sdk-init-callback.md](sdk-init-callback.md)）**：一个 `runWhenSdkInitializedOnce` → 语言/enter/back + 开屏 → Loading 放行闸 → `obtainForShow`
2. 插屏/原生：**先 preload，展示只 take 缓存**
3. **禁止** Splash 协程内 `loadAd(开屏)`、`preloadAdAwait` 链、`AdPreloadCoordinator` 里再 preload 开屏
4. Banner：**现场** `FloorziqAd.showBanner`；Tab 切走 GONE、切回复用实例
5. 注释简体中文；`scene` 写清业务时机便于 Logcat
6. 订阅用户 `MonetizationKit.isSubs=true` 全局不展示
7. VPN/代理可能导致 SSL 异常 — 关 VPN 再验
