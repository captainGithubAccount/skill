# Integration 模板

每个项目**只保留这一个集成文件**。复制到 `app/src/main/java/{包名}/{Feature}Integration.kt`。

---

## Kotlin / Android 模板

```kotlin
package com.yourapp.integration  // ← 按项目包名填写

import android.app.Application
// import com.yourfeature.{Feature}Config
// import com.yourfeature.{Feature}Facade
// import com.yourfeature.{Feature}Listener

/**
 * {功能名} 全项目唯一集成点。
 * 换项目时：只改本文件 + build.gradle 依赖 + Application 一行 setup。
 */
object {Feature}Integration {

    fun setup(application: Application) {
        // val config = {Feature}Config(
        //     appId = "...",           // ← 按项目填写
        //     isDebug = BuildConfig.DEBUG,
        // )
        //
        // {Feature}Facade.init(
        //     context = application,
        //     config = config,
        //     listener = object : {Feature}Listener {
        //         override fun onSuccess(...) {
        //             // 埋点、导航等业务桥接只写在这里
        //         }
        //         override fun onError(...) {
        //             // Analytics.track(...)
        //         }
        //     }
        // )
    }
}
```

## Application 入口（仅加这一行）

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        {Feature}Integration.setup(this)  // ← 只加这一行
    }
}
```

## 业务 Screen 用法（每处最多一行 Facade + 脚本式编排）

```kotlin
// ✅ 启动页脚本（复杂逻辑在 core，Screen 只排顺序）
lifecycleScope.launch {
    awaitLoadingAnimation()
    ConsentFacade.runOnColdStart(activity) { waiting -> showUmpProgress(waiting) }
    AdFacade.runSplashLaunch(
        SplashLaunchParams(
            activity = activity,
            releaseGateAnchorElapsed = gateAnchor,
            onFinished = { navigateNext() },
        ),
    )
}

// ✅ 普通位：单行
AdFacade.showInterstitial(AdSense.ENTER_MAIN)

// ❌ 禁止：Pipeline 一行藏全流程
SplashLaunchPipeline(activity, binding, ...).start()

// ❌ 禁止：在 Screen 里 init、配 config、直连 SDK、写埋点、写 2s/10s 闸
```

## build.gradle.kts 依赖

```kotlin
dependencies {
    implementation(project(":{feature}-core"))
    // 或 implementation("com.yourgroup:{feature}-core:1.0.0")
}
```

---

## 职责边界

| 文件 | 职责 |
|------|------|
| `{feature}-core` | 功能实现、SDK 封装、错误映射 |
| `{Feature}Integration.kt` | 项目配置、埋点/导航桥接 |
| `Application` | 一行 setup |
| `Screen` / `Page` | 一行 Facade 调用 |
