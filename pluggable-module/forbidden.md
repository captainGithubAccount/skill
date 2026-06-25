# 禁止耦合清单

以下写法**一律禁止**。AI 完成后必须确认项目中不存在这些模式。

---

## 依赖违规

| 违规写法 | 正确做法 |
|----------|----------|
| `core` 模块 `import com.yourapp.xxx` | core 只依赖 SDK/标准库 |
| Screen 直接 `import com.thirdparty.sdk.*` | 只 `import {Feature}Facade` |
| core 依赖项目的 `analytics`、`navigation` 模块 | 通过 Listener 回调，由 Integration 桥接 |
| core 读取 `BuildConfig.AD_ID` | app 传入 `{Feature}Config` |

---

## 集成违规

| 违规写法 | 正确做法 |
|----------|----------|
| `Application`、`MainActivity`、`HomeViewModel` 各写一份 init | 只在 `{Feature}Integration.setup()` init 一次 |
| 2 个以上 Integration/Setup/Initializer 文件 | 全项目只有 1 个 Integration |
| 在 Composable 里 `MobileAds.initialize()` | Composable 只调 `Facade.show()` |
| ViewModel 里配置广告位 ID、密钥 | 配置放 Config，由 Integration 传入 |

---

## 调用违规

| 违规写法 | 正确做法 |
|----------|----------|
| Screen 里 20 行广告加载+回调+埋点 | 1 行 `Facade.show()`，埋点在 Integration Listener |
| Facade 方法传入 `NavController` / `ViewModel` | Facade 只接收 Config、Placement、简单 lambda |
| 业务代码判断 SDK 错误码并分支处理 | 错误映射在 core internal，业务只收 Listener 事件 |
| app 层 `XxxLaunchPipeline` / `XxxFlowCoordinator` 封装多步启动流 | 编排进 `{feature}-core/internal`；Screen 脚本式逐步调 Facade |
| 用 `typealias` 留空壳代替删文件改引用 | 直接删 shim，全工程改 import 到 core 公开 API |
| Screen 内写 2s/10s 放行闸、preload 时序 | 时序进 `AdFacade` / internal Orchestrator；Screen 只传 `onFinished` |

---

## Screen 脚本式违规（启动页 / 关键流程）

| 违规写法 | 正确做法 |
|----------|----------|
| `Pipeline.start()` 一行启动，内部 170 行 | `runOnColdStart` + `runSplashLaunch` 两行可见步骤 |
| Binding/UI 与 UMP、开屏逻辑混在同一个 Pipeline 类 | UMP → `ConsentFacade`；开屏 → `AdFacade`；UI 回调留 Screen |
| 为了「好看」在 app 再抽象 Coordinator | Coordinator/Orchestrator 只在 **core internal** |

---

## 升级违规

| 违规写法 | 正确做法 |
|----------|----------|
| 升版本后全项目搜索 `AdManager` 替换 | 只改 `build.gradle` 版本号 |
| 无 BREAKING 却改 10+ 业务文件 | 无 breaking 则业务零改动 |
| 把 bug 修在业务 Screen 里 patch SDK 行为 | bug 修在 core，发 patch 版本 |

---

## 快速嗅探（让 AI 搜索这些信号）

若项目中存在以下模式，大概率耦合了：

- `import com.google.android.gms.ads`（或任意 SDK）出现在 `app/src/main` 非 Integration 文件
- 多个文件含 `initialize(` + 功能名关键词
- `{feature}-core` 内出现 `R.string`、`R.layout`（app 资源）
- ViewModel 构造函数注入 SDK Manager 而非 Facade
