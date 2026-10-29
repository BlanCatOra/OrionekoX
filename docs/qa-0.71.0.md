# QA — alpha 0.71.0（终端配色对比度重调 + PWD/PST 键）

- 版本：`0.71.0`，versionCode **7109**（`0.70.0` 为 7009）
- 设备：emulator-5554（1080×2400，Android 14/34，无窗口）
- 时间：2026-10-29 12:50–13:05（CST）
- 目标（用户反馈）：① 预设看不清字（Nord 举例），要逐套人性化/美观；
  ② Termius 有"一键输密码"的快捷键；③ 钥匙按钮太突兀，要参考 Termius
  的实现。

## 1. 对比度审计与重调（11 套终端配色）

用 WCAG 相对亮度公式对每套的 text/dim/accent 相对背景计算对比度，发现
问题集中在 dim（Nord 1.69:1、One Dark 2.32、Solarized Light 2.48）与
输出行被误判为 dim（行首缩进 "  " 即弱化）——空屏欢迎语几乎全 dim。

重调后（目标 text ≥6.5、dim ≥4.0、accent ≥4.5）：

| 主题 | text | dim | accent |
|---|---|---|---|
| Termius Dark | 14.90 | 8.21 | 14.53 |
| Termius Light | 15.39 | 4.93 | 6.95 |
| Solarized Dark | 8.21 | 4.94 | 4.75 |
| Solarized Light | 6.59 | 4.16 | 5.00 |
| Dracula | 13.36 | 4.81 | 5.90 |
| One Dark | 10.00 | 4.07 | 6.94 |
| Nord | 10.26 | 4.06 | 6.24 |
| Gruvbox Dark | 10.75 | 5.30 | 8.69 |
| Tokyo Night | 10.59 | 4.80 | 6.79 |
| Monokai | 13.94 | 5.05 | 9.58 |
| Pure black | 17.14 | 7.46 | 16.01 |

同时修正渲染：仅 `session ...` 欢迎横幅使用 dim，命令输出/缩进结果恢复
正常文字色（此前 `  ` 开头即弱化，是"看不清"的主因）。Nord 实机复查
（截图 01-nord-fixed）文字清晰可读。

## 2. 一键输密码（PWD）与粘贴（PST）

- 参考 Termius：其热键行全部是等宽文本键、无 emoji；密码由凭据提示/
  保存密码完成，而不是单独一个彩色钥匙。据此把首发版 🔑 emoji 键改为
  **等宽文本键 PWD**（与 Esc/Tab 同字号同键帽），粘贴键为 **PST**，
  置于键行尾部（滚动可达），观感与其它键一致。
- 选项面板新增 TERMINAL SECRET (LOCAL)：密码型输入 + Save secret；
  存在本地 SharedPreferences（`term_secret`），**不入皮肤 JSON 导出**。
- PWD 一键把 secret 填入输入框；未设置时 Toast 提示去 ⋯ 设置。PST 把
  剪贴板文本填入输入框。
- 设备验证：设置 `term_secret=abc123` 后点 PWD，输入框出现 abc123
  （截图 02/03）；键行滚动后 PWD/PST 与其它键风格一致（截图 05）。

## 3. 产物

- release：`dist/OrionekoX-0.71.0-alpha-arm64-x64.apk`（versionCode 7109）
  sha256 `ea48e84b7b289c6ac807e6e9ac6447656f954754e0f618ceaa8151b16aeca7a3`
- debug：`dist/OrionekoX-0.71.0-debug-arm64-x64.apk`
  sha256 `8738b14ef56473710c0a93943f81cff3eca96b2e89e9a1c7f73461287c8ccc09`
- 构建日志 `/tmp/ox-build-0710-final2.log`；截图 `/tmp/oxui-710/`。

## 4. 边界

1. 对比度为静态审计；未做色盲模拟与 OLED 实测。
2. PWD 是本地明文存储（与现有 agent API key 同级别），未接入 Keystore；
   已从皮肤导出去除。
3. PWD/PST 在键行尾部，需横向滚动才能看到（Termius 同样为可滚动键行）。
4. 未实现 Termius 的"连接时密码提示 + 保存密码自动填充"（本地控制台
   无 SSH 会话/无 shell）。
