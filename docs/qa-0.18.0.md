# QA — alpha 0.18.0（主题预设包 .oxthemes）

- 版本：`0.18.0`，versionCode **1809**（`0.17.1` 为 1719）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-27 23:25–23:58（CST）
- 目标：补齐竞品“预设包导出/导入”缺口：把全部命名预设连同背景图、聊天/代码
  字体打成一个 `.oxthemes` 文件，换机/分享后可预览、去重、幂等导入。
- 版本规则：新增可携带资产的多预设包能力，按 minor 从 0.17.1 → **0.18.0**，
  versionCode 1809（180×10+9）。

## 1. 新增能力

- 包格式：zip，`bundle.json`（`format=orionekox-theme-bundle`、`version=1`、
  `presets[]`，每项含 `name`、`theme`、`assets`）加
  `presets/<n>/background.*`、`chat-font.ttf`、`code-font.ttf`。
- 导出：读取 `files/ox_chat_presets/*.json`，把仍存在的背景/字体文件一起打包；
  资产缺失时仍导出颜色/气泡/行高/模糊等快照。
- 导入预览：显示预设数量、前 8 个名称、已存在/同名将跳过的数量、含背景图与
  含字体的预设数；取消不写入。
- 导入策略：整包先校验（格式、条目数、单资产 8 MB、总资产 40 MB、最多 100 个
  预设），通过后才写盘；同 sanitize 后的名称已存在则跳过，不覆盖用户预设；
  资产恢复到 app-private 新文件名并改写快照路径；写入中途失败会删除本次创建的
  资产和预设文件（回滚）。损坏包/非 zip 直接拒绝，不产生任何文件。
- 外观页“导入主题 / 预设 JSON”区域新增“导入预设包 / 导出预设包”两行，沿用
  UniversalRecyclerView + SettingCell + 原生 SAF + AlertDialog。

## 2. 设备验证 — PASS

### 2.1 无资产包导出与重复导入

- 现有 `DarkSea`、`R2C-Image` 两个纯色预设导出为
  `orioneko-presets.oxthemes`（506 B，仅 `bundle.json` 1588 B）。
- 导入预览：`预设 2 个：DarkSea / R2C-Image`、`已存在或同名 2 个，导入时跳过`、
  `含背景图 0 个 · 含字体 0 个`。
- 确认导入后提示“已导入 0 个预设，跳过 2 个”，两个 JSON 的内容与 mtime 未变，
  证明幂等且不覆盖。
- 截图：`docs/screenshots/qa-0.18.0-bundle-preview.jpg`。

### 2.2 带背景资产包的导出、删除、再导入

- 以当前全局外观（背景图 `ox_chat_bg`、模糊关、1.15× 字号、1.4× 行高）保存
  预设 `BundleQA`，导出为 `orioneko-presets (1).oxthemes`（4713 B），内容：
  `bundle.json` 2481 B + `presets/0/background.img` 16360 B。
- 长按删除 `BundleQA` 后重新导入同一包：预览显示
  `预设 3 个：BundleQA / DarkSea / R2C-Image`、`已存在或同名 2 个`、
  `含背景图 1 个 · 含字体 0 个`；确认后提示
  “已导入 1 个预设，跳过 2 个”。
- 恢复的 `BundleQA.json` 为 649 B，`backgroundImage` 指向新文件
  `files/ox_chat_bg_preset_1793115639909_0.img`（16360 B），不是导出机的旧路径；
  点击该预设提示“已应用预设：BundleQA”，`ox_chat_theme.xml` 的
  `background_image` 同步指向新资产，背景显示正常。
- 截图：`docs/screenshots/qa-0.18.0-bundle-imported.jpg`。

### 2.3 非法包

- 把 13 B 的纯文本命名为 `invalid.oxthemes` 导入：提示
  “不是有效的 OrionekoX 预设包”，预设列表与资产文件数量均未变化。
- 截图：`docs/screenshots/qa-0.18.0-bundle-invalid.jpg`。

### 2.4 文案与中文包

- 首轮安装（包版本 84）中新增/修改的字符串未生效：导入行仍显示旧提示，导出
  行回退英文。根因是修改 `zh-cn.xml` 后没有再次提高
  `OX_ZH_PACK_VERSION`，设备上已有同版本包不会重新拷贝。
- 包版本 84 → 85 → 86 重建后，最终包显示“导入预设包 / 导出预设包”、
  “.oxthemes · 同名自动跳过”“全部命名预设，含背景与字体”；预览标题为
  “导入这个预设包？”，确认按钮为“导入”。
- 截图：`docs/screenshots/qa-0.18.0-bundle-rows.jpg`。

## 3. 未覆盖 / 已知边界

- 当前测试预设都不含自定义字体，`chat-font.ttf` / `code-font.ttf` 的打包与恢复
  只做代码审查，未做设备往返；资产缺失时的纯色回退也未单独截图。
- 单资产超过 8 MB 时导出会跳过该资产（保留颜色与气泡），未构造大文件验证。
- 回滚路径（写盘中途失败）只做了代码审查；非法包在写盘前即被拒绝，已验证。
- 包内预设上限 100、zip 条目上限 300、总资产上限 40 MB；超限包未做设备样本。
- 与 Kelivo/RikkaHub 全量备份导入仍相互独立；本轮只完成 OrionekoX 自有预设包。
- 模拟器仍为 `-gpu guest`（`-gpu off` 的宿主渲染崩溃见 0.17.1 QA），真机/GPU
  复验仍需补做。
- 测试用 `BundleQA` 预设及其导入资产保留在模拟器作为回归样本；Downloads 中的
  临时 `.oxthemes` 与非法文件已删除；未修改真实联系人、未清理用户数据。

## 4. 产物

- `dist/OrionekoX-0.18.0-alpha-arm64-x64.apk`（versionCode 1809），sha256
  `d9dbb96c518f4fd2e03bd51b8858a763b02dc20456947b1c8b6009f9ccaf2a18`
- `dist/OrionekoX-0.18.0-debug-arm64-x64.apk`，sha256
  `6cd3a82e130da0a164373013b64cf923588a12dcadfd140ef3d965481092f4eb`
