# 跟 AI 提问的提示词模板

使用时把 `{功能名}`、`{Feature}`、`{feature}` 替换成实际名称（如 广告/Ad/ad）。

---

## 1. 抽离功能为独立模块（最常用）

```
按桌面 pluggable-module 协议（或 @pluggable-module），把「{功能名}」抽离为可跨项目复用的独立模块。

要求：
- 先出方案和文件清单，不要写代码，等我确认
- 单 Facade、单 Integration 文件
- 遵守白名单，不得耦合业务

交付：模块代码方案、公开 API 列表、集成步骤、禁止耦合清单、验收五问预判。
```

---

## 2. 在新项目接入已有模块

```
按 pluggable-module 协议，在当前项目接入已有的 {功能名} 模块（{feature}-core）。

规则：
- 只从 integration-template 复制 Integration 文件
- 只允许改白名单内的文件
- 业务使用点只能 Facade 单行调用
- 禁止修改 {feature}-core 内任何文件

完成后：列出修改文件、每项职责、自检清单勾选结果、验收五问答案。
```

---

## 3. 升级模块版本

```
按 pluggable-module 协议，将 {feature}-core 从 {旧版本} 升到 {新版本}。

规则：
- 默认只改依赖版本号
- 只处理 changelog 中 BREAKING 项
- 无 BREAKING 禁止改 Screen/ViewModel
- 禁止全项目搜索替换

输出：是否需要改 Integration？改哪几行？为什么？
```

---

## 4. 审查 AI 已写的代码是否耦合

```
按 pluggable-module 协议的验收五问和 forbidden 清单，审查当前 {功能名} 相关代码。

只输出：
- 违规项（文件 + 行号 + 违反哪条规则）
- 修复建议（按最小改动）
- 验收五问答案

不要直接改代码，先让我确认。
```

---

## 5. 启动页 / 多步流程（脚本式抽离）

```
@pluggable-module
把启动页 UMP + 开屏抽离到 consent-core / ad-orchestrator-core。

要求：
- SplashActivity 脚本式：ConsentFacade.runOnColdStart → AdFacade.runSplashLaunch，一行一步
- 禁止 app 层 SplashLaunchPipeline / Coordinator 藏流程
- 2s/10s 放行闸、preload 时序进 core internal
- Host 在 PlatformIntegration 注入；删 typealias shim
- 先出方案等我确认
```

---

## 6. 最短一句话（日常用）

```
按 pluggable-module 协议抽离 {功能名}，先方案后代码。
```

或：

```
按 pluggable-module 接入 {功能名}，完成后跑验收五问。
```

---

## 7. 指定功能专属 Skill（进阶）

某个功能稳定后，可复制本目录为 `~/.cursor/skills/{feature-name}/`，在 SKILL.md 中补充：

- 模块路径 / Maven 坐标
- 该功能的 Facade API 文档
- 该功能的 changelog.md

以后对新项目只说：

```
按 ~/.cursor/skills/{feature-name}/ 规范接入，遵守 pluggable-module 母协议。
```
