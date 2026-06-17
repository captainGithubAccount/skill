---
name: adjust-reporting
description: >-
  Android Adjust SDK 接入与双路上报（Adjust + Firebase Analytics）：初始化、自定义事件、
  AdMob 广告收益回传。当用户提到 adjust、Adjust SDK、归因、广告收益回传、adjust上报、
  DeferredTrackBridge、AdjustSdkUtil，或需要在 Android 项目中集成/迁移 Adjust 埋点时自动应用。
---

# Adjust 上报接入

将 Adjust SDK 以 **SDK 封装层 → 桥接层 → 业务调用层** 三层架构接入 Android 项目，并与 Firebase Analytics 双路上报。

参考实现来源：`videodownload` 项目 `PromoCore` 模块。

## 接入前：识别目标项目

按以下顺序扫描，判断当前状态：

| 检查项 | 路径/关键词 | 含义 |
|--------|-------------|------|
| Gradle 依赖 | `adjust-android`、`libs.other.adjustSdk` | 是否已声明 SDK |
| Token 配置 | `local.properties` → `adjust.app.token` | 是否已配置 App Token |
| BuildConfig | `ADJUST_APP_TOKEN` | 是否已编译期注入 |
| 封装类 | `AdjustSdkUtil` | 是否已有 SDK 封装 |
| 桥接类 | `DeferredTrackBridge` | 是否已有双路上报桥接 |
| 初始化 | `Application.onCreate` → `DeferredTrackBridge.init` | 是否已初始化 |
| 广告收益 | `OnPaidEventListener` → `trackAdMobPaidEvent` | 是否已接入收益回传 |
| 广告展示 | `onAdImpression` → `DeferredTrackBridge.emit` | 是否已接入展示事件 |

**分支决策：**

- 无任何 Adjust 痕迹 → 走 [全新接入](#全新接入工作流)
- 有依赖但缺封装/桥接 → 补全 `AdjustSdkUtil` + `DeferredTrackBridge`
- 有封装但缺广告回调 → 在各广告 Fetcher 补 `OnPaidEventListener` 与 `onAdImpression`
- 已有完整实现 → 仅核对 [checklist.md](checklist.md)，按需迁移包名/模块

## 全新接入工作流

### Step 1：Gradle 与 Token

1. 在 `gradle/libs.versions.toml` 添加版本与库坐标（见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)）
2. 在目标库模块（推荐独立 `PromoCore` 或 `track` 模块）的 `build.gradle.kts`：
   - 读取 `local.properties` 注入 `BuildConfig.ADJUST_APP_TOKEN`
   - 添加 `implementation(libs.other.adjustSdk)`
   - 添加 `implementation("com.android.installreferrer:installreferrer:2.2")`
3. 在根目录 `local.properties` 添加（**不入库**）：
   ```
   adjust.app.token=你的AdjustAppToken
   ```

### Step 2：复制核心类

将模板复制到目标模块，替换 `{promo_package}` 为实际包名（如 `com.example.promo`）：

| 模板 | 目标路径 |
|------|----------|
| [AdjustSdkUtil.kt.template](templates/AdjustSdkUtil.kt.template) | `{promo_package}/internal/AdjustSdkUtil.kt` |
| [DeferredTrackBridge.kt.template](templates/DeferredTrackBridge.kt.template) | `{track_package}/DeferredTrackBridge.kt` |

`DeferredTrackBridge` 依赖项目已有的 `VdAnalyticsHub`（或等价的 Firebase Analytics 封装）。若目标项目 Firebase 入口名称不同，替换 `VdAnalyticsHub.emit` 调用即可。

### Step 3：Application 初始化

在 `Application.onCreate()` 中，**Firebase 初始化之后、广告展示之前**调用：

```kotlin
DeferredTrackBridge.init(this, BuildConfig.DEBUG)
```

`debug=true` 时使用 Adjust Sandbox + VERBOSE 日志；Release 使用 Production + SUPPRESS。

### Step 4：广告收益双路上报

在每个广告的 `OnPaidEventListener` 中同时调用：

```kotlin
ad.setOnPaidEventListener { adValue ->
    // Adjust 广告收益
    AdjustSdkUtil.trackAdMobPaidEvent(adValue, ad.responseInfo)
    // Firebase 付费展示（paid_ad_impression）
    PromoEventRelay.loadAdLoadedReport(adValue, ad.responseInfo)
}
```

`PromoEventRelay` 仅负责 Firebase，Adjust 收益不走桥接层。模板见 [reference.md](reference.md#promoeventrelay-firebase-付费展示)。

### Step 5：广告展示事件双路上报

在 `onAdImpression`（或等效展示回调）中：

```kotlin
DeferredTrackBridge.emit(DeferredTrackBridge.AD_SHOW)
```

`AD_SHOW` 常量值为 `"ad_show"`，Adjust 与 Firebase 使用同一事件名/token 双路上报。

> **注意**：若 Adjust 控制台要求使用独立事件 token（如 `8q6z3l`），将 `AD_SHOW` 改为控制台 token，Firebase 侧可继续用 `ad_show`——需拆分 `emit` 为两次独立调用。

### Step 6：验证

完成 [checklist.md](checklist.md) 全部项，对改动文件执行 Lint 验证（不编译）。

## 架构说明

```
Application.onCreate
    └── DeferredTrackBridge.init()
            └── AdjustSdkUtil.init()

业务埋点（自定义事件）
    └── DeferredTrackBridge.emit(token)
            ├── AdjustSdkUtil.trackEvent(token)    → Adjust
            └── VdAnalyticsHub.emit(token)          → Firebase

广告展示
    └── DeferredTrackBridge.emit(AD_SHOW)           → 双路

广告收益 OnPaidEventListener
    ├── AdjustSdkUtil.trackAdMobPaidEvent()         → Adjust
    └── PromoEventRelay.loadAdLoadedReport()        → Firebase
```

## 关键约定

1. **Token 不入库**：`adjust.app.token` 仅放 `local.properties`
2. **Adjust v5 无需 Activity 生命周期**：不用手动 `onResume`/`onPause`
3. **广告收益与 Firebase 分开**：Adjust 用 `trackAdRevenue`，Firebase 用 `paid_ad_impression`
4. **注释语言**：新增类/方法注释使用简体中文
5. **包名**：按目标项目现有结构放置，不强制 `com.isi.vd.*`

## 详细参考

- 完整代码与接入示例：[reference.md](reference.md)
- 逐项检查清单：[checklist.md](checklist.md)
