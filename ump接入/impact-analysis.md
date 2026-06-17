# UMP 方案 — 启动页与业务影响

> 定稿：**先读缓存 + 欧盟预筛 + 双闸门 + Application 提前 init**  
> 可移植包：`skill/ump接入/portable/` · 金样：`tools/browser/pdf`

---

## 更新 skill 的影响范围

| 位置 | 是否变更 | 说明 |
|------|----------|------|
| `skill/ump接入/` | ✅ | SKILL.md、portable/、templates/、文档 |
| `skill/admob广告/` | 仅链接 | 交叉引用 ump skill，逻辑不变 |
| PDF 工程 | ❌ 不改 | 仍为金样；新项目用 portable/ |
| 已上线 APK | ❌ | skill 不参与编译 |
| `~/.cursor/skills` | ❌ 不自动同步 | 需指定 skill 路径或手动复制 |

**面向各项目接入**：拷贝 `portable/` 5 个文件 + templates 片段，无需新建 Gradle 模块。

---

## 结论速览

| 问题 | 答案 |
|------|------|
| 启动页骨架会变吗？ | **不会**（放行闸、goNext、热启动结构保留） |
| 国内还会弹 UMP 吗？ | **通常不会**（无缓存时跳过 gather；有缓存也跳过） |
| 国内冷启动变慢吗？ | **基本不会**（非欧盟不走 UMP 网络） |
| 拒绝还请求广告吗？ | **是**（不以 `canRequestAds` 阻断） |
| Application 提前 init 会提前发广告吗？ | **不会**（`isUmpResolved` 拦住） |

---

## 与旧版差异

| 维度 | 旧（全球 gather） | 现（v1.2.0） |
|------|-------------------|---------------|
| 决策顺序 | 地区由 SDK 判 | **先读缓存** → 无缓存再欧盟预筛 |
| 非欧盟冷启动 | 仍调 UMP 网络 | **跳过 UMP** |
| 二次冷启动 | 每次可能 gather | **读缓存跳过** |
| init 时机 | UMP 后 init | **Application 可提前 init** |
| 请求闸门 | `isInit` | **`isInit + isUmpResolved`** |
| 拒绝后广告 | 可能阻断 | **不阻断** |
| UMP 转圈 | 冷启动均可能显示 | **仅 willRunUmpGather** |
| Debug 联调弹窗 | reset + 强制 EEA | **`setDebugGeography(EEA)` + 测试 hashed id**（仅 Debug）；AdMob 消息须 Publish |

---

## 未改动部分

- `runLaunchPipeline` / `awaitReleaseGate` / `showSplashOrGoNext`
- `goNext` 路由（语言 / 引导 / 主页）
- 热启动叠栈、`HOT_LOADING_SPLASH(4)`
- 放行闸常量（2s / 10s）
- VIP `isSubs` 短路

---

## 各用户群影响

### 国内（CN）无缓存

- 不调 UMP → Loading **更短**
- 无转圈 → UI **更干净**
- 与旧版「非欧盟跳过」一致

### 欧盟（DE）首次

- 走 gather → 可能弹窗
- 转圈 + 藏进度条
- 同意/拒绝后均请求广告，**结果缓存**

### 欧盟二次冷启动

- 读缓存 → **无 gather、无弹窗、无转圈**

### 弱网 UMP

- UMP 无专用超时 → 启动页可能长时间等待（与旧版一致）
- UMP 结束后仍请求广告（不因 `canRequestAds` 失败而永久无广告）

---

## 真机必测

1. 清数据 + 系统地区 CN：无 UMP 日志 gather，开屏正常  
2. 系统地区 DE 首次：gather + 可能弹窗 + 缓存写入  
3. 二次冷启动 DE：`读取 UMP 缓存`  
4. 拒绝后：仍能开屏 load  
5. Logcat：`isInit=true` 且 `isUmpResolved=false` 期间无成功广告请求  

---

## 可选增强（当前未做）

- UMP `requestConsentInfoUpdate` 失败时客户端超时强制 `markUmpResolved`（需产品确认）
- 设置页「隐私选项」入口 `showPrivacyOptionsForm`（金样已预留 API）
