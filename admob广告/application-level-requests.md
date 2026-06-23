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
| **展示** show/bind | 页面 `lifecycleScope` + Activity 容器 | `showAd`、`bindNativeAdInstantIfNeeded` / `loadNativeForPageBind`、`MainBannerController.showCollapsibleBanner` |

页面 `onDestroy` **不应** cancel 进行中的 preload/load；展示层 `onDestroy` 仍清理 View / 原生 destroy。

---

## Banner 两阶段（PDF 金样 · 与项目一致）

| 阶段 | API | 触发时机（scene 示例） |
|------|-----|------------------------|
| **应用级 load** | `MainBannerController.requestLoad(context, scene)` | ① 已配语言冷启：`SDK就绪-直达主页Banner`（Splash SDK 批） ② 首次语言页 initView：`语言页-折叠Banner预加载` ③ Main initView/onResume：`进入主页-折叠Banner预加载` / `主页Banner-onResume展示` |
| **页面 attach** | `MainBannerController.showCollapsibleBanner(...)` | Main 容器挂载；`bannerReady` 复用；`loadInFlight` 时 400ms 后 attach（**UI 等待**，非原生 bind 重试） |
| **强制刷新** | `forceReload=true` | 二级页返回 `主页Banner-返回热启重建`；AB 升 B `主页Banner-AB面升级重建` → `requestInterrupted` + `clearAppScopeBanner` |

语言页 destroy 时 `MainBannerController.onEarlyLoadHostDestroyed()`；load **不**绑 Language ON_DESTROY。

---

## 原生 instant bind（2026-06）

| API | 用途 |
|-----|------|
| `loadNativeForPageBind` | suspend：缓存 → 页内 load（不补货）→ preloadAdAwait |
| `bindNativeAdInstantIfNeeded` | 列表/二级页封装 bind |
| `bindNativeAd` | **仅缓存**（ConvertFinish 等） |

进页 `preloadAd` 仍保留；**禁止** 400ms postDelayed bind 重试环。

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
- [ ] 展示仍用 `showAd` / `bindNativeAdInstantIfNeeded` / `loadNativeForPageBind` / Banner attach
- [ ] Application 已 `ApplicationAdRequests.wire`
- [ ] 不在 `lifecycleScope` 内 await 多条 preload（开屏窗口除外由 Splash 管线控制）
- [ ] 可中断场景有 `【请求中断】` 日志（若业务会 cancel Job，须在方案中说明）

---

## 相关文档

- [preload-timing.md](preload-timing.md) — 各广告位 request 时机
- [SKILL.md](SKILL.md) — 总入口
- [checklist.md](checklist.md) — 验收清单
