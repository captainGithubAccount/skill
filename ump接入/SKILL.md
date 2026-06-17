---
name: ump-consent-integration
description: >-
  Android Google UMP 同意弹窗可移植接入：portable/ 源码包（AdConsentManager + EuRegionHelper +
  UmpGate/UmpFlowCallbacks 解耦）、先读缓存、欧盟预筛、Debug EEA 联调、双闸门 isInit+isUmpResolved、
  启动页时序。当用户提到 UMP、GDPR 同意弹窗、UMP 缓存、Debug 弹窗不显示、setDebugGeography、
  任意项目接入 UMP 时自动应用。
---

# UMP 同意弹窗接入（可移植）

将 **PDF 金样定稿** UMP 规则接入**任意** Android 项目。核心源码在 **[portable/](portable/)** 目录，**不依赖 AdBridge**，拷贝即用。

**金样对照（行为参考，不必改 PDF 工程）**：

| 项目 | 路径 |
|------|------|
| Nitro PDF | `/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf` |
| videodownload v1.2.0 | `/Users/MacLuo/Desktop/D/study/android/workplace/FrameDemo/vsave/v1.2.0/videodownload` |

## 可移植源码包（优先使用）

| 文件 | 说明 |
|------|------|
| [portable/AdConsentManager.kt](portable/AdConsentManager.kt) | UMP 协调器（完整版） |
| [portable/EuRegionHelper.kt](portable/EuRegionHelper.kt) | 欧盟/英国预筛 |
| [portable/UmpGate.kt](portable/UmpGate.kt) | 双闸门接口 + `SimpleUmpGate` |
| [portable/UmpFlowCallbacks.kt](portable/UmpFlowCallbacks.kt) | 与宿主解耦回调 |
| [portable/UmpLogTag.kt](portable/UmpLogTag.kt) | Logcat 前缀 |
| [portable/README.md](portable/README.md) | 拷贝步骤 |

**拷贝后**：把包名 `com.isi.ump` 替换为目标项目包名。

## 两条接入路径

| 路径 | 适用 | 闸门 | 回调 |
|------|------|------|------|
| **A. 有 AdBridge** | 已接 MonetizationKit | `MonetizationKit.isUmpResolved` | [MonetizationKitUmpAdapter.kt.template](templates/MonetizationKitUmpAdapter.kt.template) |
| **B. 无广告模块** | 仅需 UMP | `SimpleUmpGate` | `GateUmpFlowCallbacks(SimpleUmpGate)` |

PDF 金样仍用 AdBridge 内嵌版（直接调 `MonetizationKit`）；**新项目优先用 portable/**。

## 产品规则（定稿，必须遵守）

1. **先读缓存**：`hasCachedUmpConclusion()`；有缓存 **跳过 gather**
2. **无缓存 + 非欧盟/英国**：不初始化 UMP，直接结束并 `markUmpResolved`
3. **无缓存 + 欧盟/英国**：走 `gatherConsent`；**同意或拒绝**均结束，并写缓存
4. Application **可提前** `MonetizationKit.init`（与 UMP 并行）；广告请求须 `isInit && isUmpResolved`
5. **`canRequestAds()` 仅记日志**，不阻断广告请求
6. **Release** 与 Debug **同一套**地区/缓存分支；**不**在 Release 注入 `ConsentDebugSettings`
7. **UMP UI**：仅 `willRunUmpGather()==true` 时隐藏进度条、显示转圈
8. 弹窗样式由 **AdMob Privacy & messaging Publish** + SDK `isConsentFormAvailable` 决定

## 5 分钟快速接入

### Step 1：Gradle + Manifest

见 [templates/gradle-snippet.kts.template](templates/gradle-snippet.kts.template)

### Step 2：拷贝 portable/

拷贝 [portable/](portable/) 下 5 个 `.kt` → 目标 `app/.../ump/`，改包名。

### Step 3：Application

见 [templates/MyApplication-ump-snippet.kt.template](templates/MyApplication-ump-snippet.kt.template)

```kotlin
AdConsentManager.isDebugBuild = BuildConfig.DEBUG
if (BuildConfig.DEBUG) {
    AdConsentManager.debugConfig = AdConsentManager.DebugConfig(
        testDeviceHashedIds = AdConsentManager.DEFAULT_DEBUG_TEST_DEVICE_HASHED_IDS,
    )
}
```

### Step 4：启动页

- Pipeline 式：[SplashLaunchPipeline-awaitConsent.kt.template](templates/SplashLaunchPipeline-awaitConsent.kt.template)
- Activity 式：[StartActivity-awaitConsent.kt.template](templates/StartActivity-awaitConsent.kt.template)
- Layout：[activity_splash-ump-ui.xml.template](templates/activity_splash-ump-ui.xml.template)

### Step 5：接闸门

- **有 AdBridge**：`AdConsentManager(activity, MonetizationKitUmpCallbacks)`
- **无 AdBridge**：`AdConsentManager(activity, GateUmpFlowCallbacks(SimpleUmpGate))`；广告 load 前查 `SimpleUmpGate.isUmpResolved`

### Step 6：验收

[checklist.md](checklist.md)

## 决策顺序（冷启动）

```
① hasCachedUmpConclusion?  → 是：跳过 gather
② 欧盟/英国（EuRegionHelper）? → 否：跳过 UMP
③ gather UMP               → 结束写缓存
→ markUmpResolved → 后续广告 load
```

详见 [流程图.md](流程图.md)。

## Debug 联调弹窗

```kotlin
if (AdConsentManager.isDebugBuild && cfg != null && cfg.testDeviceHashedIds.isNotEmpty()) {
    ConsentDebugSettings.Builder(activity).apply {
        setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        cfg.testDeviceHashedIds.forEach { addTestDeviceHashedId(it) }
    }
}
```

测首次弹窗前：`adb shell pm clear <package>`

## 双闸门（有 AdBridge 时）

| 闸门 | 标志 | 负责方 |
|------|------|--------|
| SDK 就绪 | `MonetizationKit.isInit` | Application |
| UMP 完成 | `MonetizationKit.isUmpResolved` | AdConsentManager 回调 |

## 接入前：扫描目标项目

| 检查项 | 关键词 |
|--------|--------|
| 已有 UMP | `AdConsentManager`、`UserMessagingPlatform` |
| 闸门 | `isUmpResolved` 或 `SimpleUmpGate` |
| 过早请求 | `isInit=true` 但 UMP 未完成时 load |
| Debug | `setDebugGeography` + `debugConfig` |

**分支**：无 UMP → 快速接入；旧版全球 gather → [迁移](#迁移旧逻辑)；已符合 → [checklist.md](checklist.md)

## 迁移旧逻辑

**删除**：全球 gather 无缓存优先、`canRequestAds=false` 阻断、Release 注入 DebugGeography

**替换为**：portable/ + 先读缓存 → 欧盟预筛 → 双闸门

## 关键约定

1. **缓存**：SDK 持久化 + `ump_consent_cache.flow_completed_once`
2. **地区**：`EuRegionHelper` 读系统 locale；Debug 另用 `setDebugGeography(EEA)`
3. **热启动**：不 gather；入口即 `markUmpResolved`
4. **放行闸**：`MIN_ANIM_MS=2s`，`MAX_AFTER_UMP_MS=10s`
5. **Logcat**：`TAG-->>UMP合规`

## 详细参考

- [reference.md](reference.md)
- [流程图.md](流程图.md)
- [checklist.md](checklist.md)
- [impact-analysis.md](impact-analysis.md)
- [portable/README.md](portable/README.md)
