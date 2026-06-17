---
name: ab-mode-judgment
description: >-
  Android A/B 面（Mode2）判断逻辑接入与迁移：Firebase Remote Config 总开关强制 A/B、
  代码子项检查（买量渠道/Google Play 安装）、兜底默认 A 面、Debug 强制 A/B（DebugAbOverride）、
  Bootstrap 产出 isModeB。当用户提到 A/B 面、Mode2、isModeB、DebugAbOverride、
  enable_mode2_with_video、审核面/买量面、强制 A/B 测试 时自动应用。
---

# A/B 面判断逻辑接入

将 **V3.1.0 定稿** 的 A/B 面规则以 `Mode2Utils` + `AppAdsBootstrap` 为核心接入 Android 项目，并与广告/通知/首页 URL 按 `isModeB` 分流。

参考实现：`videodownload` 项目 `app/src/main/java/com/isi/app/util/mode2/`。

## 产品规则（必须遵守）

1. **总开关 RC 存在且配置 B**（`enable_mode2_with_video == 1`）→ **强制 B**
2. **总开关 RC 存在且配置 A**（`enable_mode2_with_video == 0`）→ **强制 A**
3. **总开关 RC 不存在**，或 **存在但值既非 0 也非 1** → **走代码判断**
4. **代码判断不出**（有检查但存在失败 / 无可执行检查）→ **兜底 A**

### 代码判断细则

- **子项 RC key 不存在** → 视为开关 `=1`，**执行**对应代码检查
- **子项 RC 存在且 `=0`** → **跳过**该项
- **有执行过的检查且任一失败** → **A**；**全部通过** → **B**
- **禁止**：子项或总开关 RC 拉不到时 **强制 B**（旧版 `!exists(key) → return true` 必须删除）

## Debug 强制 A/B（仅 Debug 包）

**自然判定**仍走上文 `isMode2()`；**最终** `AppAdsBootstrap.isModeB` 由 `DebugAbOverride.resolveModeB(naturalModeB)` 决定。Release 包 `resolveModeB` 恒返回 `naturalModeB`。

### 优先级（高 → 低）

1. **`CODE_OVERRIDE_MODE`**（编译期常量，`DebugAbOverride.kt`）为 `FORCE_A` / `FORCE_B` → 强制 A/B，**忽略** MMKV 与 `naturalModeB`
2. **MMKV 弹窗**（`KEY_DEBUG_AB_OVERRIDE`）选 `FORCE_A` / `FORCE_B` / `NORMAL`
3. **`NORMAL`** → `isModeB = naturalModeB`

### 两种测试方式

| 方式 | 适用 | 说明 |
|------|------|------|
| 编译期常量 | 自动化 / 固定面联调 | 改 `CODE_OVERRIDE_MODE` 后重装；首页 AB 按钮隐藏 |
| 首页 AB 弹窗 | 手工切换 | 冷启动进首页显示按钮；选一项后 `restartApp` 并隐藏入口 |

### 关键行为

- 切回 **NORMAL**：`set(NORMAL)` 会 `Mode2Utils.resetCachedCheckForDebug()`，重启后重新完整判定
- **不修改** `isMode2()` / Mode2 缓存语义以外的自然逻辑；只覆盖最终 `isModeB`
- 业务分流（广告/通知/首页 URL）统一读 **`AppAdsBootstrap.isModeB`**，不读 `naturalModeB`

详见 [流程图.md §3](流程图.md#3-debug-专属debugaboverride-覆盖层)、[reference.md §DebugAbOverride](reference.md#debugaboverride-仅-debug-包)。

## 接入前：扫描目标项目

| 检查项 | 路径/关键词 | 含义 |
|--------|-------------|------|
| 核心判定 | `Mode2Utils` / `isMode2` | 是否已有 AB 逻辑 |
| Bootstrap | `AppAdsBootstrap.isModeB` | 是否已有全局结果 |
| 归因 | `AttributionManager` / `InstallationSourceChecker` | 买量渠道数据来源 |
| RC 拉取 | `FirebaseRemoteConfig.fetchAndActivate` | bootstrap 前是否拉 RC |
| Debug 覆盖 | `DebugAbOverride` | Debug 强制 A/B |
| A 面 UI | `HomeUrlPresets` / `shouldRemoveFromList` | 黄色网站入口过滤 |
| 分流 | `applyByMode` / `AppAdsBootstrap.isModeB` | 广告/通知方案 |

**分支：**

- 无 Mode2 → [全新接入](#全新接入工作流)
- 有 Mode2 但含 `!exists(...) return true` → [迁移旧逻辑](#迁移旧逻辑)
- 已有 V3.1.0 逻辑 → 核对 [checklist.md](checklist.md)

## 全新接入工作流

### Step 1：Remote Config Key

在 Firebase 控制台配置（或使用本地 default）：

| Key | 角色 | 值 |
|-----|------|-----|
| `enable_mode2_with_video` | **总开关** | `1`=强制 B，`0`=强制 A；**不存在**或**存在但值≠0且≠1**则进代码判断 |
| `enable_installation_source_condition` | 代码子项：买量渠道 | 不存在视为 `1`；`0` 跳过 |
| `enable_installed_from_google_play_condition` | 代码子项：GP 安装 | 不存在视为 `1`；`0` 跳过 |

`exists(key)` 实现：`FirebaseRemoteConfig.getValue(key).source != VALUE_SOURCE_STATIC`（服务端曾下发过该 key）。

### Step 2：复制核心类

按目标项目包名替换 `{app_package}`：

| 模板 | 目标 |
|------|------|
| [templates/Mode2Utils-isMode2.kt.template](templates/Mode2Utils-isMode2.kt.template) | `{app_package}/util/mode2/Mode2Utils.kt` 中 `isMode2` 段 |
| 参考 [reference.md](reference.md#完整判定流程) | `initMode2AndGet`、MMKV 缓存、`InstallationSourceChecker` |
| [templates/DebugAbOverride-snippet.kt.template](templates/DebugAbOverride-snippet.kt.template) | `DebugAbOverride` 覆盖层 |

必备伴随类（可从参考项目复制并改包名）：

- `AppAdsBootstrap` — 拉 RC → `Mode2Utils.initMode2AndGet` → `isModeB`
- `InstallationSourceChecker` + `AttributionManager` — 买量标记 `KEY_INSTALL_FROM_AD`
- `DebugAbOverride` + `DebugAbOverrideDialog`（**Debug 必接**；Release 无影响）
- `HomeUrlPresets` — A 面隐藏 Xvideos/Pornhub

### Step 3：Bootstrap 初始化

在 `Application` 或 `StartActivity` 冷启动路径（**拉 RC 之后**）：

```kotlin
val naturalModeB = Mode2Utils.initMode2AndGet(context)
val isModeB = DebugAbOverride.resolveModeB(naturalModeB) // Release：等同 naturalModeB
AppAdsBootstrap.isModeB = isModeB
AdRemoteConfigBridge.applyByMode(context, isModeB)
// 通知等同理 applyByMode
```

Debug 包在 `StartActivity` / `HomeFragment` 接入 `onStartActivityCreate`、首页 AB 按钮与弹窗（见 reference）。

热启动回前台：**不要**重复判定，沿用进程内 `isModeB`。

### Step 4：业务分流

凡 A/B 差异统一读 `AppAdsBootstrap.isModeB`，典型分流见 [reference.md#ab-面业务分流](reference.md#ab-面业务分流)。

### Step 5：验证

完成 [checklist.md](checklist.md)，对照 [流程图.md](流程图.md) 核对 Release/Debug 路径，对改动文件执行 **Lint**（不编译）。

## 迁移旧逻辑

若发现以下反模式，**必须删除**：

```kotlin
// ❌ 旧版：RC 不存在强制 B
if (!exists(enable_mode2_with_video)) return true
if (!exists(enable_installation_source_condition)) return true
```

替换为 [templates/Mode2Utils-isMode2.kt.template](templates/Mode2Utils-isMode2.kt.template) 中的 V3.1.0 逻辑，并新增 `isCodeSubCheckEnabled(key)`。

## 架构

```
AppAdsBootstrap.run()
  ├── Firebase RC fetch（≤8s）
  ├── Mode2Utils.initMode2AndGet()
  │     ├── InstallationSourceChecker（等待归因）
  │     └── isMode2() → naturalModeB
  ├── DebugAbOverride.resolveModeB()（仅 Debug）
  └── applyByMode(isModeB) → 广告 / 通知 / 首页预设
```

## 关键约定

1. **总开关**与**代码子项** RC 语义不同：总开关不存在或值非 0/1 才进代码段；子项不存在视为开启
2. **代码 B 条件**：有执行过的检查且**全部通过**；**任一失败** → A
3. **默认 A**：代码段有失败 / 无可执行检查 → A
4. **Debug 与 Release 共用 `isMode2()`** → `naturalModeB`；Debug 另用 `DebugAbOverride` 得到最终 `isModeB`
5. **Debug 强制面**：不改自然判定代码；`FORCE_A`/`FORCE_B` 时 `isModeB` 与 `naturalModeB` 可相反
6. **缓存**：曾判 B 或 `checkCount > 4` 后跳过重检；Debug 切 NORMAL 应清 Mode2 缓存（见 reference）
7. 新增类/方法注释使用**简体中文**
8. **广告兜底**：广告位是否展示以 [fb skill](../fb的ab面远程配置拉取/SKILL.md#禁止擅自扩展兜底ai--接入必读) 为准；**禁止**在 A/B 分流之外私加 BuildConfig 等兜底（A 面 JSON 没有的位必须不展示）

## 详细参考

- **A/B 面广告远程配置（ad_config_a / ad_config_b）**：[fb的ab面远程配置拉取](../fb的ab面远程配置拉取/SKILL.md)
- **流程图（含 Release / Debug / 强制覆盖）**：[流程图.md](流程图.md)
- 判定流程、Debug 详解、场景表：[reference.md](reference.md)
- Debug 代码模板：[templates/DebugAbOverride-snippet.kt.template](templates/DebugAbOverride-snippet.kt.template)
- 接入检查清单：[checklist.md](checklist.md)
