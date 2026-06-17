# Meta（Facebook）App Events 推广 — 详细参考

> 可视化流程见 **[流程图.md](流程图.md)**。

## 目录

1. [需求对照表（第七节）](#需求对照表第七节)
2. [事件与参数](#事件与参数)
3. [类职责与路径](#类职责与路径)
4. [Gradle 与 Manifest](#gradle-与-manifest)
5. [安装自然日存储](#安装自然日存储)
6. [AdMob 桥接](#admob-桥接)
7. [Logcat 验收](#logcat-验收)
8. [常见问题](#常见问题)

---

## 需求对照表（第七节）

| 需求文档条目 | 客户端实现 | 说明 |
|--------------|------------|------|
| Meta App Events SDK，非变现 SDK | 仅用 `AppEventsLogger`，不接 AN 广告位 | ✅ |
| App Events + SDK 初始化 | `FacebookEventsDelegate.init` | ✅ |
| App Install Attribution | `activateApp(application)` | ✅ 配合后台 AppId |
| Ad_Impression_Revenue | `EVENT_NAME_AD_IMPRESSION` + value + USD | ✅ |
| ad_d0_revenue，安装当日每次有收益 | `ad_d0_revenue` + `isInstallCalendarDay()` | ✅ |
| ad_revenue，ROAS | 自定义 `ad_revenue` | ✅ |
| currency 传 USD | `EVENT_PARAM_CURRENCY = "USD"` | ✅ 数值不做汇率换算 |
| 使用广告 SDK 返回的 value | `valueMicros / 1_000_000.0` | ✅ |
| Facebook 用户级聚合 | 无客户端逻辑 | 后台自动 |

---

## 事件与参数

| 事件名（逻辑） | SDK 调用 | 触发条件 | Bundle |
|----------------|----------|----------|--------|
| Ad_Impression_Revenue | `AppEventsConstants.EVENT_NAME_AD_IMPRESSION` | 每次 paid 且 value>0 | `EVENT_PARAM_VALUE_TO_SUM`, `EVENT_PARAM_CURRENCY=USD` |
| ad_revenue | `logEvent("ad_revenue", params)` | 同上 | 同上 |
| ad_d0_revenue | `logEvent("ad_d0_revenue", params)` | 同上 **且** 安装自然日 | 同上 |

**自定义事件名**须与 Facebook Events Manager 中配置一致（大小写敏感）。

---

## 类职责与路径

| 类 | 职责 | 参考路径（videodownload） |
|----|------|---------------------------|
| `FacebookEventsDelegate` | init、三事件、D0 判断、中文日志 | `app/.../util/v3/delegate/FacebookEventsDelegate.kt` |
| `VsaveV3FeatureKit` | `init` 调 delegate；对外 `reportFacebookPaidAdRevenue` | `app/.../util/v3/VsaveV3FeatureKit.kt` |
| `InstallIdHolder` | 首次安装写 `KEY_FIRST_LAUNCH_APP_TS` | `app/.../util/mode2/InstallIdHolder.kt` |
| `AdEventUtils` | paid 统一入口 + `facebookRevenueReporter` 钩子 | `AdBridge/.../AdEventUtils.kt` |
| `BaseApplication` | 注入 reporter | `app/.../base/clazz/BaseApplication.kt` |
| `AdBridgeIntegration` | 开屏/插屏 paid → `loadAdLoadedReport` | `AdBridge/.../AdBridgeIntegration.kt` |
| `NativeAdLoader` | 原生 paid → `loadAdLoadedReport` | `AdBridge/.../NativeAdLoader.kt` |

---

## Gradle 与 Manifest

### libs.versions.toml

```toml
facebook = "latest.release"  # 或锁定具体版本
other-facebook = { group = "com.facebook.android", name = "facebook-android-sdk", version.ref = "facebook" }
```

### app/build.gradle.kts（片段）

```kotlin
val mFbAppId = localProperties.getProperty("FB_APP_ID") ?: ""
val mFbClientToken = localProperties.getProperty("FB_CLIENT_TOKEN") ?: ""
android {
    defaultConfig {
        manifestPlaceholders["FB_APP_ID"] = mFbAppId
        manifestPlaceholders["FB_CLIENT_TOKEN"] = mFbClientToken
        buildConfigField("String", "FB_APP_ID", "\"$mFbAppId\"")
        buildConfigField("String", "FB_CLIENT_TOKEN", "\"$mFbClientToken\"")
    }
}
dependencies {
    implementation(libs.other.facebook)
}
```

### AndroidManifest

```xml
<meta-data
    android:name="com.facebook.sdk.ApplicationId"
    android:value="${FB_APP_ID}" />
<meta-data
    android:name="com.facebook.sdk.ClientToken"
    android:value="${FB_CLIENT_TOKEN}" />
```

---

## 安装自然日存储

| MMKV Key | 写入时机 | 用途 |
|----------|----------|------|
| `KEY_FIRST_LAUNCH_APP_TS` | 首次 `KEY_INSTALL_ID` 创建或 `obtainForBootstrap` | D0 日历比较 |

```kotlin
private fun ensureFirstLaunchTimestamp(mmkv: MMKV) {
    if (mmkv.decodeLong(Config.KEY_FIRST_LAUNCH_APP_TS, 0L) > 0L) return
    mmkv.encode(Config.KEY_FIRST_LAUNCH_APP_TS, System.currentTimeMillis())
}
```

比较逻辑：本地时区 `Calendar.YEAR` + `DAY_OF_YEAR` 与安装日相同即为 D0。

---

## AdMob 桥接

```kotlin
// AdEventUtils.loadAdLoadedReport 末尾
val revenue = valueMicros / 1_000_000.0
facebookRevenueReporter?.invoke(revenue, currencyCode)

// BaseApplication
AdEventUtils.facebookRevenueReporter = { revenue, _ ->
    VsaveV3FeatureKit.reportFacebookPaidAdRevenue(revenue)
}
```

**注意**：注入必须在首条广告请求之前完成（`Application.onCreate` 即可）。

---

## Logcat 验收

过滤 tag：**`FbAppEvents`**

| 期望日志（安装日、有收益） | 含义 |
|---------------------------|------|
| `Facebook SDK 已初始化并 activateApp` | init 成功 |
| `已上报 Meta 事件=fb_mobile_ad_impression` 或标准名 | Ad_Impression |
| `已上报 Meta 事件=ad_revenue` | ROAS |
| `已上报 Meta 事件=ad_d0_revenue` | D0 |
| `非安装当日，跳过 ad_d0_revenue` | 次日仅 2 条收入事件 |

Events Manager → **Test Events** 核对参数 `value`、`currency=USD`。

---

## 常见问题

### Q1：事件不进后台？

- 检查 `FB_APP_ID` 是否为真实应用编号（非占位 `111`）
- 确认 Meta 应用与包名、签名与发布配置一致
- Debug 包可用 Test Events 实时看

### Q2：ad_d0_revenue 每天都有？

- 检查 `KEY_FIRST_LAUNCH_APP_TS` 是否被错误重置
- 清数据重装会重新计为「新安装日」

### Q3：与 Adjust / Firebase paid 关系？

- 三路并行：Adjust `trackAdMobPaidEvent`、Firebase `paid_ad_impression`、Meta 本 skill
- Meta **不替代** Adjust 归因

### Q4：value 与 USD 不一致？

- 定稿实现：**数值取 AdMob 返回金额，currency 参数写 USD**，不做汇率转换
- 若产品要求「必须先换算美元再传 value」，需在 `reportPaidAdRevenue` 前增加换算层（当前 skill **未包含**）

---

## 相关 skill

- [fb的ab面远程配置拉取](../fb的ab面远程配置拉取/SKILL.md) — `ad_config_a/b`
- [ab面判断逻辑](../ab面判断逻辑/SKILL.md) — `isModeB`
- [adjust上报接入](../adjust上报接入/SKILL.md) — AdMob paid 双路中的 Adjust 分支
