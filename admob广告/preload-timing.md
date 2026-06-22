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
| 1 | UMP + SDK 首次就绪（**UMP 批**） | 语言插屏/原生、enter、back；开屏 1 次（并行） |
| 2 | 开屏放行闸 | **不新发** request，只读开屏缓存 show |
| 3 | 进语言页 onResume | bind 原生，**无货 b.iii** 才 preload |
| 4 | 中途升 B | 仅 RESUMED 语言页 listener 分发 |

**不再发生**：Loading 批、commit/A→B/FC 整批补 preload。

---

## 语言页（飞书 b.）

| 场景 | 什么时候 request |
|------|------------------|
| 首次安装 | **UMP 批**（若 commit 前闸门未过可能 skip B 位）+ bind 无货 **b.iii** |
| 设置入口 | initView **b.ii** preload 语言两位 |
| 确认离开 | 插屏 ensure → 无货再 load（b.iii） |
| 中途升 B | `bindModeBAdGateWhileVisible` |

---

## 进主页 / enter / back / 大原生 / 补货

见 [mode-b-page-gate.md](mode-b-page-gate.md) 与各页金样；进主页 `preloadOnMainEntry`；enter/back 路由与 `AdReplenishCoordinator` 不变。
