# A/B 面广告远程配置 — 详细参考

> 流程图：[流程图.md](流程图.md)  
> `isModeB`：[ab面判断逻辑](../ab面判断逻辑/reference.md)

## 目录

1. [Firebase 参数](#firebase-参数)
2. [拉取与激活](#拉取与激活)
3. [本地 assets](#本地-assets)
4. [applyByMode 逻辑（先本地，后远程覆盖）](#applybymode-逻辑先本地后远程覆盖)
5. [接入示例](#接入示例)
6. [排错](#排错)

---

## Firebase 参数

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `ad_config_a` | String | A 面广告 JSON 全文 |
| `ad_config_b` | String | B 面广告 JSON 全文 |

---

## 拉取与激活

### FirebaseRemoteConfigFetcher（`:Ad` 模块）

```kotlin
suspend fun fetchAndActivate(minimumFetchIntervalSeconds: Long): Boolean
```

`AppAdsBootstrap` 在 `applyByMode` **之前**调用 fetch（常与通知 RC 共用 `NotificationRemoteConfig.fetchActivateAndLog`），超时建议 `8_000` ms。

**注意**：fetch 先于 apply，但 **apply 内部**先写本地、再读远程覆盖，不是等远程为空才读 assets。

---

## 本地 assets

| 面 | assets 文件 | 候选顺序 |
|----|-------------|----------|
| A | `ad_remote_config_default_a.json` | 先 `.json`，再 `.template.json` |
| B | `ad_remote_config_default_b.json` | 同上 |

运行时路径：`app/src/main/assets/`。`.gitignore` 忽略时需从 template 复制到 assets。

---

## applyByMode 逻辑（先本地，后远程覆盖）

```kotlin
fun applyByMode(context: Context, isModeB: Boolean) {
    // ① 先本地
    applyLocalAssets(context, candidates)  // 失败则 parse(null) 兜底
    // ② 再远程（fetch 已完成）
    applyRemoteFirebase(key)               // 有效则覆盖 ①
}
```

### 日志关键字（videodownload）

| 日志 | 含义 |
|------|------|
| `生效顺序: 先 assets 本地 → 远程非空且可 parse 则覆盖` | 进入 applyByMode |
| `使用【本地 assets】配置（先行生效）` | ① 完成 |
| `使用【远程 Firebase】覆盖本地配置` | ② 覆盖成功 |
| `最终生效: 保持本地/assets 配置` | 远程空或不可 parse |
| `最终生效: 远程 Firebase 已覆盖本地` | 远程为最终态 |

### 方案说明

| isModeB | Firebase key | assets |
|---------|--------------|--------|
| `false` | `ad_config_a` | `ad_remote_config_default_a.json` |
| `true` | `ad_config_b` | `ad_remote_config_default_b.json` |

---

## getAdId：禁止额外兜底

```kotlin
fun getAdId(sense: AdSense): String? =
    current.get()?.findBySense(sense)?.adId?.takeIf { it.isNotBlank() }
```

JSON 无该位 → `null` → 不请求。不得加 BuildConfig 回退。

---

## 接入示例

```kotlin
// bootstrap 内（fetch 与 isModeB 已就绪后）
AdRemoteConfigBridge.applyByMode(context, isModeB)
```

---

## 排错

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| 仅有本地日志、无覆盖 | 远程 `ad_config_*` 空或非法 | 查 Firebase 控制台 JSON |
| 远程覆盖后广告位不对 | 远程 JSON 与预期不一致 | 修 Firebase 或核对 B 面模板 |
| 改 Firebase 不生效 | fetch 间隔 / 未冷启动 fetch | Debug interval=0 |
| 启动无广告 | assets 与远程皆不可用 | 补 assets 文件 |
| 仍见旧日志「远程为空才 assets」 | 未更新 `AdRemoteConfigBridge` | 对齐 videodownload 定稿 |

---

## 参考类（videodownload）

| 类 | 模块 | 职责 |
|----|------|------|
| `AdRemoteConfigBridge` | AdBridge | 先本地 assets，后远程覆盖 |
| `AdRemoteConfigManager` | AdBridge | 持有当前生效配置 |
| `FirebaseRemoteConfigFetcher` | AdBridge | fetchAndActivate |
| `AppAdsBootstrap` | app | fetch → isModeB → applyByMode |
