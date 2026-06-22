# A→B 升面：页面分发（非后台整批补货）

> **一句话**：不在 Application / Splash **应用级**整批 `preloadAdAwait`；升 B 只 **通知当前 RESUMED 的前台页面**，各页在 `bindModeBAdGateWhileVisible` 里自行 preload/bind。

**金样**：`pdf/app/.../ads/ModeBAdGateLifecycle.kt`

---

## 1. 禁止（PDF 2026-06 定稿）

| 禁止项 | 原位置 | 原因 |
|--------|--------|------|
| commit 后 `preloadLanguageFunnelAfterModeBCommit` | `commitAbFace` | 后台替语言页要货 |
| commit 后 `schedulePreloadAfterLoadingWhenReady` | `commitAbFace` | 应用级整批补货 |
| A→B 后 `schedulePreloadAfterLoadingOnBootstrapComplete` | `notifyModeBUpgraded` | 应用级整批补货 |
| FC 后 `schedulePreloadAfterRemoteConfigRefresh` | `applyRemoteConfigCore` | FC 后再跑整批 B 面 request |
| **Splash Loading 批** `schedulePreloadAfterLoadingWhenReady` | `SplashLaunchPipeline` | 等 commit 后串行 await 全 B 位；与「页面分发」冲突 |

**Loading 批 ≠ UMP 批**（勿混）：

| | UMP 批（保留） | Loading 批（已删） |
|---|----------------|-------------------|
| 入口 | `preloadAfterUmpConsent` + 开屏单次 preload | `schedulePreloadAfterLoadingWhenReady` |
| 时机 | UMP 结束、SDK **首次** isInit | Splash 挂号 → **等 commit** → 串行 |
| 协程 | Splash **页面级**，并行 fire-and-forget | **applicationScope**，`preloadAdAwait` 串行 |
| 典型 scene | `UMP后预加载-*` / `UMP后开屏单次请求` | `Loading冷热启动结束-*` |

---

## 2. 仍保留的全局预加载（仅 UMP 批 + 页面/补货）

| 批次 | 触发 | 说明 |
|------|------|------|
| **UMP 批** | UMP + SDK 就绪，Splash 页面级并行 | 语言（未选语言）+ enter/back + 开屏 1 次 |
| **进主页** | `MainActivity` initView | `preloadOnMainEntry`、Banner 现场 load |
| **升 B 页面分发** | `bindModeBAdGateWhileVisible` | 仅 RESUMED 页 |
| **展示后补货** | `AdReplenishCoordinator` | 曝光消耗后再 preload |

---

## 3. 页面分发：`bindModeBAdGateWhileVisible`

```kotlin
bindModeBAdGateWhileVisible { /* 本页 preload / bind / Banner 重建 */ }
```

| 规则 | 说明 |
|------|------|
| 注册时机 | **onResume** 注册，**onPause** 移除 |
| 回调条件 | 仅 `lifecycle >= RESUMED` |
| 升 B 通知 | `notifyModeBUpgraded` 只 invoke 已注册监听 |

### 金样已接入页面

| 页面 | 升 B / RC 刷新时做什么 |
|------|------------------------|
| `LanguageActivity` | settings：`preloadLanguageAds`；bind 语言原生 |
| `MainActivity` | Banner 重建 + 收藏 Tab 大原生 preload |
| `ToolsFragment` | 工具页大原生 preload |
| `BookmarksFragment` | 刷新书签列表原生 |
| `ConvertFinishActivity` | bind 成功页大原生 |

---

## 4. 验收 Logcat

- **无** `Loading结束后台预加载已调度` / `Loading冷热启动结束-*`
- commit / A→B / FC 后 **无** Bootstrap 层 Coordinator 整批 preload
- 冷启可见 `UMP后预加载` / `UMP后开屏单次请求`

```bash
rg "schedulePreloadAfterLoading|Loading冷热启动结束|Loading结束后台" app/
# 应无匹配
```
