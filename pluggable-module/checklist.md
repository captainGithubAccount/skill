# 自检清单

AI 完成抽离/接入后，必须逐项勾选并输出结果。

---

## 架构

- [ ] 存在独立 `{feature}-core` 模块（或等价独立包）
- [ ] 存在 `{Feature}Facade` 作为唯一业务入口
- [ ] 存在 `{Feature}Config`（纯数据配置）
- [ ] 存在 `{Feature}Listener`（事件上行）
- [ ] SDK/实现细节在 `internal` 包，未对外 export

## 依赖

- [ ] core 模块零 import 业务 app 包
- [ ] 业务代码零 import 第三方 SDK（Integration 除外若必须）
- [ ] core 未依赖 Navigation / Analytics / 具体 UI 模块

## 集成

- [ ] 全项目只有 **1 个** `{Feature}Integration.kt`（或 `PlatformIntegration.kt` 统一装配）
- [ ] Application 只有 **1 行** setup 调用
- [ ] init/config 未散落在 3 个以上文件
- [ ] 各业务使用点均为 Facade **单行调用**

## Screen 脚本式（启动页 / 关键流程页）

- [ ] 复杂时序（UMP、放行闸、preload、show）在 **core internal**，不在 app Pipeline
- [ ] app **无** `XxxLaunchPipeline` / 多层 Coordinator 藏流程
- [ ] 读 Screen 主函数能逐步看到：动画 → Facade 步骤 → 导航，无需跳进 100+ 行类
- [ ] 项目特化（AB 闸门、预加载位、埋点）经 **Host/Listener** 注入，不在 Screen
- [ ] 迁移后 **无** 仅 `typealias` 的空壳文件

## 可替换性

- [ ] 换项目接入：新建/修改文件 ≤ 3 个（Integration + gradle + Application）
- [ ] 升 patch/minor 版本、无 breaking：业务改动 = 0 处
- [ ] 已提供集成步骤文档（≤ 3 步）
- [ ] 已提供 Integration 模板（含「按项目填写」注释）

## 验收五问

1. 集成点文件数 = **1** ？ 是 / 否
2. 业务直接 import SDK = **无** ？ 是 / 否
3. core import app 包 = **无** ？ 是 / 否
4. 换项目最少改文件数 ≤ **3** ？ 是 / 否
5. 无 breaking 升版业务改动 = **0** ？ 是 / 否

**任一项「否」→ 不得交付，按协议返工。**
