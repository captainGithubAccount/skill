# 应用级广告请求（PDF 金样 · 2026-06）

> **一句话**：广告的**展示**绑页面 lifecycle；广告的**请求（preload/load）**绑应用级 lifecycle + Application Context。

---

## 核心入口

| 代码 | 中文含义 | 何时用 |
|------|----------|--------|
| `ApplicationAdRequests.wire(scope, application)` | Application 启动时绑定请求 Scope | `MyApplication.onCreate`（在 `applicationScope` 赋值后） |
| `ApplicationAdRequests.preload(sense, scene)` | 应用级 fire-and-forget 预加载 | 业务页、Coordinator、补货 |
| `ApplicationAdRequests.preloadAwait(sense, scene)` | 应用级挂起预加载 | 需在协程内 await 时 |
| `ApplicationAdRequests.loadAwait(...)` | 应用级 load（开屏等） | `ActivityAdExt.loadAd` delegate |
| `FragmentActivity.preloadAd(...)` | 兼容旧 API | **内部 delegate** 到 `ApplicationAdRequests` |
| `AdRequestLog.requestInterrupted(...)` | 请求中断日志 | Loader cancel、Banner forceReload 等 |

**禁止**：新业务写 `lifecycleScope.launch { preloadAdAwait }` 作为预加载入口。

---

## Application 初始化（必做）

```kotlin
// MyApplication.onCreate
applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
ApplicationAdRequests.wire(applicationScope, this)
```

未 `wire` 时调用 `preload` 会抛错：`ApplicationAdRequests 未 wire`。

---

## 展示 vs 请求

| 类型 | Scope / Context | 典型 API |
|------|-----------------|----------|
| **请求** preload/load | `applicationScope` + `applicationContext` | `ApplicationAdRequests.preload`、Banner `MainBannerController.requestLoad` |
| **展示** show/bind | 页面 `lifecycleScope` + Activity 容器 | `showAd`、`bindNativeAd`、`MainBannerController.showCollapsibleBanner` |

页面 `onDestroy` **不应** cancel 进行中的 preload/load；展示层 `onDestroy` 仍清理 View / 原生 destroy。

---

## Banner 两阶段

| 阶段 | API | 说明 |
|------|-----|------|
| **应用级 load** | `MainBannerController.requestLoad(context, scene)` → `FloorziqAd.preloadCollapsibleBanner` | 语言页 initView、SDK 批；**不**绑语言页 ON_DESTROY |
| **页面 attach** | `MainBannerController.showCollapsibleBanner(...)` → `FloorziqAd.showBanner` | Main 容器挂载；destroy 仅 detach AdView |

`forceReload` → `AdRequestLog.requestInterrupted` + `FloorziqAd.clearAppScopeBanner()`。

---

## AdMob Helper 缓存 key

预加载使用 `AdmobManager.getAppScoped`（key=`AppScope`+adType），**不**再绑 `SplashActivity`/`LanguageActivity` 类名，避免页面 finish 后 Helper 与在途请求错位。

---

## 中断日志规范（`TAG-->>AdRequest`）

| 文案 | 含义 |
|------|------|
| `【预加载跳过】` | 未发起网络（闸门/去重） |
| `【预加载失败】` / `【加载失败】` | SDK 失败或超时 |
| **`【请求中断】`** | 已 `requestStart` 后被打断；含 **`原因=`** + **`中断时刻=`** |

Logcat 过滤：

```bash
adb logcat -s "TAG-->>AdRequest:I" "TAG-->>AdRequest:E"
```

---

## 接入 checklist（新增位时）

- [ ] 预加载调用 `ApplicationAdRequests.preload` 或 `activity.preloadAd`（delegate）
- [ ] 展示仍用 `showAd` / `bindNativeAd` / Banner attach
- [ ] Application 已 `ApplicationAdRequests.wire`
- [ ] 不在 `lifecycleScope` 内 await 多条 preload（开屏窗口除外由 Splash 管线控制）
- [ ] 可中断场景有 `【请求中断】` 日志（若业务会 cancel Job，须在方案中说明）

---

## 相关文档

- [preload-timing.md](preload-timing.md) — 各广告位 request 时机
- [SKILL.md](SKILL.md) — 总入口
- [checklist.md](checklist.md) — 验收清单
