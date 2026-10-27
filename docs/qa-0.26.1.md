# QA — alpha 0.26.1（Kelivo Provider 配置接线）

- 版本：`0.26.1`，versionCode **2619**（`0.26.0` 为 2609）
- 设备：emulator-5554（1080×2400，Android 14/34，`-gpu guest` 软件渲染）
- 时间：2026-10-28 03:50–04:02（CST）
- 目标：补完 Kelivo/Chatbox 助手导入的 Provider 接线：读取备份
  `settings.json` 中的 `provider_configs_v1`，按助手的 `chatModelProvider`
  选择对应 baseUrl/apiKey/models，而不是一律继承本机第一个 Provider。
- 版本规则：仅补既有导入的 Provider 映射，按 patch 从 0.26.0 → **0.26.1**，
  versionCode 2619（261×10+9）。

## 1. 新增能力

- 解析 `provider_configs_v1`（JSON 字符串、数组或 id→配置映射）以及嵌套
  `providers`，建立 provider id → 配置映射；仅接受有 `baseUrl` 的条目。
- 导入助手时：
  - 若 `chatModelProvider` 命中备份 Provider，使用其 `baseUrl`/`apiKey`，
    模型优先取 `chatModelId`，否则取 models 第一个（字符串或
    `apiModelId`/`id`/`modelId`）；
  - 未命中时回退本机现有 Provider；都没有则离线。
- 预览新增“将使用备份中的 N 个 Provider 配置为助手接线”；结果新增
  “使用备份中的 N 个 Provider 配置接线”。不显示任何 Key。
- 重复导入仍按 `kelivo_<id>` 更新，不新建。

## 2. 设备验证 — PASS（schema 样例）

样例 zip：`provider_configs_v1` 含
`prov-1 = {baseUrl: https://kelivo.example/v1, apiKey: <测试值>,
models:[kelivo-test-model]}`；Kelivo Assistant 的 `chatModelProvider=prov-1`；
Chatbox Copilot 无 Provider 引用。

- 预览：`助手 2 个`，note“将使用备份中的 1 个 Provider 配置为助手接线”。
- 导入：`新建助手 2，更新 0`；note“使用备份中的 1 个 Provider 配置接线”。
- `kelivo_ka-1/agent.json`：`base_url=https://kelivo.example/v1`、
  `model=kelivo-test-model`、temperature 0.5、top_p 0.9、maxTokens 256、
  `X-Kelivo` header 正确。
- Chatbox Copilot 无 Provider 配置，按预期回退本机现有 Provider。
- 重复导入：`新建助手 0，更新 2`，仍 2 个 Agent。
- `logcat` 检索测试 Key 命中 0；无 FATAL/ANR。
- 清理：删除两个测试助手与 Downloads 样例。

## 3. 未覆盖 / 已知边界

- Provider 的 OAuth、多 Key、代理、自定义 headers/body、Vertex/Google 特殊
  配置未导入；只使用 baseUrl/apiKey/models 基础字段。
- 只导入被助手引用的 Provider，不会单独保存 Provider 列表。
- models 只取第一个；`chatModelId` 可能为逻辑 id，不等于服务端真实模型名。
- 样例按 Kelivo 源码字段构造，真实备份仍待复测。

## 4. 产物

- `dist/OrionekoX-0.26.1-alpha-arm64-x64.apk`（versionCode 2619），sha256
  `18cd44f4e3d39e9809fac9f9343965fc1edeac57911d0d580a6e15319ac4d885`
- `dist/OrionekoX-0.26.1-debug-arm64-x64.apk`，sha256
  `e0e1eb8209abee9b17330e4b41d4e7f1bf5a336999c209c233735f8c286c5a09`
