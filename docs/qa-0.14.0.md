# QA — alpha 0.14.0（Operit 外观偏好 JSON 导入）

- 版本：`0.14.0`，versionCode **1449**
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu off`）
- 时间：2026-10-27 20:50–21:02（CST）
- 目标：把 Operit / Operit2 的外观备份 JSON 接入现有外观导入链路，验证嵌套 `values`、颜色、字号、磨砂、背景不透明度与强调色映射。

## 1. 新增映射

`OxChatTheme.parseJsonObject` 在识别 Kelivo / RikkaHub 键之前先处理 Operit：

- 若根对象含 `values` 对象，把其中的键合并到顶层（Operit 外观备份格式）。
- 识别标志键：`bubble_ai_bubble_color`、`bubble_user_bubble_color`、`font_scale` 等。
- 字段映射：
  - `bubble_ai_bubble_color` → 收到气泡
  - `bubble_user_bubble_color` → 发出气泡
  - `bubble_ai_text_color` / `bubble_user_text_color` → 消息文字色
  - `font_scale` → 消息字号
  - `background_image_opacity` → 背景不透明度
  - `theme_mode`（`dark` / `light`）→ 背景基色 `#101114` / `#F7F7FA`
  - `use_background_blur` 或玻璃开关 → 气泡样式“磨砂”
  - `custom_primary_color` → 气泡边框颜色，1dp
- 未知键忽略；导入仍先走 `previewBytes`/`previewJson` 摘要，确认后才写当前作用域。

## 2. 设备验证 — PASS

测试文件 `/sdcard/Download/operit-appearance.json`：

```json
{
  "values": {
    "theme_mode": "dark",
    "bubble_ai_bubble_color": -13817275,
    "bubble_user_bubble_color": -8626945,
    "bubble_ai_text_color": -1,
    "bubble_user_text_color": -1,
    "font_scale": 1.15,
    "background_image_opacity": 0.8,
    "use_background_blur": true,
    "custom_primary_color": -53867
  }
}
```

### 2.1 预览

- Ox 设置 → 外观 → Agent 会话外观 → 导入主题 / 预设 JSON → 选择该文件。
- 预览弹窗文案：
  `背景 ox_chat_bg · 收 #2D2A45 · 发 #7C5CFF · 100% · 16dp · 1.1x · 磨砂 · 边框 1dp`
- 预览只读取不写盘；取消不会改变现有外观。

### 2.2 应用后外观页

| 行 | 结果 |
|---|---|
| 外观模式 | 自定义 |
| 背景样式 | 图片（沿用当前背景图） |
| 聊天背景 | `#101114` |
| 背景不透明度 | 80% |
| 收到的气泡 | `#2D2A45` |
| 发出的气泡 | `#7C5CFF` |
| 气泡样式 | 磨砂 |
| 气泡边框颜色 | `#FF2D95` |
| 气泡边框宽度 | 1 dp |
| 消息文字颜色 | `#FFFFFF` |
| 气泡透明度 | 100% |
| 气泡圆角 | 16 dp |
| 消息字号 | 1.1x（源值 1.15，界面保留一位小数） |

### 2.3 聊天渲染

- 打开 `**Bold** Card`：收气泡深紫 `#2D2A45`、白字、洋红 `#FF2D95` 1dp 边框；发气泡紫色 `#7C5CFF`。
- 背景沿用该会话已有的 `ox_chat_bg` 图片并按 80% 不透明度合成，暗化 24%；无白底、无不可读文字。
- 截图：`/tmp/imp1.png`（预览）、`/tmp/chat_operit.png`（聊天）。

## 3. 行为说明与边界

- 导入是“在当前快照上覆盖命中的字段”，未在 Operit 文件中出现的背景图/字体继续保留；预览摘要会显示这些继承值，因此应用前可见。
- Operit 的 `values` 内若同时存在 Kelivo 风格键，Operit 映射优先；未命中任何已知键时按原 JSON 主题逻辑处理。
- `theme_mode` 只在导入时决定背景基色，不强制切换 Telegram 原生主题；浅色/深色仍由 Telegram 主题控制。
- 兼容性收紧：只有命中 Operit 专有的气泡/文字/玻璃键才进入 Operit 分支；仅含
  `font_scale` 或 `background_image_opacity` 的 Kelivo/RikkaHub JSON 不会被误判，
  且缺少 `theme_mode` 时不再改写背景基色。重建后用同一文件复验，结果不变。
- 已知视觉缺口：Agent 工具调用状态气泡（🔧 行）仍沿用原生白色，不随自定义收发气泡
  着色；不影响文字气泡与边框，已记入后续外观缺口。
- 本轮未覆盖：Operit 9-patch 气泡图、自定义头像/字体 URI、逐气泡内边距与图片裁剪。
- 无 FATAL/ANR；导入过程未修改或删除用户会话、Agent、记忆与工作区数据。

## 4. 产物

- `dist/OrionekoX-0.14.0-alpha-arm64-x64.apk`（versionCode 1449），sha256 `5c7a27b98bfa9552dcd785790311946ecd9d645707de6e933f0933447751c278`
- `dist/OrionekoX-0.14.0-debug-arm64-x64.apk`，sha256 `2807a1dc3197a4cbb5250931b8903698b47dc58bff70485502a556629d5620cf`
- 版本规则：本轮为兼容性小步功能迭代，0.13.0 → 0.14.0。
