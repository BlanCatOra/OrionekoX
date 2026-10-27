# QA — alpha 0.26.2（Operit 背景模糊映射修正）

- 版本：`0.26.2`，versionCode **2629**（`0.26.1` 为 2619）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 04:04–04:14（CST）
- 目标：修正 Operit 外观 JSON 的模糊映射：`use_background_blur` 和
  `background_blur_radius` 之前只把气泡设为磨砂，没有真正模糊壁纸；现在映射到
  OrionekoX 的 1–3 级背景模糊。
- 版本规则：仅修既有导入字段映射，按 patch 从 0.26.1 → **0.26.2**，
  versionCode 2629（262×10+9）。

## 1. 修正内容

- Operit 分支识别 `use_background_blur=true` 或 `background_blur_radius>0` 时：
  - `backgroundBlur = radius >= 20 ? 3 : radius >= 12 ? 2 : 1`；
  - 不再因为 `use_background_blur` 把 `bubbleStyle` 设为磨砂；
  - `bubble_*_liquid_glass` / `water_glass` 仍映射为磨砂气泡。
- 背景图本身仍按当前快照继承；只有图片背景会出现可见模糊。

## 2. 设备验证 — PASS

测试 JSON（Operit 字段）：
`bubble_ai_bubble_color=#2D2A45`、`bubble_user_bubble_color=#7C5CFF`、
`background_image_opacity=0.8`、`theme_mode=dark`、`use_background_blur=true`、
`background_blur_radius=24`、`custom_primary_color=#FF2D95`。

- 在外观页导入该 JSON，预览摘要显示
  `背景 … · 收 #2D2A45 · 发 #7C5CFF · 100% · 模糊强 · 16dp · 1.1x · 行高 1.4x · 磨砂 · 边框 1dp`。
- 应用到 R2B Agent 后 `agent_555000016.json` 含 `"backgroundBlur":3`。
- 打开“预览当前外观”：壁纸文字/边框被明显抹平，气泡颜色、边框与文字保持；
  截图：`docs/screenshots/qa-0.26.2-operit-blur.jpg`。
- 无 FATAL/ANR；测试后删除该 Agent 覆盖文件，恢复跟随全局。

## 3. 未覆盖 / 已知边界

- 半径阈值是启发式（≥20 强、≥12 中、其余轻）；Operit 的精确 sigma 没有直接对应。
- 仅图片背景有可见模糊；纯色/渐变背景不受影响。
- 仍未映射 Operit 9-patch 气泡图片、输入样式、头像形状、内容 padding、图片
  crop/repeat/scale、背景视频等字段。
- 测试只覆盖 Agent 作用域；全局/会话复用同一解析逻辑。

## 4. 产物

- `dist/OrionekoX-0.26.2-alpha-arm64-x64.apk`（versionCode 2629），sha256
  `033b4c544e668e3f96759e21c29f1166bbac2126ff28dcd1bf2b63b4cbc07861`
- `dist/OrionekoX-0.26.2-debug-arm64-x64.apk`，sha256
  `3ed2cc9252bdffcccd9e3fbcb7216a043f06a237cc9faa93b39c31f630fbfdd5`
