---
name: ab-mode-ad-remote-config
description: >-
  Android A/B 面广告方案 Firebase Remote Config：先应用 assets 本地 JSON、
  fetch 后远程 ad_config_a/b 覆盖本地、按 isModeB 生效。当用户提到 ad_config_a、
  ad_config_b、广告远程配置、AdRemoteConfigBridge、ad_remote_config_default 时自动应用。
---

# A/B 面广告远程配置拉取

仅覆盖 **A 面 / B 面广告 JSON** 的 Firebase Remote Config 拉取与生效，**不包含**通知配置、Mode2 判定 key。

- **面判定**：见 [ab面判断逻辑](../ab面判断逻辑/SKILL.md)（产出 `isModeB` 后再应用广告配置）。
- **Meta 投放事件**：见 [fb推广](../fb推广/SKILL.md)（与 `ad_config_*` 无耦合）。
- **参考实现**：`videodownload` → `AdRemoteConfigBridge`、`FirebaseRemoteConfigFetcher`、`AppAdsBootstrap`。

## Firebase 参数（仅 2 个）

| 参数 | 类型 | 说明 |
|------|------|------|
| `ad_config_a` | String（JSON 全文） | A 面广告方案 |
| `ad_config_b` | String（JSON 全文） | B 面广告方案 |

## 生效顺序（单面，videodownload 定稿）

**先本地，后远程覆盖**（`applyByMode` 内两步，非「远程空才读 assets」）：

```
1. assets/ad_remote_config_default_*.json（按候选顺序）→ 立即 apply
2. Firebase ad_config_a 或 ad_config_b（fetch 已完成后 getString）
     ├─ 非空且可 parse → 覆盖步骤 1
     └─ 空或 parse 失败 → 保留步骤 1
3. assets 与远程均不可用 → AdRemoteConfig.parse(null) 内置兜底，再尝试步骤 2 覆盖
```

**禁止**在以上链路外再叠加 BuildConfig / 按 ad_type 回退等第四级兜底（见下文专节）。

## bootstrap 编排（AppAdsBootstrap）

```
fetchAndActivate（≤8s，与通知 RC 共用）
  → Mode2 → isModeB
  → AdRemoteConfigBridge.applyByMode   // 内部：先本地 → 再远程覆盖
```

fetch 仍在 `applyByMode` **之前**完成，以便 `getString(ad_config_*)` 读到本次激活结果；**应用顺序**改为本地先行、远程覆盖。

## 禁止擅自扩展兜底

| ❌ 禁止 | 说明 |
|--------|------|
| `BuildConfig` / `local.properties` 的 `AD_*_ID` 回退 | JSON 无该 ad_sense 即不请求 |
| 按 `ad_type` 的通用 id 映射 | 破坏 A 面仅开屏 |
| 远程优先、远程空才 assets（旧逻辑） | 已废弃，须先本地后覆盖 |
| 自造第四级兜底 | 仅 assets → 远程覆盖 → parse(null) |

## 接入工作流

### Step 1～2：Firebase 控制台 + assets 兜底

见 [reference.md](reference.md)（`ad_remote_config_default_a/b.json` + template）。

### Step 3：bootstrap

```kotlin
// 1. fetch（≤8s 超时仍继续 applyByMode）
NotificationRemoteConfig.fetchActivateAndLog(...) // 或 FirebaseRemoteConfigFetcher

// 2. isModeB 已确定
AdRemoteConfigBridge.applyByMode(context, isModeB)  // 先本地，后远程覆盖
```

片段：[templates/ad-config-bootstrap-snippet.kt.template](templates/ad-config-bootstrap-snippet.kt.template)。

### Step 4：验证

[checklist.md](checklist.md) + [流程图.md](流程图.md)，改动文件 **Lint**（不编译）。

## 拉取策略

| 项 | Debug | Release |
|----|-------|---------|
| `minimumFetchInterval` | `0` | `3600`（秒） |
| bootstrap 超时 | `8_000` ms | 同左 |
| fetch 失败/远程空 | 保持 **本地 assets** 已生效配置 | 同左 |
| fetch 成功且远程有效 | **远程覆盖** 本地 | 同左 |

## 架构

```
冷启动 AppAdsBootstrap
  ├── fetchAndActivate()
  ├── isModeB 确定
  └── applyByMode(isModeB)
        ├── ① applyLocalAssets → AdRemoteConfigManager
        └── ② applyRemoteFirebase（有效则覆盖）
```

## 关键约定

1. **`applyByMode` 在 `isModeB` 确定之后、fetch 完成之后**调用。
2. **先本地 apply，再读远程覆盖**；Logcat 见 `使用【本地 assets】` → `使用【远程 Firebase】覆盖`。
3. 参考实现 **未** 对 `ad_config_*` 做 `setDefaultsAsync`；首启靠 **assets**。
4. `getAdId` 仅查当前已 apply 的 JSON，无 BuildConfig 回退。

## 详细参考

- [流程图.md](流程图.md)
- [reference.md](reference.md)
- [checklist.md](checklist.md)
