# Adjust 接入检查清单

AI 集成完成后逐项核对，全部通过再结束任务。

## Gradle 与配置

- [ ] `gradle/libs.versions.toml` 已添加 `adjustSdk` 版本（推荐 `5.4.6`）与 `other-adjustSdk` 坐标
- [ ] 目标库模块 `build.gradle.kts` 已 `implementation(libs.other.adjustSdk)`
- [ ] 目标库模块已 `implementation("com.android.installreferrer:installreferrer:2.2")`
- [ ] `buildFeatures { buildConfig = true }` 已开启
- [ ] `buildConfigField("String", "ADJUST_APP_TOKEN", ...)` 已从 `local.properties` 读取 `adjust.app.token`
- [ ] 根目录 `local.properties` 已配置 `adjust.app.token`（提醒用户勿提交 VCS）

## 核心类

- [ ] `AdjustSdkUtil.kt` 已创建（init / trackEvent / trackAdMobPaidEvent / isReady）
- [ ] `DeferredTrackBridge.kt` 已创建（init / emit / AD_SHOW = "ad_show"）
- [ ] `PromoEventRelay.kt` 或等价类已创建（Firebase `paid_ad_impression`）
- [ ] 所有新增类与方法均有简体中文注释

## 初始化

- [ ] `Application.onCreate()` 中已调用 `DeferredTrackBridge.init(context, BuildConfig.DEBUG)`
- [ ] 初始化顺序：Firebase 初始化 → Adjust 初始化 → 广告加载/展示
- [ ] Token 为空时 SDK 优雅跳过（不崩溃）

## 广告收益（Adjust + Firebase 双路）

- [ ] Native 广告 `OnPaidEventListener` 已接入
- [ ] 插屏广告 `OnPaidEventListener` 已接入
- [ ] 开屏广告 `OnPaidEventListener` 已接入
- [ ] 每个回调同时调用 `AdjustSdkUtil.trackAdMobPaidEvent` 与 `PromoEventRelay.loadAdLoadedReport`

## 广告展示（Adjust + Firebase 双路）

- [ ] 各广告 `onAdImpression` 已调用 `DeferredTrackBridge.emit(DeferredTrackBridge.AD_SHOW)`
- [ ] `AD_SHOW` 常量值为 `"ad_show"`

## 质量

- [ ] 未使用 `resources.getIdentifier`
- [ ] 未将真实 Adjust App Token 硬编码到源码
- [ ] 改动文件已通过 Lint 检查（不编译）
- [ ] 包名已按目标项目替换，无残留 `com.isi.vd` 引用（除非目标项目即使用该包名）
