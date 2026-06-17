# A/B 面广告远程配置 — 检查清单

## Firebase

- [ ] 控制台已配置 `ad_config_a`、`ad_config_b`（String JSON）
- [ ] bootstrap 在 `applyByMode` **之前**完成 `fetchAndActivate`（≤8s 超时仍 apply）

## 生效顺序（定稿）

- [ ] `applyByMode` **先**应用 assets 本地 JSON（`使用【本地 assets】`）
- [ ] **再**读远程；非空且可 parse 时覆盖（`使用【远程 Firebase】覆盖`）
- [ ] 远程空/非法时**保留**本地，不是重新才读 assets
- [ ] **未**使用旧逻辑「远程优先、远程空才 assets」

## assets

- [ ] `ad_remote_config_default_a.json` / `_b.json` 已放入 assets（或 template 可复制）
- [ ] A/B 候选顺序：`.json` 优先于 `.template.json`

## 面判定与桥接

- [ ] `isModeB` 在 `applyByMode` 之前已确定
- [ ] A 面读 `ad_config_a`，B 面读 `ad_config_b`
- [ ] `getAdId` 无 BuildConfig 第四级回退

## 场景自测（Logcat `AdRemoteConfig`）

- [ ] 远程有效：先见本地 assets 日志，再见「远程覆盖」
- [ ] 远程空：仅本地/assets 生效，「保持本地/assets 配置」
- [ ] fetch 超时：本地仍先行生效
- [ ] B 面 `isModeB=true` 读 `ad_config_b` + assets B

## 质量

- [ ] 改动文件 Lint 通过（不编译）
