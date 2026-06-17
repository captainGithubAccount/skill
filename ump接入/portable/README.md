# UMP 可移植源码包

从 PDF 金样抽离，**不依赖 AdBridge / MonetizationKit**，拷贝到任意 Android 项目即可接入。

## 拷贝清单

| 文件 | 目标（示例） |
|------|----------------|
| `UmpGate.kt` | `app/src/main/java/.../ump/UmpGate.kt` |
| `UmpFlowCallbacks.kt` | 同上 |
| `UmpLogTag.kt` | 同上 |
| `EuRegionHelper.kt` | `.../ump/EuRegionHelper.kt` |
| `AdConsentManager.kt` | `.../ump/AdConsentManager.kt` |

**包名**：拷贝后把 `com.isi.ump` 批量替换为目标项目包名（如 `com.example.app.consent`）。

## 接入分支

### A. 项目已有 AdBridge / MonetizationKit

1. 拷贝上表 5 个文件  
2. 在 Application 配置 `AdConsentManager.debugConfig`（Debug）  
3. 启动页使用 `templates/SplashLaunchPipeline-awaitConsent.kt.template` 或 `StartActivity-awaitConsent.kt.template`  
4. 用 `templates/MonetizationKitUmpAdapter.kt.template` 把 `UmpFlowCallbacks` 接到 `MonetizationKit.markUmpResolved()`  
5. `MonetizationKit.enableFor` 已含 `isUmpResolved` 闸门则无需改广告层

### B. 项目无广告模块（仅 UMP）

1. 拷贝上表 5 个文件  
2. Application：`AdConsentManager.isDebugBuild = BuildConfig.DEBUG`  
3. 启动页：`SimpleUmpGate` + `GateUmpFlowCallbacks(SimpleUmpGate)`  
4. 自有广告 SDK 在 load 前检查 `SimpleUmpGate.isUmpResolved`

## Gradle

```kotlin
implementation(libs.google.ads) // play-services-ads，含 com.google.android.ump.*
```

Manifest 配置 AdMob `APPLICATION_ID`；AdMob 后台 Privacy & messaging **Publish**。

详见上级 [SKILL.md](../SKILL.md)。
