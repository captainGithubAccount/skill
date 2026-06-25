---
name: pluggable-module
description: >-
  将任意功能抽离为可跨项目复用的独立模块，遵循 Facade + 单集成点架构。
  适用于抽离广告、登录、支付、推送、埋点等功能，或在新项目接入/升级已有模块。
  Use when the user mentions 可插拔模块、解耦、抽离模块、独立 module、可替换模块、
  pluggable-module、Facade 集成、或要求功能可跨项目复用且升级时只需替换模块。
disable-model-invocation: true
---

# 可插拔模块协议（Pluggable Module Protocol）

将任意功能抽离为**独立可替换模块**。业务项目只保留一个集成文件，升级模块版本时默认不改业务代码。

## 核心架构

```
{feature}-core/          ← 独立模块（可单独发版、git submodule、Maven/npm 发布）
  ├── {Feature}Facade    ← 业务唯一调用入口（object 或 interface）
  ├── {Feature}Config    ← 纯数据配置，由 app 传入
  ├── {Feature}Listener  ← 事件回调，业务在 Integration 里实现
  └── internal/          ← SDK/实现细节，不对外暴露

app/
  └── {Feature}Integration.kt  ← 全项目唯一集成点（仅此文件含项目特化逻辑）
```

## 依赖方向（硬性，违反即返工）

| 方向 | 规则 |
|------|------|
| core → app | **禁止**。core 不得 import 任何 app/业务包 |
| app → core | 只允许 Facade、Config、Listener、公开枚举/数据类 |
| app → 第三方 SDK | **禁止**。业务不得直连 SDK，必须经 Facade |
| core → 业务模块 | **禁止**。不得依赖 Nav、Analytics、具体 Screen/ViewModel |

## 集成白名单

### 抽离/新建模块时允许新增/修改

- `{feature}-core/**`（新建独立 module）
- `app/.../{Feature}Integration.kt`（新建，从模板复制）
- `app/build.gradle` 或 `build.gradle.kts`（添加 module 依赖）
- `Application` / `App` 入口（**仅加一行** `{Feature}Integration.setup()`）

### 接入已有模块时允许修改

- 同上白名单
- 各业务 Screen/Page（**仅加 Facade 单行调用**，不得写 init/config）

### 永远禁止修改（除非用户明确要求）

- `{feature}-core` 内文件（接入已有模块时）
- 在 ViewModel/Repository 内写 SDK 初始化或配置
- 在 3 个以上文件散落 init/config/回调逻辑
- 为省事让 core 依赖项目 DI、Navigation、Analytics

## 公开 API 规范

对外**仅暴露**以下类型（名称可替换，结构不变）：

```kotlin
// 入口：业务唯一调用点
object {Feature}Facade {
    fun init(context: Context, config: {Feature}Config, listener: {Feature}Listener)
    // 按功能添加 show/load/release 等方法，保持精简稳定
    fun release()
}

// 配置：纯数据，core 不读 BuildConfig
data class {Feature}Config(val /* 按功能填写 */)

// 回调：事件上行，core 不主动调业务
interface {Feature}Listener {
    fun onSuccess(...)
    fun onError(...)
}
```

- 实现细节放 `internal` 包
- Facade 方法少而稳定；内部可随意重构
- 配置由 app 传入，core 不读取项目常量文件

## AI 工作流（必须按顺序）

1. **先出方案**（禁止先写业务代码）：
   - 架构说明
   - 公开 API 列表
   - 文件清单（含每个文件职责）
   - 集成点数量（Integration 文件数必须 = 1）
   - 业务侧 Facade 调用点列表
2. **等用户确认**后再写代码
3. **完成后自检**：逐项勾选 [checklist.md](checklist.md)
4. **输出交付物**：见下方清单

## 交付物清单

每次抽离或接入必须交付：

1. `{feature}-core` 模块代码
2. `{Feature}Integration.kt` 模板（注释标注哪些行按项目填写）
3. 集成步骤（≤ 3 步：加依赖 → Application init → 业务处 Facade 调用）
4. [forbidden.md](forbidden.md) 中列出的禁止写法对照
5. 自检清单勾选结果

## 升级模块版本规则

1. 默认**只改**依赖版本号
2. 阅读 `changelog.md`（或模块 CHANGELOG），**仅处理 BREAKING 项**
3. 无 BREAKING 时，禁止修改 Screen/ViewModel/Integration 以外文件
4. **禁止**全项目搜索替换功能相关代码

## 业务侧调用规范

- 每个使用点 **最多 1 行** Facade 调用，例如：`{Feature}Facade.show(placement)`
- 禁止在 Composable/Activity/Fragment 内写 init、config、SDK 直连
- 埋点、导航、业务回调 **只在** `{Feature}Integration` 的 Listener / Host 里桥接

## Screen 脚本式原则（**启动页 / 关键流程页强制**）

复杂逻辑 **进 module 内部**；Screen 只保留「读脚本就能懂流程」的线性步骤。

### 目标形态

```kotlin
// ✅ 启动页像脚本：一行一步 + 回调，不藏 Pipeline
lifecycleScope.launch {
    awaitLoadingAnimation()
    ConsentFacade.runOnColdStart(activity) { showUmp -> updateUmpUi(showUmp) }
    AdFacade.runSplashLaunch(SplashLaunchParams(..., onFinished = { navigateNext() }))
}
```

### 硬性规则

| 规则 | 说明 |
|------|------|
| **禁止 app 层 Pipeline/Coordinator 藏流程** | 不得在 `app/` 再包 `XxxLaunchPipeline`、`XxxFlowCoordinator` 把 UMP/开屏/闸门藏进去 |
| **编排进 core internal** | 2s/10s 放行闸、预加载时序、频次上限等 → `{feature}-core/internal/` |
| **Screen 只做编排顺序** | Activity/Composable 只写：**谁先谁后**、**UI 回调**、**导航** |
| **Host 桥接项目特化** | AB 闸门、预加载列表、埋点 → Integration 注入 `{Feature}Host`，不进 Screen |
| **禁止 typealias shim 占文件** | 迁移后 **删旧类、改 import**，不用 `@Deprecated typealias` 留空壳 |

### 分层对照（PDF 启动页实例）

| 层 | 职责 | 示例 |
|----|------|------|
| `SplashActivity` | 脚本：动画 → UMP → 开屏 → 路由 | 读 `runSplashLaunchScript()` 即懂全流程 |
| `consent-core` | UMP 细节 | `ConsentFacade.runOnColdStart` |
| `ad-orchestrator-core` | 开屏 preload / 放行闸 / show | `AdFacade.runSplashLaunch` → `internal/SplashLaunchOrchestrator` |
| `PlatformIntegration` | 唯一 Host 注入 | `AdOrchestratorHost.canShowAd` → `PdfAppAdsBootstrap` |

### 反例（禁止）

```kotlin
// ❌ app 里再包一层，Screen 看不出 UMP 和开屏先后
SplashLaunchPipeline(activity, binding, ...).start()

// ❌ ViewModel 里 50 行广告时序
// ❌ typealias 空壳占目录
typealias AppRemoteConfig = AbRemoteConfig
```

## 验收五问（完成后必须回答）

1. 集成点有几个文件？（**必须 = 1**）
2. 业务代码有没有直接 import 第三方 SDK？（**必须 = 无**）
3. core 模块有没有 import app 包？（**必须 = 无**）
4. 换项目接入，最少新建/改几个文件？（**目标 ≤ 3**）
5. 只升 core 版本、API 无 breaking，业务要改几处？（**目标 = 0**）

任一项不满足 → 按本协议返工，不得交付。

## 附加资源

- 提示词模板：[prompts.md](prompts.md)
- 禁止耦合清单：[forbidden.md](forbidden.md)
- 自检清单：[checklist.md](checklist.md)
- Integration 模板：[integration-template.md](integration-template.md)
