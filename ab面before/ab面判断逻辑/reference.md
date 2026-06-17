# A/B 面判断 — 详细参考

> 可视化流程见 **[流程图.md](流程图.md)**（含 Release / Debug 完整流程图）。

## 目录

1. [完整判定流程](#完整判定流程)
2. [Remote Config Key](#remote-config-key)
3. [exists 与 isCodeSubCheckEnabled](#exists-与-iscodeSubcheckenabled)
4. [代码检查项](#代码检查项)
5. [场景对照表](#场景对照表)
6. [缓存与跳过重检](#缓存与跳过重检)
7. [Bootstrap 与 Debug](#bootstrap-与-debug)
8. [DebugAbOverride（仅 Debug 包）](#debugaboverride-仅-debug-包)
9. [A/B 面业务分流](#ab-面业务分流)
10. [埋点](#埋点)

---

## 完整判定流程

```
initMode2AndGet(context)
  │
  ├─ await InstallationSourceChecker（≤6s，等待 AttributionManager FINAL）
  │
  ├─ 缓存短路？
  │    ├─ MMKV 上次 isMode2 == true  → 直接返回 B
  │    └─ checkCount > 4             → 直接返回上次结果
  │
  └─ isMode2(context)
        │
        ├─【阶段 1】总开关 enable_mode2_with_video
        │    ├─ exists && value==1  → return true  （强制 B）
        │    ├─ exists && value==0  → return false （强制 A）
        │    └─ !exists 或 exists 且 value≠0 且 value≠1 → 进入阶段 2
        │
        ├─【阶段 2】代码判断（总开关未强制 A/B 时进入；Release / Debug 相同）
        │    │
        │    ├─ 买量渠道（isCodeSubCheckEnabled(installation_source)）
        │    │    isInstallFromAd() == false → anyCheckFailed = true
        │    │
        │    ├─ GP 安装（isCodeSubCheckEnabled(google_play)）
        │    │    isInstalledFromGooglePlay() == false → anyCheckFailed = true
        │    │
        │    ├─ hasApplicableCheck && anyCheckFailed → return false （任一失败 → A）
        │    └─ hasApplicableCheck && !anyCheckFailed → return true （全部通过 → B）
        │
        └─【阶段 3】兜底 → return false （默认 A）
```

---

## Remote Config Key

| Key | 常量名 | 层级 | 不存在时 | =1 | =0 | 存在且值≠0且≠1 |
|-----|--------|------|----------|----|----|------------------|
| `enable_mode2_with_video` | 总开关 | 强制层 | 进代码判断 | 强制 B | 强制 A | 进代码判断 |
| `enable_installation_source_condition` | 买量子项 | 代码层 | **视为 1，执行检查** | 执行检查 | 跳过 |
| `enable_installed_from_google_play_condition` | GP 子项 | 代码层 | **视为 1，执行检查** | 执行检查 | 跳过 |

以下为历史 key，**当前默认注释不参与判定**（可按需恢复）：

- `enable_forced_a_time_condition`
- `enable_security_ip_condition`
- `enable_emulator_condition`
- `enable_debug_mode_condition`
- `enable_probably_in_china_condition`

---

## exists 与 isCodeSubCheckEnabled

```kotlin
/** RC 是否由服务端下发（非 Firebase 静态默认值） */
private fun exists(key: String): Boolean {
    val value = FirebaseRemoteConfig.getInstance().getValue(key)
    return value.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC
}

/**
 * 代码子项开关：key 不存在视为开启；存在时仅 value==1 才执行检查。
 */
private fun isCodeSubCheckEnabled(key: String): Boolean {
    if (!exists(key)) return true
    return getFirebaseRemoteConfigWithLong(key) == 1L
}
```

**注意**：总开关用 `isMasterSwitchForceB()` / `isMasterSwitchForceA()`（仅当 RC 存在且值严格为 1 或 0 才强制）；其余情况进代码段。代码子项用 `isCodeSubCheckEnabled`。

```kotlin
private fun getMode2MasterSwitchValue(): Long =
    getFirebaseRemoteConfigWithLong(enable_mode2_with_video)

private fun isMasterSwitchForceB(): Boolean =
    exists(enable_mode2_with_video) && getMode2MasterSwitchValue() == 1L

private fun isMasterSwitchForceA(): Boolean =
    exists(enable_mode2_with_video) && getMode2MasterSwitchValue() == 0L
```

---

## 代码检查项

### 买量渠道

- **开关**：`isCodeSubCheckEnabled(enable_installation_source_condition)`
- **检查**：`InstallationSourceChecker.isInstallFromAd()`
- **数据来源**：`AttributionManager` 终态写入 `MMKV KEY_INSTALL_FROM_AD`
- **失败**：`false` → 计入 `anyCheckFailed`

### Google Play 安装

- **开关**：`isCodeSubCheckEnabled(enable_installed_from_google_play_condition)`
- **检查**：`isInstalledFromGooglePlay(context)`
- **逻辑**：`installingPackageName == "com.android.vending"`（API 30+）或 `getInstallerPackageName`（旧 API）
- **失败**：`false` → 计入 `anyCheckFailed`

### B 面代码结论

```kotlin
if (hasApplicableCheck && anyCheckFailed) return false  // 任一失败 → A
if (hasApplicableCheck && !anyCheckFailed) return true   // 全部通过 → B
return false  // 兜底 A
```

---

## 场景对照表

| 总开关 RC | 总开关值 | 买量检查 | GP检查 | 结果 |
|-----------|----------|----------|--------|------|
| 存在 | 1 | — | — | **B**（强制） |
| 存在 | 0 | — | — | **A**（强制） |
| 存在 | 非 0 非 1（如 2） | ✅ | ✅ | **B**（代码判断） |
| 存在 | 非 0 非 1 | ✅ | ❌ | **A**（代码判断） |
| 不存在 | — | ✅ | ❌ | **A** |
| 不存在 | — | ❌ | ✅ | **A** |
| 不存在 | — | ✅ | ✅ | **B** |
| 不存在 | — | ❌ | ❌ | **A**（兜底） |
| 不存在 | — | 子项全跳过（存在且=0） | — | **A**（兜底） |

买量/GP 列中 ✅/❌ 表示该项**已执行**且通过/未通过。

---

## 缓存与跳过重检

MMKV 字段（示例命名，可按项目 `Config` 调整）：

| Key | 含义 |
|-----|------|
| `KEY_IS_MODE2_CHECKED` | 上次判定是否为 B |
| `KEY_MODE2_CHECK_COUNT` | 累计检测次数 |

```kotlin
fun notCheck(): Boolean = isMode2 || checkCount > 4
```

- 上次已判 **B** → 后续启动不再重检（保持 B）
- `checkCount > 4` → 使用上次结果，避免无限重试

Debug 切回 NORMAL 时应调用 `resetCachedCheckForDebug()` 清空缓存。

---

## Bootstrap 与 Debug

### AppAdsBootstrap（参考）

```kotlin
suspend fun run(context: Context, hotResumeFastPath: Boolean = false) {
    if (hotResumeFastPath || configAppliedThisProcess) return

    // 1. 拉 Firebase RC（建议 ≤8s 超时）
    NotificationRemoteConfig.fetchActivateAndLog(...)

    // 2. AB 判定
    val naturalModeB = Mode2Utils.initMode2AndGet(context)
    isModeB = DebugAbOverride.resolveModeB(naturalModeB)

    // 3. 分流
    AdRemoteConfigBridge.applyByMode(context, isModeB)
    NotificationRemoteConfigBridge.applyByMode(context, isModeB)

    configAppliedThisProcess = true
}
```

Release 包：`resolveModeB(natural)` 直接返回 `natural`，不读 MMKV。

---

## DebugAbOverride（仅 Debug 包）

> 模板：[templates/DebugAbOverride-snippet.kt.template](templates/DebugAbOverride-snippet.kt.template)

### 职责

| 方法 | 作用 |
|------|------|
| `resolveModeB(naturalIsModeB)` | 产出最终是否 B 面；**业务只读 `AppAdsBootstrap.isModeB`** |
| `get()` | 当前生效的 Debug 模式（NORMAL / FORCE_A / FORCE_B） |
| `set(mode)` | 写 MMKV；NORMAL 时清 Mode2 检测缓存 |
| `onStartActivityCreate` | 控制冷启动是否再次展示首页 AB 按钮 |
| `shouldShowHomeDebugAbButton` | 首页 AB 入口可见性 |
| `restartApp` | 清栈重启 `StartActivity`，带 `EXTRA_DEBUG_AB_RESTART` |

### 优先级

```
1. CODE_OVERRIDE_MODE != NORMAL   → 强制 A/B，忽略 MMKV 与 naturalModeB
2. MMKV KEY_DEBUG_AB_OVERRIDE     → FORCE_A / FORCE_B / NORMAL
3. NORMAL                         → isModeB = naturalModeB
```

### MMKV / 常量

| Key | 含义 |
|-----|------|
| `Config.KEY_DEBUG_AB_OVERRIDE` | 弹窗选择的 Mode.ordinal |
| `Config.KEY_DEBUG_AB_ENTRY_DISMISSED` | 首页 AB 按钮是否已点过并隐藏 |
| `StartActivity.EXTRA_DEBUG_AB_RESTART` | `restartApp` 拉起，避免再次清除 dismissed |

### Mode 枚举

| Mode | `resolveModeB` 结果 | 说明 |
|------|---------------------|------|
| `NORMAL` | `naturalIsModeB` | 走完整 Mode2 自然判定 |
| `FORCE_A` | `false` | 强制 A，用于测审核包广告/通知/UI |
| `FORCE_B` | `true` | 强制 B，用于测买量包能力 |

### 编译期强制（`CODE_OVERRIDE_MODE`）

```kotlin
@JvmField
val CODE_OVERRIDE_MODE: Mode = Mode.NORMAL  // 改为 FORCE_A / FORCE_B 后重装
```

- `isCodeOverrideActive()` 为 true 时：**隐藏**首页 AB 按钮，弹窗与 MMKV 不覆盖常量。
- 适合 CI、固定面联调，无需点 UI。

### 首页弹窗流程（参考 videodownload）

1. `HomeFragment`：`shouldShowHomeDebugAbButton()` 为 true 时展示 AB 按钮。
2. 点击 → `DebugAbOverrideDialog`：FORCE_A / FORCE_B / NORMAL。
3. `set(selected)`；若与 `get()` 不同 → `restartApp(context)`。
4. `markHomeDebugAbEntryConsumed()` 隐藏按钮。
5. `StartActivity` 冷启动：`DebugAbOverride.onStartActivityCreate(intent)`（仅进程内首次）。

### 与 Mode2 缓存的关系

- **FORCE_A / FORCE_B**：不修改 `isMode2()` 内部逻辑；`naturalModeB` 仍会按 RC/代码算出来，只是最终 `isModeB` 被覆盖。
- **`set(NORMAL)`**：调用 `Mode2Utils.resetCachedCheckForDebug()`，避免沿用旧 B 面缓存导致自然判定失真。
- Logcat（Debug bootstrap）：`naturalIsModeB=... -> isModeB=...`（见 `AppAdsBootstrap`）。

### Release 行为

所有 `DebugAbOverride` 方法在 `!BuildConfig.DEBUG` 时 no-op 或返回安全默认；**Release 与未接入该类等价**。

---

## A/B 面业务分流

统一读取 `AppAdsBootstrap.isModeB`（`true` = B 面）：

| 模块 | A 面 | B 面 |
|------|------|------|
| 首页快捷网址 | 隐藏 Xvideos / Pornhub | 展示 |
| 首页原生广告 | 隐藏 | `HOME_NATIVE` |
| 广告 Remote Config | A 方案（仅开屏） | 全广告位 |
| 底部导航插屏 | 不展示 | 每 2 次切换 |
| 常驻通知 Idle | 禁止 | 允许 |
| 定时召回通知 | 禁止 | 允许 |
| 引导页/语言页大原生 | 无 | 有 |

首页 URL 过滤示例：

```kotlin
fun shouldRemoveFromList(url: String, isModeB: Boolean): Boolean {
    if (!isModeB && (url == XVIDEOS_URL || url == PORNHUB_URL)) return true
    return false
}
```

---

## 埋点

Bootstrap 完成后（`AttributionManager.emitAbTelemetryAfterBootstrap`）：

| 事件 | 条件 |
|------|------|
| `ab_lock_final_b` | 判定为 B，仅一次 |
| `ab_update_override_a` | 判定为 A 但 referrer 为买量渠道，仅一次 |
