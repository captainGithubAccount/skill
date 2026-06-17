---
name: cursor-check-rules
description: >-
  Android 项目接入 cursorCodeReviewCheck Gradle 手动检查：从 skill 同步 .cursor 规则目录、
  复制 cursorTaskRules.gradle、注册 Gradle 任务；task-permission-audit 报告写入
  .cursor/buildTxt/task-permission-audit.txt。当用户提到 cursorCodeReviewCheck、cursor检查项、
  task-permission-audit、Cursor 规则检查接入、帮我接入 cursor检查项 skill 时使用。
---

# Cursor 检查项接入（cursorCodeReviewCheck）

将 **手动 Gradle 检查任务** `cursorCodeReviewCheck` 接入 Android 工程。规则以 `.cursor/rules/task-*.mdc` 维护；执行时扫描 `gradleChecks`，**仅打印警告、不阻断构建、不自动打包**。

参考实现：`/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf`

## Skill 目录结构

```
cursor检查项/
├── SKILL.md
├── checklist.md
├── .cursor/                          # 维护入口：你只改这里
│   ├── rules/task-*.mdc
│   └── buildTxt/.gitkeep
├── gradle/cursorTaskRules.gradle     # 检查引擎（随 skill 版本更新）
├── templates/                        # 接入片段
└── scripts/
    ├── sync-cursor-dir.sh
    └── sync-gradle-engine.sh
```

**维护约定**：新增/修改检查项时，只更新 skill 内 `.cursor/rules/`（及必要时 `gradle/cursorTaskRules.gradle`）。接入其它项目时由 AI 从 skill 同步到目标工程。

## 产品行为（必须遵守）

1. **仅手动触发**：用户在 Gradle 面板点击或执行 `./gradlew cursorCodeReviewCheck`
2. **不挂接打包**：禁止 `assembleDebug` / `assembleRelease` 的 `finalizedBy`
3. **task- 规则范围**：仅执行文件名含 `task-` 的 `.mdc`
4. **权限审计输出**：执行到 `task-permission-audit.mdc` 时，**覆盖写入**  
   `.cursor/buildTxt/task-permission-audit.txt`（不打印详细内容到控制台）
5. **其它 task- 规则**：正常 `println` 到控制台

## 接入前：扫描目标项目

| 检查项 | 路径/关键词 | 含义 |
|--------|-------------|------|
| 应用模块 | `app/build.gradle.kts` | 注册 `:app:cursorCodeReviewCheck` |
| 根工程 | `build.gradle.kts` | 可选根别名任务 |
| Gradle 脚本链 | `lwj_work_by_gradle/qd_work_tasks/task.gradle` | 是否已有 `apply from` 模式 |
| 规则目录 | `.cursor/rules/` | 是否已有其它 rules |
| 已接入 | `cursorTaskRules.gradle` / `runCursorRuleChecks` | 避免重复接入 |

**分支：**

- 未接入 → [全新接入工作流](#全新接入工作流)
- 已接入但行为不对 → 对照 [checklist.md](checklist.md) 与 [reference.md](reference.md) 修正
- 仅更新规则 → 只跑 [同步 .cursor](#step-1同步-cursor-目录)

## 全新接入工作流

### Step 1：同步 .cursor 目录

```bash
SKILL="/Users/MacLuo/Desktop/D/working/shenzhen/skill/cursor检查项"
bash "$SKILL/scripts/sync-cursor-dir.sh" "<目标项目根目录>"
```

或 AI 手动：

- 将 skill `.cursor/rules/*.mdc` 复制到 `<项目>/.cursor/rules/`（**合并**，勿删目标工程已有 rules）
- 创建 `<项目>/.cursor/buildTxt/`（可不提交生成的 txt）

### Step 2：复制 Gradle 检查引擎

```bash
bash "$SKILL/scripts/sync-gradle-engine.sh" "<目标项目根目录>"
```

默认目标：`app/lwj_work_by_gradle/qd_work_tasks/gradle/cursorTaskRules.gradle`

若项目无 `lwj_work_by_gradle`，可放到 `app/gradle/cursorTaskRules.gradle` 并相应调整 `apply from` 路径。

### Step 3：apply cursorTaskRules.gradle

在 `app/lwj_work_by_gradle/qd_work_tasks/task.gradle` 追加（参考 [templates/task.gradle.snippet](templates/task.gradle.snippet)）：

```groovy
apply from: '../app/lwj_work_by_gradle/qd_work_tasks/gradle/cursorTaskRules.gradle'
```

若项目无 `task.gradle`，在 `app/build.gradle.kts` 的 `apply { from(...) }` 中直接 apply 该文件。

### Step 4：注册 :app 任务

将 [templates/app-build.gradle.kts.snippet](templates/app-build.gradle.kts.snippet) 合并进 `app/build.gradle.kts`（放在 `plugins` / `apply` 之后，避免重复注册）：

- `cursorCodeReviewCheck` — `dependsOn("processDebugMainManifest")`
- `verifyCursorRules` — 别名，`dependsOn("cursorCodeReviewCheck")`

### Step 5：注册根工程别名（推荐）

将 [templates/root-build.gradle.kts.snippet](templates/root-build.gradle.kts.snippet) 合并进根 `build.gradle.kts`：

- `./gradlew cursorCodeReviewCheck` → `:app:cursorCodeReviewCheck`

### Step 6：按项目调整规则（如需要）

`task-permission-audit.mdc` 中 `scanRoots` 默认：

```json
["app/src/main","netLib/src/main"]
```

按目标工程模块增删路径。

### Step 7：验证

```bash
cd <项目根目录>
./gradlew cursorCodeReviewCheck
```

确认：

- 任务成功、不阻断构建
- `.cursor/buildTxt/task-permission-audit.txt` 已生成/更新
- 控制台无权限审计长报告刷屏

完成 [checklist.md](checklist.md) 全部项。

## 仅更新规则（已接入项目）

1. 你手动改 skill 内 `.cursor/rules/`
2. 对目标项目再执行 `sync-cursor-dir.sh`，或让 AI 复制同名 `.mdc` 覆盖
3. 若改了 `permissionAudit` 逻辑，同步 `gradle/cursorTaskRules.gradle` 并 `./gradlew cursorCodeReviewCheck` 验证

## 新增 task- 检查项

1. 在 skill `.cursor/rules/` 新建 `task-xxx.mdc`
2. frontmatter 配置 `gradleChecks` JSON 数组（类型见 [reference.md](reference.md)）
3. 同步到目标项目 `.cursor/rules/`
4. 运行 `cursorCodeReviewCheck` 验证

## 禁止事项

- 不要将 `cursorCodeReviewCheck` 设为 `preBuild` / `assemble*` 的依赖或 `finalizedBy`
- 不要为每个规则单独生成 buildTxt（**仅** `task-permission-audit` 写固定 txt）
- 不要将 buildTxt 路径改为工程根 `buildTxt/`（固定为 `.cursor/buildTxt/`）
- 接入时不要删除目标工程 `.cursor` 下无关文件（如 `skills/`、其它 rules）

## 用户触发话术示例

> 帮我接入 `/Users/MacLuo/Desktop/D/working/shenzhen/skill/cursor检查项` 这个 skill

AI 应：读取本 SKILL.md → 扫描目标工程 → 按工作流接入或更新 → 跑 checklist → 执行 `./gradlew cursorCodeReviewCheck` 验证（若环境允许）。

## 附加说明

- 检查引擎详解：[reference.md](reference.md)
- Gradle 调试 echo：`-PbuildTxt.echoToConsole=true`
