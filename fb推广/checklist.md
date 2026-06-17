# Meta（Facebook）App Events 推广 — 接入检查清单

AI 集成完成后逐项核对，全部通过再结束任务。

## 产品规则

- [ ] 仅使用 Meta **App Events**，未为 FB 推广额外接 Audience Network 展示
- [ ] `init` + `AppEventsLogger.activateApp` 已在 `Application` 冷启动执行
- [ ] 每次 AdMob paid 且 value>0 上报 **Ad_Impression**（标准事件）
- [ ] 每次 AdMob paid 且 value>0 上报 **ad_revenue**
- [ ] **仅安装自然日**上报 **ad_d0_revenue**
- [ ] 所有 Meta 收入事件 `currency` 固定 **USD**
- [ ] value≤0 不上报 Meta

## 依赖与配置

- [ ] `implementation(libs.other.facebook)` 或等价坐标
- [ ] `local.properties` 配置 `FB_APP_ID`、`FB_CLIENT_TOKEN`（不入库）
- [ ] Manifest `ApplicationId` + `ClientToken` meta-data
- [ ] `BuildConfig.FB_APP_ID` / `FB_CLIENT_TOKEN` 与 Manifest 一致
- [ ] 非占位 AppId（Release 联调前确认）

## 核心类

- [ ] `FacebookEventsDelegate` 已实现（见 template）
- [ ] `KEY_FIRST_LAUNCH_APP_TS` 首次安装只写一次
- [ ] `InstallIdHolder`（或等价）写入安装时刻
- [ ] `VsaveV3FeatureKit.init` 调用 `FacebookEventsDelegate.init`
- [ ] `reportFacebookPaidAdRevenue` 对外门面
- [ ] `AdEventUtils.facebookRevenueReporter` 已在 Application 注入
- [ ] 开屏/插屏 paid 经 `loadAdLoadedReport`
- [ ] 原生 paid 经 `loadAdLoadedReport`
- [ ] 新增/修改方法注释为简体中文

## 运营（代码外）

- [ ] Facebook Events Manager 已识别 `ad_d0_revenue`、`ad_revenue`
- [ ] ROAS 优化对象指向 `ad_revenue`（按投放配置）
- [ ] 隐私政策 / 数据披露满足 Meta 要求

## 质量

- [ ] 未向 Meta 上报伪造收益
- [ ] 改动文件 Lint 通过（不编译）

## 场景自测（Logcat `FbAppEvents`）

- [ ] 冷启动：`Facebook SDK 已初始化并 activateApp`
- [ ] 安装日首次 paid：三条「已上报 Meta 事件=…」（Impression + ad_revenue + ad_d0_revenue）
- [ ] 非安装日 paid：仅 Impression + ad_revenue，且有「跳过 ad_d0_revenue」
- [ ] value=0：「收益<=0，跳过 Meta 上报」
