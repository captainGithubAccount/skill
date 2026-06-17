# UMP 同意弹窗接入检查清单

**可移植包**：`skill/ump接入/portable/`（任意项目优先）  
金样对照：`tools/browser/pdf` · `videodownload v1.2.0`

## 拷贝与包名

- [ ] 已拷贝 `portable/` 下 5 个 `.kt` 到目标工程
- [ ] 包名 `com.isi.ump` 已替换为目标包名
- [ ] `AdConsentManager.isDebugBuild = BuildConfig.DEBUG` 已在 Application 设置

## 产品规则

- [ ] **先读缓存**：`hasCachedUmpConclusion()` 优先于 gather
- [ ] 缓存来源：SDK `OBTAINED`/`NOT_REQUIRED` + 本地 `flow_completed_once`
- [ ] **无缓存 + 非欧盟/英国**：跳过 UMP，不 gather
- [ ] **无缓存 + 欧盟/英国**：走 gather；结束后 `markUmpConclusionCached`
- [ ] **同意/拒绝均允许广告请求**；不以 `canRequestAds` 阻断
- [ ] Application **可提前** `MonetizationKit.init`（与 UMP 并行）
- [ ] 广告请求须 **`isInit && isUmpResolved`**
- [ ] Release **不**注入 `ConsentDebugSettings`
- [ ] AdMob 后台 **Privacy & messaging 已 Publish**

## 弹窗展示配置

- [ ] AdMob → Privacy & messaging → 消息已 **发布** 且绑定正确 App ID
- [ ] Debug：`AdConsentManager.debugConfig` 在 Application 注入测试设备 id 列表
- [ ] Debug：`gatherConsent` 内 `setDebugGeography(DEBUG_GEOGRAPHY_EEA)`
- [ ] Debug：`addTestDeviceHashedId` 含**本机** MD5（见 `AdTestDeviceIdLog` / Logcat）
- [ ] 测首次弹窗前 **`pm clear` 或清除应用存储**
- [ ] Logcat 出现 `【UMP 弹窗预期】` 且 `isConsentFormAvailable=true`（Debug）

## 核心类

- [ ] `EuRegionHelper` 存在且 27 国 + GB 名单正确
- [ ] `AdConsentManager` 含 `logUmpFormPopupExpectation`（Debug 排查）
- [ ] **有 AdBridge**：`MonetizationKitUmpCallbacks` + `isUmpResolved` 闸门
- [ ] **无 AdBridge**：`SimpleUmpGate` + `GateUmpFlowCallbacks`；广告 load 前检查 `isUmpResolved`
- [ ] `enableFor` 检查 `isUmpResolved`（有 AdBridge 时）
- [ ] （可选）`AdTestDeviceIdLog`：Debug `RequestConfiguration.setTestDeviceIds`
- [ ] 注释简体中文

## Gradle / Manifest

- [ ] `play-services-ads` 已依赖
- [ ] AdMob `APPLICATION_ID` 已配置

## 启动页

- [ ] 冷启动 `awaitConsent()` → `onUmpFlowFinished`（无 Boolean）
- [ ] UMP 结束后开屏 load + 预加载
- [ ] 转圈 `splashUmpWait` / 藏进度条仅 `willRunUmpGather()==true`
- [ ] UMP 转圈色与底部进度条一致（如 `#3D7CFD` / `lang_blue_main`）
- [ ] 热启动立即 `markUmpResolved`，不 gather
- [ ] 放行闸：2s / UMP 后 10s 开屏 load 上限
- [ ] （推荐）UMP 后 `awaitSdkInitIfNeeded` 兜底（videodownload 有；PDF 可选补）

## 埋点

- [ ] `ump_pop_show` 在 `isConsentFormAvailable==true` 且即将 show 前上报

## 场景自测（Logcat `TAG-->>UMP合规`）

- [ ] **清数据 + Debug + 任意系统地区**：`无 UMP 缓存，即将 gatherConsent` + `Debug：已注入 N 个`
- [ ] **有缓存**二次冷启动：`读取 UMP 缓存，跳过 gatherConsent`
- [ ] **无缓存 + CN（Release）**：`无缓存且非欧盟/英国，跳过 UMP`
- [ ] **无缓存 + FR 首次 + 后台已 Publish**：可能 `REQUIRED` + 弹窗
- [ ] **isConsentFormAvailable=false**：检查 AdMob 消息未发布
- [ ] **用户拒绝后**：仍能 load 开屏（`isUmpResolved=true`）
- [ ] **热启动**：不 gather
- [ ] Application 已 init 但 UMP 未完成：请求含 `UMP流程未完成`

## 反模式（不应出现）

- [ ] Release 注入 `setDebugGeography` 或测试设备 id
- [ ] 仅加测试设备 id、未 Publish AdMob 消息却期望必弹
- [ ] 有缓存仍期望每次弹窗（未清数据）
- [ ] `canShowAd(false)` / `canRequestAds=false` 阻断广告
- [ ] 仅有 `isInit` 无 `isUmpResolved` 就发广告
