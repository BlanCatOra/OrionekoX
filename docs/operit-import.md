# 从 Operit / Operit2 迁移到 Ox

Ox 读取两个应用主动导出的公共备份目录，不读取其它应用的私有数据。

## Operit (v1)

在 Operit 里导出：

1. 角色卡：`Character Card Editor → 卡片菜单 → Export`，或
   `Settings → Data Backup & Restore → Character Cards → Export`；
2. 聊天记录：`Data Backup & Restore → Chat History → Export`，选择 JSON；
3. MCP：MCP 管理页本身把配置写在公共目录。

文件位置：

```
Download/Operit/backup/character_cards/*.json
Download/Operit/backup/chat/*.json
Download/Operit/mcp_plugins/mcp_config.json
```

## Operit2

Operit2 的快照包含角色卡、聊天 SQLite、MCP 配置、模型配置（含 API Key）和
`USER.md` 记忆。可以在 Operit2 里制作快照，或使用 CLI：

```bash
operit2 cli export snapshot /path/to/snapshot.zip
```

把 zip 放到：

```
Download/Operit2/backup/*.zip
```

## Ox 导入

`设置 → Ox 设置 → 从 Operit / Operit2 导入`。

- 首次需要授予“所有文件访问权限”（系统设置里打开）。
- Ox 会扫描上面的目录，导入角色卡、聊天、MCP 和记忆。
- 如果 Operit2 快照里有可用的模型配置（endpoint + apiKey），导入的 Agent 会
  直接使用该 Provider（OpenAI 兼容、API Key 模式），并把上下文长度设置为快照
  里的 `maxContextLength`。
- 聊天会同时写入 Ox 的聊天存储和原生 Telegram 消息库，打开 Agent 即可看到
  迁移后的会话；重复导入会去重。
- 已存在的同 URL MCP 服务不会重复添加。
