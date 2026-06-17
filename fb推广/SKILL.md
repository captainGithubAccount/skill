---
name: meta-fb-app-events
description: >-
  Android Meta（Facebook）App Events 投放优化接入：SDK 初始化与安装归因、
  Ad_Impression_Revenue、ad_d0_revenue（安装自然日）、ad_revenue（ROAS）、
  AdMob OnPaid 桥接。当用户提到 fb推广、Facebook 推广、Meta App Events、
  ad_d0_revenue、Ad_Impression_Revenue、ad_revenue、FB_APP_ID、FbAppEvents 时自动应用。
---

# Meta（Facebook）App Events 推广接入

将需求文档 **第七节「接入 fb」** 以 **App Events + SDK 初始化** 为核心接入 Android 项目。**仅用于 Facebook 广告投放优化**，不是变现 SDK、不是业务功能 SDK。

- **参考实现**：`videodownload` → `app/.../util/v3/delegate/FacebookEventsDelegate.kt`、`InstallIdHolder`、`AdEventUtils`、`BaseApplication`
- **与 A/B 面广告 JSON**：无直接耦合；收益上报与 `ad_config_a/b` 无关，见 [fb的ab面远程配置拉取](../fb的ab面远程配置拉取/SKILL.md)
- **官方文档**：[Getting Started with App Events for Android](https://developers.facebook.com/docs/app-events/getting-started-app-events-android/)

## 产品规则（必须遵守）

1. **SDK 范围**：仅 Meta **App Events**（`FacebookSdk` + `AppEventsLogger`），**禁止**为「接 FB 推广」额外接入 Audience Network 展示广告
2. **安装归因**：`Application` 冷启动 `init` + `AppEventsLogger.activateApp(application)`
3. **Ad_Impression_Revenue**：每次 AdMob **产生收益**（`OnPaidEventListener`）上报标准事件 `EVENT_NAME_AD_IMPRESSION`，带 `value` + `currency=USD`
4. **ad_d0_revenue**：**仅安装自然日**（本地时区），每次有收益上报自定义事件 `ad_d0_revenue`，`value` + `USD`；非安装日不上报
5. **ad_revenue**：每次有收益上报自定义事件 `ad_revenue`，供 **ROAS** 优化，`value` + `USD`
6. **value 来源**：使用广告 SDK 返回的收益金额（AdMob `valueMicros / 1e6`）；`currency` 参数固定 **USD**（不做汇率换算，与定稿实现一致）
7. **收益 ≤ 0**：跳过 Meta 上报
8. **用户级聚合**：由 Facebook 后台完成，客户端只逐次 `logEvent`

## 接入前：扫描目标项目

| 检查项 | 路径/关键词 | 含义 |
|--------|-------------|------|
| 依赖 | `facebook-android-sdk` / `libs.other.facebook` | 是否已声明 |
| 凭证 | `FB_APP_ID`、`FB_CLIENT_TOKEN` | Manifest + BuildConfig |
| 委托类 | `FacebookEventsDelegate` | 是否已有三事件 + D0 判断 |
| 安装日 | `KEY_FIRST_LAUNCH_APP_TS` | 首次启动时间是否写入 |
| 桥接 | `AdEventUtils.facebookRevenueReporter` | paid 是否接到 Meta |
| 开屏/插屏 paid | `AdBridgeIntegration` / `AdRevenueReporter` | 全屏是否走统一 paid |
| 原生 paid | `NativeAdLoader.setOnPaidEventListener` | 原生是否上报 |
| 门面 | `VsaveV3FeatureKit` 或等价 | `init` + `reportFacebookPaidAdRevenue` |

**分支：**

- 无 Meta 事件 → [全新接入](#全新接入工作流)
- 有 SDK 但缺 D0 / ad_revenue / USD → [迁移补齐](#迁移补齐)
- 已与 [checklist.md](checklist.md) 一致 → 仅改包名/模块路径

## 全新接入工作流

### Step 1：Gradle 与凭证

1. `gradle/libs.versions.toml` 增加 `facebook-android-sdk`（见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)）
2. `app/build.gradle.kts`：从 `local.properties` 注入 `FB_APP_ID`、`FB_CLIENT_TOKEN` 到 `manifestPlaceholders` 与 `BuildConfig`（**不入库**）
3. `local.properties` 示例：
   ```
   FB_APP_ID=你的Facebook应用编号
   FB_CLIENT_TOKEN=你的ClientToken
   ```

### Step 2：AndroidManifest

```xml
<meta-data android:name="com.facebook.sdk.ApplicationId" android:value="${FB_APP_ID}"/>
<meta-data android:name="com.facebook.sdk.ClientToken" android:value="${FB_CLIENT_TOKEN}"/>
```

见 [templates/AndroidManifest-meta-snippet.xml.template](templates/AndroidManifest-meta-snippet.xml.template)。

### Step 3：复制核心类

按目标项目包名替换 `{app_package}`：

| 模板 | 目标 |
|------|------|
| [templates/FacebookEventsDelegate.kt.template](templates/FacebookEventsDelegate.kt.template) | `{app_package}/util/v3/delegate/FacebookEventsDelegate.kt` |
| [templates/InstallIdHolder-first-launch-snippet.kt.template](templates/InstallIdHolder-first-launch-snippet.kt.template) | 合并进 `InstallIdHolder` 或等价安装 id 类 |
| [templates/VsaveV3FeatureKit-fb-snippet.kt.template](templates/VsaveV3FeatureKit-fb-snippet.kt.template) | 应用门面 `init` + `reportFacebookPaidAdRevenue` |
| [templates/BaseApplication-fb-reporter-snippet.kt.template](templates/BaseApplication-fb-reporter-snippet.kt.template) | `Application.onCreate` 注入 reporter |
| [templates/AdEventUtils-facebook-hook-snippet.kt.template](templates/AdEventUtils-facebook-hook-snippet.kt.template) | 广告模块 paid 回调 |

`Config` 增加：

```kotlin
/** 本机首次安装/启动墙钟时间（毫秒），供 Meta ad_d0_revenue 判断安装自然日 */
const val KEY_FIRST_LAUNCH_APP_TS = "KEY_FIRST_LAUNCH_APP_TS"
```

### Step 4：Application 初始化时序

在 `Application.onCreate` 中（**早于**首条广告请求）：

```kotlin
VsaveV3FeatureKit.init(this)  // 内部 FacebookEventsDelegate.init
AdEventUtils.facebookRevenueReporter = { revenue, _ ->
    VsaveV3FeatureKit.reportFacebookPaidAdRevenue(revenue)
}
```

`VsaveV3FeatureKit.init` 内第一行附近调用 `FacebookEventsDelegate.init(application)`。

### Step 5：AdMob paid 统一入口

确保 **开屏 / 插屏 / 原生** 的 `OnPaidEventListener` 最终都调用 `AdEventUtils.loadAdLoadedReport`（或等价），内部 `facebookRevenueReporter?.invoke(revenue, currencyCode)`。

参考工程：全屏经 `AdBridgeIntegration` 的 `AdRevenueReporter`；原生在 `NativeAdLoader` 单独设置。

### Step 6：验证

完成 [checklist.md](checklist.md)，对照 [流程图.md](流程图.md)。Logcat 过滤 **`FbAppEvents`**（简体中文日志）。

## 迁移补齐

| 旧实现问题 | 修复 |
|------------|------|
| 每次 paid 都报 `ad_d0_revenue` | 增加 `isInstallCalendarDay()`，仅安装自然日报 |
| 缺 `ad_revenue` | `reportPaidAdRevenue` 内增加 `logAdRevenue` |
| `currency` 跟 AdMob 走 | 固定 `EVENT_PARAM_CURRENCY = "USD"` |
| 未 `activateApp` | `init` 末尾 `AppEventsLogger.activateApp(application)` |
| 占位 AppId `111` 仍当完成 | 提醒配置真实 `FB_APP_ID` / Events Manager |

## 架构

```
Application.onCreate
  ├── FacebookEventsDelegate.init + activateApp
  └── AdEventUtils.facebookRevenueReporter = { reportFacebookPaidAdRevenue }

AdMob OnPaidEventListener（开屏/插屏/原生）
  └── AdEventUtils.loadAdLoadedReport
        └── reportPaidAdRevenue(value)
              ├── Ad_Impression（每次，value>0）
              ├── ad_revenue（每次，value>0）
              └── ad_d0_revenue（仅安装自然日，value>0）
```

## 关键约定

1. **与 Install Referrer 归因分工**：`AttributionManager` / `facebook_ads` 渠道用于 **A/B 面或内部埋点**；Meta **安装归因**靠 App Events SDK，二者并存不互相替代
2. **禁止**用 Meta SDK 展示广告变现
3. **禁止**在 Meta 链路外加第四套「收益兜底」假数据
4. 新增/修改类与方法注释使用**简体中文**
5. 改动文件 Lint 通过（**不编译**）

## 详细参考

- **流程图**：[流程图.md](流程图.md)
- **事件参数、场景表、控制台配置**：[reference.md](reference.md)
- **接入检查清单**：[checklist.md](checklist.md)
