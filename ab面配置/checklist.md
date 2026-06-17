# A/B 面配置接入检查清单

**现行金样**：`/Users/MacLuo/Desktop/D/working/shenzhen/tools/browser/pdf`  
**对照**：`videodownload v1.2.0`

## 基础设施

- [ ] Firebase Remote Config 依赖已接入
- [ ] `FirebaseApp.initializeApp` 在 bootstrap 前
- [ ] MMKV 已初始化
- [ ] Install Referrer 依赖已添加

## 核心类

- [ ] `AbSettlementCoordinator`（双轨、FC 3 次重试、B 面补拉）
- [ ] `AttributionManager`（阶段1+阶段2）
- [ ] `Mode2Utils`（V3.1.0 语义；PDF 现行启用 refer+GP）
- [ ] `AppAdsBootstrap` / `PdfAppAdsBootstrap`（commit、canShowAd、监听）
- [ ] `ReferrerSideParser`
- [ ] （推荐）`AppRemoteConfig.fetchAndActivateWithDetail`
- [ ] 新增类注释为简体中文

## Application 时序（PDF 现行）

- [ ] `applyDefaultLocalAssetsA` 在 **FC 之前**（可在广告预热协程内）
- [ ] `AppAdsBootstrap.run` 与 `MonetizationKit.init` **并行**（不强制嵌套 init 回调）
- [ ] （可选）热启动 `hotResumeFastPath=true` 跳过重复 settlement

## Firebase Key

- [ ] 控制台已配置 Mode2 总开关 + 子项（或确认走未配置默认）
- [ ] `ad_config_a` / `ad_config_b` JSON 已配置（或仅靠 assets）
- [ ] Mode2 key **未**错误写入 setDefaultsAsync

## 广告 / 通知

- [ ] `applyByMode`：A 面先 assets 后远程；B 面仅远程
- [ ] B 专属位在 bootstrap **`modeBExclusive` 按项目登记**
- [ ] B 面 commit 后远程未生效时有 **补拉** 或等价逻辑
- [ ] 无 BuildConfig 私加广告兜底
- [ ] 通知模块：**可选**（无则跳过 NotificationRemoteConfig*）

## 归因阶段2

- [ ] `needsExtendedReferrerRetry` / MMKV 标志位齐全
- [ ] `applyModeUpdateAfterExtended` 仅 A→B
- [ ] 15min 后 `isAbRevisionAllowed` 拒绝修订

## 自然 B 锁定

- [ ] `updateLockedNaturalModeBIfChanged` 拒绝 B→A
- [ ] 总开关=0 不降级已锁定自然 B

## Debug（可选）

- [ ] `DebugAbOverride` + `resolveModeB` 在 commit 时生效
- [ ] `applyForcedLockIfNeeded` 在 settlement 前
- [ ] Release 忽略 Debug 覆盖

## 质量

- [ ] 包名已替换，无金样残留引用
- [ ] 改动文件 Lint 通过（不强制编译）

## Logcat 关键词

- [ ] `【AB结算】` / `【Firebase RC】` / `【Mode2判定】` / `【归因】`
- [ ] FC 第 N 次拉取 / B 面补拉成功或终局失败
