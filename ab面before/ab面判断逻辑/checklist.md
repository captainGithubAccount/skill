# A/B 面判断接入检查清单

AI 集成完成后逐项核对，全部通过再结束任务。

## 产品规则

- [ ] 总开关 RC **存在且=1** → 强制 B（不跑代码判断）
- [ ] 总开关 RC **存在且=0** → 强制 A（不跑代码判断）
- [ ] 总开关 RC **不存在**，或 **存在但值≠0且≠1** → 进入代码判断段
- [ ] 代码子项 RC **不存在** → 视为开启，执行对应检查
- [ ] 代码子项 RC **存在且=0** → 跳过该项
- [ ] **有执行过的检查且任一失败** → A；**全部通过** → B
- [ ] 代码判断无成功 / 无可执行检查 → **兜底 A**
- [ ] 已删除所有 `!exists(key) return true` 旧逻辑

## 核心类

- [ ] `Mode2Utils.isMode2()` 实现符合 [templates/Mode2Utils-isMode2.kt.template](templates/Mode2Utils-isMode2.kt.template)
- [ ] `isCodeSubCheckEnabled(key)` 已实现
- [ ] `exists(key)` 使用 `VALUE_SOURCE_STATIC` 判断
- [ ] `InstallationSourceChecker` + `AttributionManager` 买量数据可用
- [ ] `isInstalledFromGooglePlay()` 已实现
- [ ] `AppAdsBootstrap.isModeB` 在 RC 拉取后赋值
- [ ] 热启动不重复判定
- [ ] 新增/修改方法注释为简体中文

## Bootstrap

- [ ] 冷启动：`fetch RC` → `initMode2AndGet` → `resolveModeB` → `applyByMode`
- [ ] `configAppliedThisProcess` 防重复 bootstrap
- [ ] 业务只读 `AppAdsBootstrap.isModeB`，不直接用 `naturalModeB`

## Debug 强制 A/B（仅 Debug 包）

- [ ] `DebugAbOverride.resolveModeB(naturalModeB)` 已在 bootstrap 赋值 `isModeB`
- [ ] Release：`resolveModeB` 恒等于 `naturalModeB`
- [ ] `CODE_OVERRIDE_MODE` 非 NORMAL 时 `isCodeOverrideActive()`，首页 AB 按钮隐藏
- [ ] 弹窗三选项：FORCE_A / FORCE_B / NORMAL；切换后 `restartApp` 生效
- [ ] `set(NORMAL)` 调用 `Mode2Utils.resetCachedCheckForDebug()`
- [ ] `StartActivity` 接入 `onStartActivityCreate`（桌面拉起 vs restart 区分）
- [ ] MMKV：`KEY_DEBUG_AB_OVERRIDE`、`KEY_DEBUG_AB_ENTRY_DISMISSED` 已定义

## A 面 UI

- [ ] A 面首页不展示黄色网站入口（`HomeUrlPresets` 或等价）
- [ ] B 面缺失时补回 B 面专属预设（若项目有该逻辑）
- [ ] A 面不展示 JSON 未配置的广告位（见 [fb skill 禁止擅自扩展兜底](../fb的ab面远程配置拉取/SKILL.md#禁止擅自扩展兜底ai--接入必读)）；**不得**私加 BuildConfig 兜底

## 质量

- [ ] 未将敏感配置硬编码进源码
- [ ] 改动文件已通过 Lint（不编译）
- [ ] 包名已按目标项目替换，无错误残留引用

## 场景自测（Logcat）

- [ ] 总开关=1 → 日志「远程配置强制展示 B 面」
- [ ] 总开关=0 → 日志「总开关关闭，不展示b面」
- [ ] 总开关=2（或其它非 0/1）→ 日志「总开关存在且值=…（非0非1），走代码判断」
- [ ] 总开关不存在 + 买量+GP 全部通过 → 「代码判断为 B 面（有执行过的检查且全部通过）」
- [ ] 总开关不存在 + 任一失败 → 「代码判断：有执行过的检查且任一失败，默认展示 A 面」
- [ ] Debug 强制 B：`naturalIsModeB=false` 且 `isModeB=true`（或相反）日志可见
- [ ] Debug 切 NORMAL 重启后重新走 Mode2 完整判定
