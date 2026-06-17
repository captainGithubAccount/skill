# AdMob 广告层接入检查清单

## 模块与 Gradle

- [ ] `:AdBridge`、`:admob`、`:AdBase` 已加入工程
- [ ] `app` 依赖 `api(project(":AdBridge"))`
- [ ] `play-services-ads` 版本与金样一致或兼容
- [ ] Manifest `APPLICATION_ID` 已配置
- [ ] `local.properties` 含 `ad.app.id`（勿提交密钥）

## 初始化

- [ ] `MonetizationKit.prepareBeforeConsent` 已调用
- [ ] `AdRemoteConfigBridge.applyDefaultLocalAssetsA` 在展示前执行
- [ ] `MonetizationKit.init` 已调用且 `isInit=true`
- [ ] UMP 结束后 `isUmpResolved=true`（双闸门均 true 才请求）
- [ ] Debug：`AdTestDeviceIdLog` 在 `MobileAds.initialize` 前注入测试设备 id
- [ ] 冷启开屏已接 UMP（见 [ump接入](../ump接入/SKILL.md)）

## AB 面与远程配置（若接入）

- [ ] `PdfAppAdsBootstrap.canShowAd` 或等价门控已用于所有展示点
- [ ] B 专属位已加入 `modeBExclusive` 集合
- [ ] Firebase `pdf_ad_config_a` / `pdf_ad_config_b` 已配置
- [ ] B 面 commit 后 Logcat 可见 `【广告RC】远程Firebase覆盖 key=pdf_ad_config_b`
- [ ] B 面 FC 失败时有重试日志（`AbSettlementCoordinator`）

## 广告位清单

- [ ] 用户已提供 [广告位清单模板.md](广告位清单模板.md) 或等价描述
- [ ] 每个位置已映射 `AdSense` + JSON `ad_sense`
- [ ] 插屏/原生已配置 **preload 时机**
- [ ] Banner 已明确「现场 load、无 Loader 预加载」
- [ ] 原生容器 `FrameLayout` 已存在；无货时容器 GONE

## JSON

- [ ] `assets/ad_remote_config_default_a.json` 含 A 面启用位
- [ ] 每个 `ad_id` 有效（测试或正式）
- [ ] `enable: true` 且 `ad_type` 与枚举一致（含 `banner`）
- [ ] `app_level_limit` 含 enter/back 概率、底栏 interval（若使用）
- [ ] Firebase B 面 JSON 含 Banner（ad_sense=4）等 B 专属位

## 代码质量

- [ ] 展示前检查 `canShowAd(sense)` 或 `MonetizationKit.enableFor(sense)`
- [ ] 插屏展示点未误用 `loadAd`（应 `showAd` 消费缓存）
- [ ] 开屏 `destroy()` 已调用
- [ ] Banner Tab 切换不重复 load（复用实例或 forceReload 语义清晰）
- [ ] 新增注释为简体中文
- [ ] 无 BuildConfig 私加 ad_id 兜底

## 验收（用户真机）

- [ ] Logcat 过滤 `TAG-->>AdRequest` 可见 preload 成功 / cacheHit
- [ ] Logcat 过滤 `TAG-->>vmodify` 可见 `【广告位判定】→ 可用`
- [ ] 插屏无缓存时不崩溃、可继续业务流程（showSkippedNoCache）
- [ ] A 面不展示 B 专属位（Banner、语言广告等）
- [ ] B 面 + FC 成功后可展示 Banner
- [ ] 订阅用户不展示广告
- [ ] 关 VPN 后 FC 与广告请求正常（排除 SSL 证书问题）
