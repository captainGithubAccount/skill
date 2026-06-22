# AdMob 广告层接入检查清单

## AI 接入流程（开屏 + UMP 后首批 preload 强制）

- [ ] 已全工程检索开屏 `preloadAd` / `loadAd` / `LOADING_SPLASH` 调用点
- [ ] 已向用户输出「开屏调用点清单」表（含 #、文件、API、preload/load/展示分类）
- [ ] 用户已回复 **「确认调用点」** 或等价书面同意（未确认 **禁止** 写代码）
- [ ] 开屏 preload 网络入口 **仅 1 处**（`SplashLaunchPipeline.requestSplashPreloadIfNeeded`）
- [ ] **UMP 后所有 fire-and-forget preload**（语言插屏/原生、enter/back）与开屏 **同一** `runWhenSdkInitializedOnce`（`preloadAfterUmpConsent` + `requestSplashPreloadIfNeeded`）
- [ ] **禁止** UMP 结束后立刻 `preloadAfterUmpConsent` / 开屏 preload（须等 SDK 回调）
- [ ] **禁止** `if (!isInit) return` 整批跳过 UMP 后预加载（6a434ef4 旧逻辑）

## 模块与 Gradle

- [ ] `:AdBridge`、`:admob`、`:AdBase` 已加入工程
- [ ] `app` 依赖 `api(project(":AdBridge"))`
- [ ] `play-services-ads` 版本与金样一致或兼容
- [ ] Manifest `APPLICATION_ID` 已配置
- [ ] `local.properties` 含 `ad.app.id`（勿提交密钥）

## 初始化

- [ ] `MonetizationKit.prepareBeforeConsent` 已调用
- [ ] `AdRemoteConfigBridge.applyDefaultLocalAssetsA` 在展示前执行
- [ ] `MonetizationKit.init` 已调用且 `isInit=true`
- [ ] **全进程仅 1 次** `MobileAds.initialize`（仅在 `MonetizationKit.init`；`AdmobListenerImpl.init` / `warmUpMobileAds` 不得 duplicate）
- [ ] 冷启 Logcat 仅 1 条 `【SDK初始化】AdMob MobileAds.initialize 完成 isInit=true`
- [ ] UMP 结束后 `isUmpResolved=true`（双闸门均 true 才请求）
- [ ] Debug：`AdTestDeviceIdLog.applyDebugConfigurationBeforeInit` 在 **唯一** `MobileAds.initialize` 之前执行
- [ ] 开屏 + UMP 后首批：统一 `runWhenSdkInitializedOnce`（见 [sdk-init-callback.md](sdk-init-callback.md)）
- [ ] 冷启开屏已接 UMP（见 [ump接入](../ump接入/SKILL.md)）

## AB 面与远程配置（若接入）

- [ ] `PdfAppAdsBootstrap.canShowAd` 或等价门控已用于所有展示点
- [ ] B 专属位已加入 `modeBExclusive` 集合
- [ ] Firebase `pdf_ad_config_a` / `pdf_ad_config_b` 已配置
- [ ] B 面 commit 后 Logcat 可见 `【广告RC】远程Firebase覆盖 key=pdf_ad_config_b`
- [ ] B 面 FC 失败时有重试日志（`AbSettlementCoordinator`）
- [ ] **无 Loading 批**：Logcat 无 `Loading冷热启动结束` / `schedulePreloadAfterLoading`（见 [mode-b-page-gate.md](mode-b-page-gate.md)）
- [ ] **commit / A→B / FC apply 后无** Bootstrap 整批 preload
- [ ] 升 B 预加载仅经 **`bindModeBAdGateWhileVisible`**，且页面 **RESUMED** 才执行
- [ ] 冷启仅有 **UMP 批**（`UMP后预加载` / `UMP后开屏单次请求`）

## 广告位清单

- [ ] 用户已提供 [广告位清单模板.md](广告位清单模板.md) 或等价描述
- [ ] 每个位置已映射 `AdSense` + JSON `ad_sense`
- [ ] 插屏/原生已配置 **preload 时机**
- [ ] Banner 已明确「现场 load、无 Loader 预加载」
- [ ] 原生容器 `FrameLayout` 已存在；无货时容器 GONE

## JSON

- [ ] `assets/ad_remote_config_default_a.json` 含 A 面启用位
- [ ] 每个 `ad_id` 有效（测试或正式）
- [ ] `enable: true` 且 `ad_type` 与枚举一致（含 `banner`）
- [ ] `app_level_limit` 含 enter/back 概率、底栏 interval（若使用）
- [ ] Firebase B 面 JSON 含 Banner（ad_sense=4）等 B 专属位

## 代码质量

- [ ] 展示前检查 `canShowAd(sense)` 或 `MonetizationKit.enableFor(sense)`
- [ ] 插屏展示点未误用 `loadAd`（应 `showAd` 消费缓存）
- [ ] Banner Tab 切换不重复 load
- [ ] 新增注释为简体中文

## 开屏 Loading + UMP 后首批 preload（必读 [splash-loading.md](splash-loading.md) + [sdk-init-callback.md](sdk-init-callback.md)）

- [ ] UMP 后 **一个** `runWhenSdkInitializedOnce`：`scheduleSplashPreloadOnceWhenSdkReady` → `preloadAfterUmpConsent` + 开屏
- [ ] UMP 后 **仅 1 次** 开屏 preload：`splashPreloadRequested` 防重
- [ ] UMP 后首批 **无** `SDK 未 init，跳过` 整批 return（旧 `preloadAfterUmpConsent`）
- [ ] Splash **无** `MonetizationKit.init`；Application **仅一处** `MonetizationKit.init`
- [ ] UMP 后 **无** 在 SDK 回调外直接 `preloadAd(LOADING_SPLASH)` / 语言位 / enter/back
- [ ] Splash 协程 **无** `loadAd(开屏)`、**无** `preloadAdAwait`、**无** `await preloadAfterLoading`
- [ ] `AdPreloadCoordinator` **不含** `LOADING_SPLASH` preload
- [ ] Loading 放行：≥2s 动画 +（`isReady` 或 UMP 后 10s）
- [ ] 展示用 `SplashAdLoader.obtainForShow`；无缓存直接跳页
- [ ] Logcat：`开屏单次请求开始`；不应有 `Loading开屏` 的 loadAd 日志

```bash
rg "scheduleSplashPreloadOnceWhenSdkReady|preloadAfterUmpConsent" app/**/splash/
rg "UMP后预加载：SDK 未 init" app/   # 应无匹配
rg "UMP后预加载：SDK 未 init" app/   # 应无匹配（已改由 SDK 回调内调用）
rg "MonetizationKit\.init" app/**/splash/   # 应无匹配
rg "loadAd\(|preloadAdAwait" app/**/splash/
rg "LOADING_SPLASH" app/**/ads/AdPreloadCoordinator.kt  # 应无匹配
```

```bash
# 全进程 initialize 应仅 MonetizationKit.init 一处
rg "MobileAds\.initialize" --glob "*.kt"
# admob/AdmobListenerImpl 不应命中 initialize
```

## 验收（用户真机）

- [ ] Logcat 过滤 `TAG-->>AdRequest` 可见 preload 成功 / cacheHit
- [ ] Logcat 过滤 `TAG-->>vmodify` 可见 `【广告位判定】→ 可用`
- [ ] 冷启 Logcat：`UMP后预加载开始` 出现在 SDK init 完成之后或同时（不应再出现「SDK 未 init，跳过」）
- [ ] 冷启 Logcat **无 duplicate** `MobileAds.initialize 完成`（仅 1 条 SDK 初始化成功）
- [ ] 插屏无缓存时不崩溃、可继续业务流程（showSkippedNoCache）
- [ ] A 面不展示 B 专属位（Banner、语言广告等）
- [ ] B 面 + FC 成功后可展示 Banner
- [ ] 订阅用户不展示广告
- [ ] 关 VPN 后 FC 与广告请求正常（排除 SSL 证书问题）
