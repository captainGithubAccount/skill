# A→B 升面：页面分发（非后台整批补货）

> **一句话**：AB commit / FC 刷新 **不再**在 `PdfAppAdsBootstrap` 里整批 `preloadAd`；升 B 只 **通知当前 RESUMED 的前台页面**，各页在 `bindModeBAdGateWhileVisible` 里自行 preload/bind。

**金样**：`pdf/app/.../ads/ModeBAdGateLifecycle.kt`

---

## 1. 禁止（PDF 2026-06 定稿）

| 禁止项 | 原位置 | 原因 |
|--------|--------|------|
| commit 后 `preloadLanguageFunnelAfterModeBCommit` | `commitAbFace` | 后台替语言页要货，GA sense 归属乱 |
| commit 后 `schedulePreloadAfterLoadingWhenReady` | `commitAbFace` | 与 Splash 已挂号重复 |
| A→B 后 `schedulePreloadAfterLoadingOnBootstrapComplete` | `notifyModeBUpgraded` | 应用级整批补货 |
| FC 后 `schedulePreloadAfterRemoteConfigRefresh` | `applyRemoteConfigCore` | 等同再跑一轮 Loading 批（enter/back/语言/底栏/大原生） |

**③ FC 刷新批是什么（已删除）**：Firebase 拉到 `pdf_ad_config_b` 后，在 **applicationScope** 里 **串行 await** 再 preload 一批 B 面位（enter → back → 语言 → 底栏 → 大原生），**不是**只刷新 JSON，而是 **又打一轮网络 request**。

---

## 2. 仍保留的全局预加载（仅两条线）

| 批次 | 触发 | 协程 | 说明 |
|------|------|------|------|
| **UMP 批** | UMP + SDK 就绪 | Splash **页面级** `preloadAd` 并行 | 语言（未选语言）+ enter/back + 开屏单次 |
| **Loading 批** | Splash 管线 **挂号一次** | **应用级** `preloadAdAwait` 串行 | 等 commit 后 enter→back→语言→底栏→大原生；**不由 commit 再挂** |

---

## 3. 页面分发：`bindModeBAdGateWhileVisible`

```kotlin
// Activity.onCreate（super 之后）或 Fragment.initView
lifecycle.addObserver(ModeBAdGateLifecycle(...))
// 或
bindModeBAdGateWhileVisible { /* 本页 preload / bind / Banner 重建 */ }
```

| 规则 | 说明 |
|------|------|
| 注册时机 | **onResume** 注册监听，**onPause** 移除（页面不可见不收广播） |
| 回调条件 | 仅当 `lifecycle >= RESUMED` 才执行 `onGateReady` |
| 升 B 通知 | `notifyModeBUpgraded` **只** `invoke` 已注册监听，**不再**调 Coordinator 整批 preload |

### 金样已接入页面

| 页面 | 升 B / RC 刷新时做什么 |
|------|------------------------|
| `LanguageActivity` | settings：`preloadLanguageAds`；bind 语言原生 |
| `MainActivity` | Banner 重建 + 收藏 Tab 大原生 preload |
| `ToolsFragment` | 工具页大原生 preload + 刷新列表 |
| `BookmarksFragment` | 刷新书签列表原生行 |
| `ConvertFinishActivity` | bind 成功页大原生 |

---

## 4. 与曝光补货区分

| 类型 | 触发 | 是否保留 |
|------|------|----------|
| **页面升 B 分发** | commit / RC → 前台页面 listener | ✅ 上节 |
| **展示消耗后补货** | `AdReplenishCoordinator` / enter·back 展示后 preload | ✅ 不变 |

---

## 5. 验收 Logcat

- commit 后 **无** `B面commit后补预加载` / `FC配置刷新后补 B 面预加载` / `Loading结束后台预加载已调度`（来自 commit 路径）
- 升 B 时若用户 **不在** 语言/主页：**无** 该页 scene 的 preload
- 升 B 时若用户 **正在** 语言页 RESUMED：可见 `设置入口-语言页预加载` 或 bind 相关日志

```bash
rg "B面commit|schedulePreloadAfterLoadingOnBootstrapComplete|schedulePreloadAfterRemoteConfigRefresh" app/
# 应无调用（Coordinator 内方法可删除）
```
