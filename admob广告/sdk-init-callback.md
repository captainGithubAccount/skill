# SDK 初始化回调与请求时机（PDF 金样 · 2026-06）

> **一句话**：Application 异步 `MonetizationKit.init` 与 Splash UMP **并行**；UMP 结束后 **只注册一次** `runWhenSdkInitializedOnce`，SDK 就绪后统一触发 **UMP 后首批 preload**（语言插屏/原生、enter/back、开屏）；勿在 isInit 前整批 skip。

**金样代码**：

| 层级 | 路径 |
|------|------|
| 一次性 SDK 就绪 API | `AdBridge/.../MonetizationKit.runWhenSdkInitializedOnce` |
| UMP 后统一调度 | `SplashLaunchPipeline.scheduleSplashPreloadOnceWhenSdkReady` |
| UMP 后非开屏 preload | `AdPreloadCoordinator.preloadAfterUmpConsent`（6a434ef4 同批，须在 SDK 回调内调） |
| Application 单点 init | `app/.../MyApplication.launchAdsWarmup` |

---

## 0. 接入门禁：开屏调用点须用户确认

按本 Skill 接入开屏时，AI **必须先**在全工程检索 `LOADING_SPLASH` / `preloadAd` / `loadAd` / `obtainForShow` 等，输出 [SKILL.md §开屏调用点清点门禁](SKILL.md#开屏调用点清点门禁接入前强制--须用户确认) 表格。

- **preload / load 多于 1 处**：必须逐条说明文件、函数、是否会重复请求  
- **用户回复「确认调用点」前**：禁止改 Splash / Coordinator / 模板落地  
- PDF 金样目标：UMP 后 **一个** `runWhenSdkInitializedOnce` → `preloadAfterUmpConsent` + 开屏（与 6a434ef4 同批广告位，仅改时机）

---

## 1. 为什么需要这层

| 问题 | 原因 |
|------|------|
| UMP 后立刻 preload 漏请求 | `enableFor` 要求 `isInit && isUmpResolved`；旧代码 `if (!isInit) return` 会**整批跳过**语言/enter/back |
| 在 Splash 再调 `MonetizationKit.init` | 可能与 Application **竞态** duplicate initialize；且 `init { }` 入参在重复 `init()` 时会**再次**执行 |
| 固定 await 2.5s（videodownload） | 能修竞态但拖慢启动；PDF 金样改用 **状态 + 一次性回调** |

---

## 2. 三个 API 的分工（勿混用）

| API | 谁调用 | 触发几次 | 用途 |
|-----|--------|----------|------|
| `MonetizationKit.init(context) { }` | **仅 Application** | 入参 lambda：`MobileAds` 首次成功 **1 次**；若已 `isInit` 则**每次 init() 再调 1 次** | SDK 单点 initialize、置 `isInit` |
| `MonetizationKit.runWhenSdkInitializedOnce { }` | **Splash / 需等 SDK 的页面** | 每个注册的 block **只执行 1 次**；重复 `init()` **不会**再广播 | UMP 后发开屏 preload 等 |
| `MonetizationKit.isInit` | 任意只读 | 进程内首次 initialize 成功后恒 true | 闸门、`runWhen…` 注册时判断 |

```kotlin
// ✅ Application（IO 协程，与 Splash 并行）
MonetizationKit.init(applicationContext) {
    AdRequestLog.i("【SDK初始化】Application init 回调完成")
}

// ✅ Splash：UMP 结束后（一个回调内顺序执行）
MonetizationKit.runWhenSdkInitializedOnce {
    AdPreloadCoordinator.preloadAfterUmpConsent(activity, languageConfigured)
    requestSplashPreloadIfNeeded()
}

// ❌ Splash 禁止
MonetizationKit.init(activity) { preloadAd(...) }
```

---

## 3. `runWhenSdkInitializedOnce` 语义

```kotlin
fun runWhenSdkInitializedOnce(block: () -> Unit)
```

| 注册时刻 `isInit` | 行为 |
|-------------------|------|
| **已为 true** | **同步立刻**执行 `block`（SDK 先于 UMP 完成时） |
| **仍为 false** | `block` 入队；**仅**在 `MobileAds.initialize` **首次**成功、`isInit=true` 后 `dispatch` **一次** |

**与 `init { initialized() }` 的区别**：

- 已注册的 `runWhenSdkInitializedOnce` **不会**因 Application 再次调用 `init()` 而重复 dispatch
- `init` 的入参 lambda 在 `isInit==true` 时**每次 init() 都会同步再跑**（当前工程仅 Application 调 1 次，风险低）

---

## 4. UMP 后首批 preload 标准编排（开屏 + 语言/enter/back）

### 4.1 注册点：**UMP 结束之后（仅一次 `runWhenSdkInitializedOnce`）**

```kotlin
// runPipeline 内，awaitConsent / markUmpResolved 之后
splashRequestStarted = true
scheduleSplashPreloadOnceWhenSdkReady(languageConfigured)

private fun scheduleSplashPreloadOnceWhenSdkReady(languageConfigured: Boolean) {
    MonetizationKit.runWhenSdkInitializedOnce {
        AdPreloadCoordinator.preloadAfterUmpConsent(activity, languageConfigured)
        requestSplashPreloadIfNeeded()
    }
}
```

**UMP 后 fire-and-forget 位（金样，均在上述回调内）**：

| AdSense | 条件 |
|---------|------|
| `LANGUAGE_INTERSTITIAL` / `LANGUAGE_NATIVE` | `!languageConfigured`（Loader 层 A 方案 skip；不卡 `canShowAd`） |
| `ENTER_INTERSTITIAL` / `BACK_INTERSTITIAL` | `canShowAd` |

**不在 UMP 前注册**；**禁止** `if (!MonetizationKit.isInit) return` 整批跳过（旧 `preloadAfterUmpConsent`）。

### 4.2 开屏 + 批次防重

```kotlin
// Coordinator：本 launch 周期一批只执行一次
private val umpConsentPreloadExecuted = AtomicBoolean(false)

// Splash 开屏：本页只 preload 一次
private var splashPreloadRequested = false
```

### 4.3 执行 preload 的充分条件

| 条件 | 说明 |
|------|------|
| UMP 已结束 | 注册监听前已 `markUmpResolved` |
| SDK 已 init | `runWhenSdkInitializedOnce` 触发时 |
| `canShowAd` / `enableFor` 通过 | 非订阅、RC、ad_id、日限额、AB 等 |
| `splashPreloadRequested==false` | 本页只请求 1 次 |

**注意**：preload **不依赖** Loading 动画是否结束；动画/10s 闸只管 **展示/跳页**（见 [splash-loading.md](splash-loading.md)）。

### 4.4 Loader 第二层去重

即使误调两次 `preloadAd`，`SplashAdLoader` 仍会 skip：

- `isCacheLoading` → 「预加载任务进行中」
- `shouldSkipSplashPreload` → 「SDK层已有开屏缓存」

---

## 5. 两种竞态时序

```mermaid
sequenceDiagram
    participant App as Application init
    participant UMP as UMP 结束
    participant R as runWhenSdkInitializedOnce
    participant P as preloadAd 开屏

    Note over App,UMP: 情况 A：SDK 先完成
    App->>App: isInit=true
    UMP->>R: 注册
    R->>P: 同步立刻 preload

    Note over App,UMP: 情况 B：UMP 先完成
    UMP->>R: 注册，入队
    App->>App: isInit=true → dispatch
    R->>P: preload 一次
```

---

## 6. 其它广告位是否也要 `runWhenSdkInitializedOnce`？

| 场景 | 建议 |
|------|------|
| **UMP 后 fire-and-forget 一批**（开屏 + 语言 + enter/back） | **必须**与开屏 **同一** `runWhenSdkInitializedOnce`（`preloadAfterUmpConsent` + 开屏） |
| Loading 后 `runPreloadAfterLoading` | 已有 `awaitBootstrapAndSdkReady` 等 isInit（最多 30s），**不必**再套 UMP 回调 |
| B 面 commit 补货 | `preloadLanguageFunnelAfterModeBCommit`（commit 时 init 通常已就绪） |
| 进主页 / 语言页后的 preload | 直接 `preloadAd` |

---

## 7. 接入检查

```bash
# Splash 使用 runWhenSdkInitializedOnce，禁止 UMP 后立即 preload 开屏（旧写法）
rg "runWhenSdkInitializedOnce|scheduleSplashPreloadOnceWhenSdkReady" app/**/splash/
rg "requestSplashOnceAfterUmp" app/**/splash/   # 应无匹配（已更名）

# Splash 禁止再 init SDK
rg "MonetizationKit\.init" app/**/splash/       # 应无匹配

# Application 仅一处 init
rg "MonetizationKit\.init" --glob "*.kt"
```

**Logcat（冷启）**：

1. `【SDK初始化】AdMob MobileAds.initialize 完成 isInit=true`（1 条）
2. UMP 相关日志 → `开屏单次请求开始`（0 或 1 条；init 极慢且闸门不过可为 0）
3. `【预加载开始】…UMP后开屏单次请求`（至多 1 条 requestStart）

---

## 8. 关联文档

- [splash-loading.md](splash-loading.md) — 放行闸、obtainForShow
- [reference.md#sdk-单点初始化](reference.md#sdk-单点初始化) — MobileAds 单点 initialize
- [templates/sdk-init-callback-snippet.kt.template](templates/sdk-init-callback-snippet.kt.template) — 可复制片段
- [templates/splash-snippet.kt.template](templates/splash-snippet.kt.template) — 完整 Splash 管线
