# PDF 金样：全部广告 request 时机（大白话）

> 只列 **业务主动 preload/load**（Loader skip 不算 request）。  
> 与 [mode-b-page-gate.md](mode-b-page-gate.md)、[sdk-init-callback.md](sdk-init-callback.md)、[splash-loading.md](splash-loading.md) 配套。

---

## 冷启动（首次安装、未选语言）

| 顺序 | 什么时候 | 大白话 | 请求哪些 | 协程 |
|------|----------|--------|----------|------|
| 1 | UMP 同意 + SDK 第一次就绪 | 开屏页上 **同时** 按几个按钮 | 语言插屏/原生、enter、back；**另**开屏 1 次 | Splash **页面级**，并行 |
| 2 | 开屏管线挂号（与 1 同时） | 后台排队，**等 AB 面 commit 后** 一个一个要 | enter → back → 语言原生 → 语言插屏 → 底栏插屏 → 大原生 | **应用级**，串行 |
| 3 | 开屏放行 | 只 **读缓存** 展示开屏；无货直接跳语言页 | 开屏展示不新发 request | — |

**不再发生**：commit 完成 / A→B / FC 刷新 **不再** 额外挂 Loading 批或语言补货。

---

## 语言页（飞书 b.）

| 场景 | 什么时候 request |
|------|------------------|
| 首次安装 | 只靠 **2 Loading 批** + **onResume bind 原生**（b.iii 没货才 load） |
| 设置入口进页 | initView **preload** 语言两位（b.ii） |
| 确认离开 | 插屏 **ensure** → 没货再 load（b.iii） |
| 中途升 B（人正在语言页） | `bindModeBAdGateWhileVisible`：settings preload + bind 原生 |

---

## 进主页

| 什么时候 | 请求什么 |
|----------|----------|
| Main initView | enter、back、底栏插屏、大原生（`preloadOnMainEntry`）；收藏 Tab 大原生；折叠 Banner **现场 load** |
| 中途升 B（人正在主页） | Banner 重建 + 收藏 Tab 大原生 |
| Tab 切换 | 只 **show** 底栏插屏缓存，不 preload |

---

## enter / back（二级页）

| 什么时候 | 请求什么 |
|----------|----------|
| 进二级页 | 有缓存 show enter；无缓存直跳并 preload enter |
| enter 关广告后 | 补货 preload enter |
| 返回 | 同逻辑，back |

---

## 其它页面大原生

文件列表、工具页、收藏 Tab、转换成功页等：进页 / onResume / bind 无货时 preload；工具页升 B 仅在 **Tab RESUMED** 时分发。

---

## 展示成功后补货（保留）

插屏/开屏/原生 **曝光后** → `AdReplenishCoordinator` → `preloadAdAwait(展示消耗后预加载)`。与上面 **预加载编排** 不是同一类时机。
