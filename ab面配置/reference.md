# A/B 面配置 — 技术参考

**现行金样**：`/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf`  
**对照**：`videodownload v1.2.0`

## 架构总览

```
Application.onCreate
  ├─ [协程A] AppAdsBootstrap.run → AbSettlementCoordinator.startSettlement
  │     ├─ FC 轨：runFcFetchAndApply（8s×最多3次）→ onFirebaseFetchCompleted
  │     └─ AB 轨：AttributionManager.init（阶段1 6s）
  │           → awaitFcReadyForMode2（30s）
  │           → resolveNaturalModeBAfterFc
  │           → commitAbFace
  │           → scheduleBRemoteConfigRetryIfNeeded（B 面远程未生效时）
  │           → startExtendedReferrerPhaseIfNeeded（阶段2）
  └─ [协程B] applyDefaultLocalAssetsA + MonetizationKit.init（与 AB 并行）
```

videodownload 差异：Application 内 `AppAdsBootstrap.run` 嵌在 `MonetizationKit.init` 回调；FC **单次**拉取；无 B 面补拉；含通知 `applyByMode`。

## FC 与归因

### 双超时

| 层 | 阈值 | 超时后 |
|----|------|--------|
| FC 单次 fetch | 8000ms | 计入失败，可进入下一次重试 |
| 等 FC（Mode2 前） | 30000ms | 强制 fcReady，用 RC 快照判 Mode2 |

### PDF 金样：FC 三次重试

| 次数 | 拉取前等待 |
|------|------------|
| 1 | 0 |
| 2 | 2000ms |
| 3 | 5000ms |

任一次 `fetchAndActivate` 成功即结束 FC 轨；三次均失败仍 `fcReady=true`，走 SDK 磁盘缓存 + 已铺 A assets。

实现可委托 `AppRemoteConfig.fetchAndActivateWithDetail()` 返回 `FetchResult(success, elapsedMs, errorMessage)` 便于 Logcat。

### PDF 金样：B 面远程补拉

触发：`commitAbFace` 后 `isModeB==true` 且 `!AdRemoteConfigBridge.isRemoteBConfigApplied()`。

| 次数 | 等待 |
|------|------|
| 1 | 0 |
| 2 | 3000ms |
| 3 | 10000ms |
| 4 | 30000ms |

每次补拉后调用 `onFirebaseFetchCompleted`；远程 B 生效或次数用尽即停。

### 「缓存 RC」读源

| 来源 | 内容 |
|------|------|
| Firebase SDK 磁盘 | 上次 fetchAndActivate **成功**的 REMOTE 值 |
| setDefaultsAsync | 仅 `notification_config`（可选模块） |
| applyDefaultLocalAssetsA | 广告 A assets → **AdRemoteConfigManager 内存** |
| Mode2 数值 key | 无 assets 默认；未 REMOTE → exists=false |

**8s 超时不会那时才加载 assets**；assets 在 Application 协程 B 已铺底。

### commit 时刻

`max(阶段1结束, fcReady时刻)`，AB 等 FC 不超过 30s。

## 归因阶段1

- 超时：`referrer_timeout_ms` 默认 6000，maxAttempts=3
- 进阶段2：`shouldStartExtendedRetry` — 超时/null、fetchFailed、空串 `""`
- 不确定：refer=`""`，`EXTENDED_PENDING=true`，`deferFinalResult=true`

## 归因阶段2

### 启动（commit 之后）

```
needsExtendedReferrerRetry():
  EXTENDED_DONE == false
  && EXTENDED_PENDING == true
  && (now - CHAIN_START) < 15min
```

### 阶梯

`RETRY_SCHEDULE_MS = [30_000, 120_000, 300_000, 840_000]`，单次 fetch 4500ms。

| 单次结果 | 行为 |
|----------|------|
| 非空 refer | applyReferrerUpdate → onReferrerResolved → runExtendedMode2UpdateIfNeeded |
| 空/失败/超时 | retryIndex++，telemetry |
| 15min 到 | markExtendedDone，终局打点，面别不变 |

### 修订

- `updateLockedNaturalModeBIfChanged`：仅 A→B
- `applyModeUpdateAfterExtended`：刷新广告 RC + notifyModeBUpgraded / modeSideChanged

## Mode2 判面

`exists(key)` := `getValue(key).source != VALUE_SOURCE_STATIC`

子项：`isCodeSubCheckEnabled` — 未配=true，=0 跳过，=1 参与。

**PDF 现行 evaluateMode2 启用项**：

1. `enable_installation_source_condition` → ReferrerSideParser 买量/B
2. `enable_installed_from_google_play_condition` → GP installer

其余（模拟器、IP、强制 A 时段、中国区等）代码保留但 PDF 金样中已注释，新项目按需打开。

## MMKV Keys

| Key | 用途 |
|-----|------|
| KEY_MODE2_RESOLVED | 是否已锁定 |
| KEY_NATURAL_MODE_B | 自然 B 结果 |
| KEY_NATURAL_MODE_B_LOCKED | 自然 B 永久锁 |
| KEY_ATTRIBUTION_EXTENDED_PENDING | 阶段2 待重试 |
| KEY_ATTRIBUTION_EXTENDED_DONE | 阶段2 结束 |
| KEY_ATTRIBUTION_RETRY_INDEX | 阶梯 index |
| KEY_ATTRIBUTION_CHAIN_START_WALL_MS | 15min 起点 |
| KEY_REFERRER_RAW | refer 串 |

具体字符串见项目 `Config` / `data.Config`。

## canShowAd

- 未 commit：`currentIsModeB()` 恒 false
- B 专属位：**按项目**在 bootstrap 登记（PDF 见 `modeBExclusive` Set）
- 开屏：非 B 专属

## Bootstrap 监听（PDF）

| API | 用途 |
|-----|------|
| `addOnModeBUpgradedListener` | A→B 或 commit 即为 B |
| `addOnModeSideChangedListener` | 任意面别变化 |
| `addOnAdRemoteConfigRefreshedListener` | FC/commit 后广告 RC 刷新，UI 重绑 |
| `emitShowModelBIfNeeded` | 首次 B 面 `show_model_b` 埋点 |

## 热启动 fast path

```kotlin
suspend fun run(context: Context, hotResumeFastPath: Boolean = false)
```

`hotResumeFastPath==true` 时跳过 settlement（本进程已 commit）。  
videodownload：`StartActivity` 传入 `isEffectiveHotStart()`。  
PDF 金样：Application 始终 `run(context)` 无参；接口预留供启动页接线。

## DebugAbOverride（PDF）

- `CODE_OVERRIDE_MODE`：编译期 FORCE_A / FORCE_B / NORMAL
- `applyForcedLockIfNeeded()`：settlement 前写入 Mode2 锁定
- `resolveModeB(natural)`：commit 时覆盖对外面

## 场景速查

| 场景 | 结果 |
|------|------|
| 买量+GP，阶段1 成功 | B |
| 阶段1 超时 | 先 A，可能阶段2 升 B |
| FC 8s 超时 ×3 | 仍 fcReady；广告用 assets + SDK 缓存 |
| B commit 后远程 B 空 | 后台补拉最多 3 次 |
| 总开关=22 | 不强制，走子项 |
| 自然 B 锁定 + 总开关=0 | 保持 B |

## 参考文件（PDF）

| 文件 | 路径 |
|------|------|
| AbSettlementCoordinator | `app/.../util/mode2/AbSettlementCoordinator.kt` |
| PdfAppAdsBootstrap | `app/.../bootstrap/PdfAppAdsBootstrap.kt` |
| Mode2Utils | `app/.../util/mode2/Mode2Utils.kt` |
| AttributionManager | `app/.../util/mode2/AttributionManager.kt` |
| AppRemoteConfig | `app/.../config/AppRemoteConfig.kt` |
| MyApplication | `app/.../MyApplication.kt` |
