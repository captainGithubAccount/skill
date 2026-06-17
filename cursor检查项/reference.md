# cursorCodeReviewCheck 技术参考

## 任务注册（:app）

```kotlin
tasks.register("cursorCodeReviewCheck") {
    group = "cursor"
    dependsOn("processDebugMainManifest")
    doLast {
        val runChecks = project.extensions.extraProperties
            .get("runCursorRuleChecks") as groovy.lang.Closure<*>
        runChecks.call(true, "cursorCodeReviewCheck")
    }
}
```

`processDebugMainManifest`：合并 debug Manifest，供 `permissionAudit` 读取。

## 规则文件约定

路径：`<项目根>/.cursor/rules/task-*.mdc`

frontmatter 示例：

```yaml
---
description: 规则说明
alwaysApply: false
gradleChecks: '[{"type":"permissionAudit","scanRoots":["app/src/main"]}]'
---
```

`gradleChecks` 为 JSON 数组，由 `cursorTaskRules.gradle` 解析执行。

## gradleChecks 类型

| type | 作用 |
|------|------|
| `propertiesScan` | 扫描 properties 命中禁止值 |
| `sourceScan` | 源码目录正则扫描 |
| `fileExists` | 检查文件是否存在 |
| `printSplashFlow` | 打印启动流程（项目定制） |
| `permissionAudit` | 合并 Manifest 敏感权限审计 |

## permissionAudit 输出

- 固定文件：`.cursor/buildTxt/task-permission-audit.txt`
- 每次 `cursorCodeReviewCheck` 覆盖同一文件
- 控制台仅一行：`📝 权限审计报告已写入 .cursor/buildTxt/task-permission-audit.txt`
- 详细章节【一】～【七】只写入 txt

引擎常量（`cursorTaskRules.gradle`）：

- `BUILD_TXT_DIR_RELATIVE = '.cursor/buildTxt'`
- `PERMISSION_AUDIT_BUILD_TXT_NAME = 'task-permission-audit'`

## 执行链路

```
cursorCodeReviewCheck (手动)
  → processDebugMainManifest
  → runCursorRuleChecks(true, "cursorCodeReviewCheck")
      → collectCursorRuleFiles (仅 task-*.mdc)
      → 逐条 executeCursorRuleFile
          → task-permission-audit.mdc → captureBuildTxtOutput → .cursor/buildTxt/...
          → 其它 task-*.mdc → println 控制台
```

## 无 lwj_work_by_gradle 的替代方案

1. 将 `gradle/cursorTaskRules.gradle` 放到 `app/gradle/`
2. 在 `app/build.gradle.kts`：

```kotlin
apply(from = "gradle/cursorTaskRules.gradle")
```

3. 同样注册 `cursorCodeReviewCheck` 任务块

## 权限审计维护

修改敏感权限分类、SDK 推断、remove 片段生成逻辑：编辑 skill 内 `gradle/cursorTaskRules.gradle` 的 `PERMISSION_AUDIT_*` 段，再 `sync-gradle-engine.sh` 到目标项目。
