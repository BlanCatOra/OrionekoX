# QA — alpha 0.70.0（组件级 DIY + 皮肤导入 + 真实模糊 dock + Termius 终端配色）

- 版本：`0.70.0`，versionCode **7009**（`0.69.0` 为 6909）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口启动）
- 时间：2026-10-29 12:25–13:05（CST）
- 目标：0.70.0 首要——组件级 DIY 细化、皮肤 JSON 导入分享、真实模糊
  材质；另按用户追加要求，内置 Termius 风格终端配色/背景设定。

## 1. 实现

1. **组件级 DIY（OxUiSkin 新字段）**：
   - `density`（cozy | compact）：消息行距 0.62×、行内间距收紧（`rowGap()`）。
   - `showTimestamps`：用户气泡时间戳整体开关（普通与引用两分支）。
   - `showAccent`：assistant 荧光强调条开关。
   - `dockItems`（bitmask，Agents/Search/Terminal/Settings）：dock 按位过滤，
     空集时不渲染 dock。
   - 设置页新增 COMPONENTS 小节：Density 二选、Timestamps/Assistant accent
     开关、Dock items 四个 shown/hidden 开关；全部写入皮肤 JSON。
2. **皮肤 JSON 导入**：Customize 底部新增 “Import JSON…” → 粘贴面板
   （默认带出当前皮肤 JSON）→ Apply 解析 `OxUiSkin.fromJson`、以 custom
   身份落盘并 recreate；非法 JSON 给 “Invalid skin JSON”。
3. **真实模糊 dock**：floating 导航改为覆盖式 dock（`dockHost` overlay），
   `updateDockBlur()` 把 dock 区域背后的画面快照降采样 6×→两遍 3×3
   box blur→按材质 tint（glass 荧光 20% / mica 骨纸 80% / acrylic 60%）
   生成圆角 LayerDrawable 作为 dock 背景；滚动 260ms 防抖更新；无法取
   快照时回退 drawable 材质。这是真实像素模糊（非纯半透明）。
4. **Termius 风格终端配色（内置 11 套）**：Termius Dark/Light、Solarized
   Dark/Light、Dracula、One Dark、Nord、Gruvbox Dark、Tokyo Night、
   Monokai、Pure black；每套 4 色（bg/text/dim/accent），终端提示符、
   ↵ 键、会话 tab 下划线、输入框描边、分隔线全部随 accent；选项面板可
   滚动并带三色 swatch 预览；活动 tab 文字色随主题（修复浅色主题下
   浅灰不可读问题）。`termTheme` 持久化进皮肤 JSON。

## 2. 设备验证（debug 7009）

1. 玻璃悬浮 dock 覆盖在输入栏上方，背景呈现背后内容的柔化色块（真实
   快照模糊）；位置切到 top 时 dock 覆盖 header 下方内容。
2. 终端选项列出 11 套配色（三色 swatch + active 标记）；Termius Light
   实测（浅底/深青提示/青色 ↵），Solarized Light 实测（米底/蓝调），
   Nord 实测（石板蓝/冰蓝），均即时生效并写入皮肤 JSON
   （`termTheme=termius-light/solarized-light/nord`）。
3. 组件级开关：Density/Timestamps/Accent/Dock items 已通过设置页接入
   （实现与编译验证；完整逐项截图待下一轮补充）。
4. 皮肤导入：UI 已接入（Copy JSON/Import JSON 对称）；导入路径编译与
   逻辑验证，端到端粘贴截图待下一轮补充。
5. release 7009 安装启动无本包 FATAL。

## 3. 产物

- release：`dist/OrionekoX-0.70.0-alpha-arm64-x64.apk`，sha256
  `02a220f9adf3ebd2387faa86b65e19b91cd8966feb0354633f9ae96dd39514b0`
- debug：`dist/OrionekoX-0.70.0-debug-arm64-x64.apk`，sha256
  `86b9ba1f15b62ede0d9697438040bbfd62c8b2c1dd9fa939af7d732a706c5aa9`
- 构建日志 `/tmp/ox-build-0700-final.log`；终端主题截图 `/tmp/oxui-700/`。

## 4. 边界 / 下一步

1. 模糊 dock 为快照+box blur 近似（每 260ms 防抖重算），非 SurfaceFlinger
   级实时模糊；滚动期间有轻微滞后，真机 GPU 复验后再评估性能。
2. Density 目前影响消息行距与顶部间距，未覆盖输入栏与列表行；下一轮补齐。
3. 皮肤导入仅粘贴 JSON，未做文件/二维码分享；dock 项只支持显示/隐藏，
   排序未开放。
4. Termius 终端配色为静态 4 色表（bg/text/dim/accent），未实现其完整
   ANSI 16 色与光标色自定义。
5. backlog 不变：视频/GIF/文件附件、跨会话搜索聚合、平板双栏、
   TalkBack、群 emoji 头像、用户头像族、改名离线化、终端 PTY。
