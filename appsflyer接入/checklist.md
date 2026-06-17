# AppsFlyer 接入检查清单

AI 集成完成后逐项核对，全部通过再结束任务。

## Gradle 与配置

- [ ] `gradle/libs.versions.toml` 已添加 `appsflyerSdk` 版本与 `other-appsflyerSdk` 坐标
- [ ] Event（或 telemetry）模块 `implementation(libs.other.appsflyerSdk)`
- [ ] `buildFeatures { buildConfig = true }` 已开启
- [ ] `buildConfigField` 已从 `local.properties` 读取 `appsflyer.app.id`、`appsflyer.dev.key`
- [ ] 根目录 `local.properties` 已配置两项密钥（提醒用户勿提交 VCS）
- [ ] 改配置后已 **Rebuild**（提醒用户）

## 核心类

- [ ] `AppsFlyerTracker.kt` 已创建（init / startSession / logAdImpression / logAdClick / logAdMobPaidEvent / updateUninstallToken / isReady）
- [ ] 可选 `AppsFlyerSdkUtil.kt` 兼容委托已创建
- [ ] `DeferredAttributionBridge` 已接 `init` + `onAppForeground`
- [ ] 所有新增类与方法均有简体中文注释

## 初始化与时序

- [ ] `Application.onCreate()` 在 Firebase 初始化 **之后** 调用 `DeferredAttributionBridge.init`
- [ ] 进程前台已调用 `AppsFlyerTracker.startSession`（或桥接 `onAppForeground`）
- [ ] Dev Key 为空时优雅跳过（不崩溃）
- [ ] Debug 包可看到 `AppsFlyer.init 完成` Log

## 应用内事件（仅 2 个）

- [ ] `KiteAnalytics.emit` 仅对 `ad_impression`、`ad_click` 转发 AF
- [ ] 展示统一 `AdTracker.trackImpression`（非散落 AF API）
- [ ] 点击统一 `AdTracker.trackClick`
- [ ] 未向 AF 上报 `ad_request`、`paid_ad_impression` 等其它事件名

## 广告收入

- [ ] 开屏 `OnPaidEventListener` 已接入（`AdjustHelper.openAd` 或等价）
- [ ] 插屏 `OnPaidEventListener` 已接入
- [ ] 原生 `OnPaidEventListener` 已接入
- [ ] `AdEventUtils.loadAdLoadedReport` 内已调用 `AppsFlyerTracker.logAdMobPaidEvent`
- [ ] onPaid 后已 `AdTracker.rememberAdNetwork`（供点击补 network）
- [ ] 使用 `logAdRevenue`，未误用 `logEvent` 报收入

## 卸载衡量

- [ ] `AppsFlyerFcmService` 已创建并在 `onNewToken` 上报
- [ ] `AndroidManifest` 已注册 `com.google.firebase.MESSAGING_EVENT`
- [ ] 工程存在 `google-services.json`
- [ ] AF 控制台 Uninstall measurement 已提醒用户开启

## 质量

- [ ] 未使用 `resources.getIdentifier`
- [ ] 未将真实 Dev Key 硬编码到源码
- [ ] 改动文件已通过 Lint（不编译）
- [ ] 包名已按目标项目替换，无错误残留引用

## 真机验收（可选，交给用户）

- [ ] Logcat：`logEvent ok: ad_impression`
- [ ] Logcat：`logAdRevenue ok`（真机付费广告）
- [ ] Logcat：`updateServerUninstallToken ok`（FCM 刷新后）
