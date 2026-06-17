# UMP 同意弹窗 — 详细参考

> 流程图：[流程图.md](流程图.md)  
> **可移植源码**：[portable/](portable/)（任意项目优先）  
> 金样对照：`tools/browser/pdf` · `videodownload v1.2.0`

## 目录

0. [可移植包与解耦](#可移植包与解耦)
1. [完整同意流程](#完整同意流程)
2. [弹窗展示配置（AdMob + Debug）](#弹窗展示配置admob--debug)
3. [UMP 缓存](#ump-缓存)
4. [MonetizationKit 与双闸门](#monetizationkit-与双闸门)
5. [EuRegionHelper 地区预筛](#euregionhelper-地区预筛)
6. [ConsentStatus 与 canRequestAds](#consentstatus-与-canrequestads)
7. [Debug 联调](#debug-联调)
8. [启动页集成（PDF / videodownload）](#启动页集成pdf--videodownload)
9. [UMP 等待 UI](#ump-等待-ui)
10. [热启动](#热启动)
11. [埋点](#埋点)
12. [反模式](#反模式)
13. [Logcat 排查](#logcat-排查)

---

## 可移植包与解耦

### portable/ 目录

| 类 | 职责 |
|----|------|
| `AdConsentManager` | UMP gather / 缓存 / Debug 联调 |
| `EuRegionHelper` | 系统地区 → 是否 gather |
| `UmpGate` | `isUmpResolved` 闸门接口 |
| `SimpleUmpGate` | 无 AdBridge 时的默认实现 |
| `UmpFlowCallbacks` | 结束回调 + 可选 `onUmpPopShow` 埋点 |
| `GateUmpFlowCallbacks` | 把回调接到任意 `UmpGate` |

### 与 PDF 金样的关系

PDF 的 `AdBridge/.../AdConsentManager` **直接调用** `MonetizationKit.markUmpResolved()`，与 AdBridge 紧耦合。

**portable/** 通过 `UmpFlowCallbacks` 解耦，便于：

- 无广告模块的项目只接 UMP
- 有 AdBridge 的项目用 `MonetizationKitUmpCallbacks` 适配一行对接

**PDF 工程无需同步改代码**；金样仅作行为对照。

### 构造方式

```kotlin
// 无 AdBridge
val callbacks = GateUmpFlowCallbacks(SimpleUmpGate)
AdConsentManager(activity, callbacks).requestGatherConsentAndInitAds(scope) { }

// 有 AdBridge
AdConsentManager(activity, MonetizationKitUmpCallbacks).requestGatherConsentAndInitAds(scope) { }
```

---

## 完整同意流程

```
requestGatherConsentAndInitAds { onUmpFlowFinished ->
  │
  ├─ ① hasCachedUmpConclusion(context)?
  │     └─ YES → markUmpResolved → onUmpFlowFinished → return
  │
  ├─ ② !EuRegionHelper.shouldInitializeUmp(context)?
  │     └─ YES → markUmpResolved（跳过 UMP）→ onUmpFlowFinished → return
  │
  └─ ③ gatherConsent (suspend)
        ├─ [Debug] ConsentDebugSettings:
        │     setDebugGeography(DEBUG_GEOGRAPHY_EEA)
        │     addTestDeviceHashedId × N
        ├─ requestConsentInfoUpdate
        │     ├─ 成功 → isConsentFormAvailable → ump_pop_show → loadAndShowConsentFormIfRequired
        │     └─ 失败 → 仍回调结束
        ├─ markUmpConclusionCached（写本地 flow_completed_once）
        └─ markUmpResolved → onUmpFlowFinished
```

**注意**：回调 `onUmpFlowFinished` 无 Boolean；**同意/拒绝均允许后续广告请求**。

---

## 弹窗展示配置（AdMob + Debug）

### 三层条件

| 层 | 谁决定 | 不满足时现象 |
|----|--------|--------------|
| 客户端是否 gather | `hasCachedUmpConclusion` + `EuRegionHelper` | 直接跳过，**绝不弹** |
| SDK 是否认为需要弹 | `consentStatus`（如 REQUIRED） | 可能无 UI |
| 是否有表单资源 | `isConsentFormAvailable` | **无 UI**（最常见配置问题） |

### AdMob 后台（必须）

1. AdMob Console → **Privacy & messaging**
2. 创建/编辑 GDPR 消息，绑定当前 **AdMob Application ID**
3. **Publish** 到生产（Debug 包同样读该配置）
4. 未发布或 App ID 不匹配 → Logcat：`isConsentFormAvailable=false`

### Debug 客户端（联调弹窗）

**现行 `AdConsentManager.gatherConsent`（PDF）：**

```kotlin
if (BuildConfig.DEBUG && cfg != null && cfg.testDeviceHashedIds.isNotEmpty()) {
    val debugSettings = ConsentDebugSettings.Builder(activity).apply {
        setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        cfg.testDeviceHashedIds.forEach { addTestDeviceHashedId(it) }
    }.build()
    paramsBuilder.setConsentDebugSettings(debugSettings)
}
```

| API | 用途 |
|-----|------|
| `setDebugGeography(EEA)` | Debug 强制欧盟；**非 Release**；系统地区 CN 也可联调 gather |
| `addTestDeviceHashedId` | 本机 GAID → 去横线大写 → MD5 十六进制大写 |
| `AdConsentManager.debugConfig` | Application 注入；Release 为 null |

**测试设备 id 获取：**

- Logcat `TAG-->>UMP合规` → `【本机 AdMob 测试设备】`（`AdTestDeviceIdLog`）
- 或 UMP 首次 `requestConsentInfoUpdate` 时 Google 官方 Logcat 提示

**与 AdMob 广告测试 id 区分：**

- UMP：`ConsentDebugSettings.addTestDeviceHashedId`
- AdMob 广告：`RequestConfiguration.setTestDeviceIds`（`AdTestDeviceIdLog.applyDebugConfigurationBeforeInit`）

### Debug 弹窗预期日志

Debug 包会打 `【UMP 弹窗预期】`（`logUmpFormPopupExpectation`）：

| isConsentFormAvailable | consentStatus | 解读 |
|------------------------|---------------|------|
| false | 任意 | 查 AdMob Privacy & messaging 是否 Publish |
| true | REQUIRED | **可能弹窗** |
| true | OBTAINED / NOT_REQUIRED | 通常不再弹（清缓存后重测） |

---

## UMP 缓存

### 判定 `hasCachedUmpConclusion(context)`

| 顺序 | 来源 | 条件 |
|------|------|------|
| 1 | `SharedPreferences` `ump_consent_cache` | `flow_completed_once == true` |
| 2 | SDK `ConsentInformation` | `consentStatus` 为 `OBTAINED` 或 `NOT_REQUIRED` |

### 写入 `markUmpConclusionCached`

首次 `gatherConsent` 回调结束后写入 `flow_completed_once=true`。

### 辅助 API

| 方法 | 用途 |
|------|------|
| `hasCachedUmpConclusion(ctx)` | 是否有缓存 |
| `willRunUmpGather(ctx)` | 无缓存且欧盟/英国 → 将执行 gather（UI 用） |

---

## MonetizationKit 与双闸门

### Application（可提前）

```kotlin
MonetizationKit.prepareBeforeConsent(context)
MonetizationKit.init(context) { /* isInit = true */ }
```

与 UMP **并行、不互相阻塞**。

### 广告请求闸门

```kotlin
fun enableFor(sense: AdSense): Boolean {
    if (!enable) return false           // isInit && !isSubs
    if (!isUmpResolved) return false    // UMP 未完成不发请求
    // ...
}
```

| 标志 | 设置时机 |
|------|----------|
| `isInit` | Application `MonetizationKit.init` 回调 |
| `isUmpResolved` | UMP 跳过/gather 结束；热启动入口立即置 true |
| `resetUmpResolvedForLaunchCycle()` | 每轮冷启 pipeline 开头 |

---

## EuRegionHelper 地区预筛

- 依据：**系统设置** `configuration.locales[0].country`
- 名单：DE、FR、IT、ES、…、GB（27 国 + 英国）
- **仅当无 UMP 缓存时**用于判断是否 gather
- **Debug 联调弹窗**：另靠 `setDebugGeography(EEA)`，不依赖改系统地区

---

## ConsentStatus 与 canRequestAds

| consentStatus | 含义 | 视为有缓存 |
|---------------|------|------------|
| OBTAINED | 已有存储结果 | ✅ |
| NOT_REQUIRED | 无需弹窗 | ✅ |
| UNKNOWN | 尚无结论 | ❌ → 欧盟走 gather |
| REQUIRED | 需用户操作 | ❌ → 欧盟走 gather |

`canRequestAds()`：**仅 Logcat 记录**，不阻断 `enableFor` 或开屏 load。

---

## Debug 联调

| 项 | 行为 |
|----|------|
| Release 分支 | 先缓存、欧盟预筛；**无** `ConsentDebugSettings` |
| Debug 差异 | 注入 `DEBUG_GEOGRAPHY_EEA` + `DEFAULT_DEBUG_TEST_DEVICE_HASHED_IDS`（6 个，可追加） |
| 清数据 | 测「首次弹窗」前必须清 `ump_consent_cache` / 应用存储 |
| VPN/代理 | 可能导致 FC/UMP 失败；联调时建议直连 |

### 推荐联调步骤（FR / 任意地区 Debug 包）

1. `adb shell pm clear <package>` 冷启
2. 确认 Log：`Debug：已注入 N 个 UMP 测试设备 hashed id`
3. 确认 Log：`无 UMP 缓存，即将执行 gatherConsent`
4. 看 `【UMP 状态】`：`isConsentFormAvailable=true` 且 `consentStatus=REQUIRED`
5. 仍无 UI → 查 AdMob Privacy & messaging Publish 状态

---

## 启动页集成（PDF / videodownload）

### PDF — `SplashLaunchPipeline`

```kotlin
fun start() {
    MonetizationKit.resetUmpResolvedForLaunchCycle()
    if (isHotStart) MonetizationKit.markUmpResolved()
    lifecycleScope.launch { runPipeline() }
}

private suspend fun awaitConsent() {
    if (AdConsentManager.hasCachedUmpConclusion(activity)) {
        MonetizationKit.markUmpResolved()
        return
    }
    suspendCancellableCoroutine { cont ->
        AdConsentManager(activity).requestGatherConsentAndInitAds(lifecycleScope) {
            MonetizationKit.markUmpResolved()  // finishUmpFlow 内也会 mark，此处可保留
            if (cont.isActive) cont.resume(Unit)
        }
    }
}

private fun applyUmpProgressVisibility() {
    val hide = !isHotStart && AdConsentManager.willRunUmpGather(context)
    binding.addressbq4.visibility = if (hide) View.GONE else View.VISIBLE
    binding.splashUmpWait.visibility = if (hide) View.VISIBLE else View.GONE
}
```

**注意**：PDF 当前**未**实现 videodownload 的 `awaitSdkInitIfNeeded(2.5s)`，UMP 后若 `isInit=false` 可能短暂跳过开屏 load。

### videodownload — `StartActivity`

```
awaitConsent → awaitSdkInitIfNeeded → beginAdPhase → preloadAfterUmpConsent → loadSplash
```

见 [templates/StartActivity-awaitConsent.kt.template](templates/StartActivity-awaitConsent.kt.template)。

### 放行闸常量（共用）

| 常量 | 值 | 含义 |
|------|-----|------|
| MIN_ANIM_MS | 2000 | 启动页最短停留 |
| MAX_AFTER_UMP_MS | 10000 | UMP 后开屏 load 硬截止 |
| SPLASH_LOAD_TIMEOUT_MS | 10000 | 单次开屏 load 超时 |

---

## UMP 等待 UI

| 项目 | 控件 id | 显示条件 | 样式 |
|------|---------|----------|------|
| PDF | `splashUmpWait` | `willRunUmpGather && 冷启` | `indeterminateTint=@color/lang_blue_main`（#3D7CFD） |
| PDF | `addressbq4` 横向进度 | UMP 等待时 **GONE** | `@style/splash_progress` / `flat_housev` |
| videodownload | `isi_splash_ump_wait` | 同上 | 项目内主题色 |

有缓存的欧盟二次冷启动：**不显示转圈**，底部进度条直接可见。

---

## 热启动

```
pipeline.start / restartLaunchPipeline:
  resetUmpResolvedForLaunchCycle()
  if (热启动) markUmpResolved()   // 不 gather
  splashUmpWait → GONE
```

---

## 埋点

| 事件 | 时机 |
|------|------|
| `ump_pop_show` | `requestConsentInfoUpdate` 成功且 `isConsentFormAvailable==true`，调用 `loadAndShowConsentFormIfRequired` **之前** |

---

## 反模式

| ❌ | ✅ |
|----|---|
| 只加测试设备 id、不加 `setDebugGeography(EEA)` 就期望 CN Debug 必弹 | Debug 联调两者配合 + AdMob Publish |
| 有 UMP 缓存仍期望每次弹窗 | 清数据后测首次 |
| 用 `canRequestAds=false` 阻断请求 | 仅记日志 |
| Release 注入 DebugGeography | 仅 Debug |
| UMP 未完成就 load 广告 | `isUmpResolved` 闸门 |

---

## Logcat 排查

```bash
adb logcat -s "TAG-->>UMP合规:W" "TAG-->>vmodify:I" "SplashLaunch:I"
```

| 关键词 | 含义 |
|--------|------|
| `UMP｜当前系统地区码：FR` | 客户端会进 gather 分支（无缓存时） |
| `读取 UMP 缓存，跳过 gatherConsent` | 不会弹窗 |
| `Debug：已注入 N 个 UMP 测试设备` | Debug 配置生效 |
| `【UMP 弹窗预期】` | Debug 下为何不弹的直接说明 |
| `isConsentFormAvailable=false` | AdMob 后台消息未就绪 |
| `【UMP闸门】isUmpResolved=true` | 允许发广告请求 |
| `UMP流程未完成` | `enableFor` 被拦 |

---

## 参考文件（PDF 现行）

| 文件 | 路径 |
|------|------|
| AdConsentManager | `AdBridge/.../AdConsentManager.kt` |
| EuRegionHelper | `AdBridge/.../EuRegionHelper.kt` |
| MonetizationKit | `AdBridge/.../MonetizationKit.kt` |
| AdTestDeviceIdLog | `AdBridge/.../AdTestDeviceIdLog.kt` |
| SplashLaunchPipeline | `app/.../splash/SplashLaunchPipeline.kt` |
| activity_splash | `app/.../res/layout/activity_splash.xml` |
| MyApplication | `app/.../MyApplication.kt` |
