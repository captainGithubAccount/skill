# PDF 金样：全部广告 request 时机（大白话）

> 只列 **业务主动 preload/load**（Loader skip 不算 request）。  
> 与 [mode-b-page-gate.md](mode-b-page-gate.md)、[sdk-init-callback.md](sdk-init-callback.md)、[splash-loading.md](splash-loading.md) 配套。

---

## UMP 批 vs Loading 批（Loading 已删）

| | **UMP 批（保留）** | **Loading 批（已删）** |
|---|-------------------|------------------------|
| 是什么 | UMP 后 SDK 就绪，Splash 上 **并行** `preloadAd` | Splash 挂号后 **等 commit**，applicationScope **串行** `preloadAdAwait` |
| 代码 | `preloadAfterUmpConsent` + `requestSplashPreloadIfNeeded` | ~~`schedulePreloadAfterLoadingWhenReady`~~ |
| Logcat | `UMP后预加载-*`、`UMP后开屏单次请求` | ~~`Loading冷热启动结束-*`~~ |

---

## 冷启动（首次安装、未选语言）

| 顺序 | 什么时候 | 请求哪些 |
|------|----------|----------|
| 1 | UMP + SDK 首次就绪（**UMP 批**） | 语言插屏/原生；开屏 1 次（并行）；**不含 enter/back** |
| 2 | 语言页 **initView** | 主页折叠 **Banner**（隐藏容器提前 load） |
| 3 | 开屏放行闸 | **不新发** request，只读开屏缓存 show |
| 4 | 进语言页 onResume | bind 原生：`loadNativeForPageBind` / `bindNativeAdInstantIfNeeded`（无缓存 **页内即时 load**，不 400ms 轮询） |
| 5 | 进 Main initView | enter、底栏插屏、大原生；Banner 挂真实容器 |
| 6 | 中途升 B | 仅 RESUMED 页 listener 分发 |

**不再发生**：Loading 批、commit/A→B/FC 整批补 preload；UMP 批 enter/back。

---

## 冷启动（已配语言、直达主页）

| 顺序 | 什么时候 | 请求哪些 |
|------|----------|----------|
| 1 | UMP + SDK 就绪 | 开屏 + **主页 Banner**（与开屏同批） |
| 2 | 进 Main | enter、底栏、大原生；Banner 挂容器（可复用/补挂） |

---

## 语言页（飞书 b.）

| 场景 | 什么时候 request |
|------|------------------|
| 首次安装 | **UMP 批**语言两位 + **initView 主页 Banner 提前 requestLoad** + bind **页内即时 load** |
| 设置入口 | initView **b.ii** preload 语言两位（无 Banner） |
| 确认离开 | 插屏 ensure → 无货再 load；曝光后 **与其它插屏相同**走 `AdReplenishCoordinator` 补货 |
| 中途升 B | `bindModeBAdGateWhileVisible` |

---

## 进主页 / enter / back / Banner

| 广告位 | 预加载时机 | 展示 |
|--------|------------|------|
| **enter** | 进 Main `preloadOnMainEntry`；UMP 批 **不含** | `navigateWithEnterAd`；无缓存只跳转+补货 |
| **back** | **不进 Main 批、不进 UMP 批**；`navigateWithEnterAd` 进二级页前 `ApplicationAdRequests.preload` | `finishWithBackAd`；展示后补货 |
| **Banner** | ① 已配语言：Splash SDK 批 `preloadBannerOnSplashSdkReady` ② 首次语言页 initView `preloadBannerOnLanguagePage` ③ Main initView/onResume `requestLoad` + attach | Main onResume / Tab 切换复用；二级页返回 `forceReload` |
