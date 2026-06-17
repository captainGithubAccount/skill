# cursorCodeReviewCheck 接入检查清单

AI 集成完成后逐项核对。

## Skill 资源同步

- [ ] 已从 skill 同步 `.cursor/rules/` 到目标工程 `.cursor/rules/`
- [ ] 目标工程存在 `.cursor/buildTxt/`（运行任务后生成 txt）
- [ ] 已复制 `gradle/cursorTaskRules.gradle` 到目标工程约定路径

## Gradle 接入

- [ ] `task.gradle`（或 `app/build.gradle.kts`）已 `apply` cursorTaskRules.gradle
- [ ] `:app` 已注册 `cursorCodeReviewCheck`、`verifyCursorRules`
- [ ] 根 `build.gradle.kts` 已注册别名任务（`dependsOn(":app:cursorCodeReviewCheck")`）
- [ ] `cursorCodeReviewCheck` 依赖 `processDebugMainManifest`（权限审计需要合并 Manifest）
- [ ] **未**挂接 `assembleDebug` / `assembleRelease` 的 `finalizedBy`（仅手动执行）

## 行为验证

- [ ] `./gradlew cursorCodeReviewCheck` 可执行且不阻断构建
- [ ] 控制台可看到其它 task- 规则输出；`task-permission-audit` 详细内容**不**刷屏
- [ ] 执行后覆盖写入 `.cursor/buildTxt/task-permission-audit.txt`（仅一个 txt，无 summary/index）
- [ ] 再次执行只更新同一文件，不新增其它 txt

## 规则维护

- [ ] 新增检查项：在 skill 的 `.cursor/rules/` 增加 `task-*.mdc`（含 `gradleChecks` frontmatter）
- [ ] 权限审计 `scanRoots` 已按目标工程模块路径调整（如 `netLib/src/main`）
