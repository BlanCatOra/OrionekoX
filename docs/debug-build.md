# `.debug` 测试包（两个模式独立测试）

正式包和测试包可以同时安装、互不影响数据：

| | 正式包 | 测试包 |
|---|---|---|
| applicationId | `io.github.blancatora.orionekox` | `io.github.blancatora.orionekox.debug` |
| 桌面名称 | OrionekoX | OrionekoX Debug |
| versionName | `0.8.19` | `0.8.19-debug` |
| 构建类型 | `standalone`（R8、资源压缩、release 签名） | `debug`（不混淆、完整日志、同一签名） |
| 数据目录 | `/data/data/io.github.blancatora.orionekox` | `/data/data/io.github.blancatora.orionekox.debug` |

`.debug` 包与正式包签名相同但包名不同，所以可以并存；Telegram 的 FileProvider /
notification provider / call provider 都用 `${applicationId}.xxx`，不会冲突。

## 构建

```bash
cd <repo>
export JAVA_HOME=~/orioneko-toolchain/jdk17
./gradlew :TMessagesProj_AppStandalone:assembleAfatDebug
```

产物：`TMessagesProj_AppStandalone/build/outputs/apk/afat/debug/app.apk`

安装（可与正式包共存）：

```bash
adb install -r TMessagesProj_AppStandalone/build/outputs/apk/afat/debug/app.apk
adb shell pm list packages | grep orionekox
```

## 为什么需要它

- 正式包已经登录真实账号；在正式包里切 Ox 模式会退出登录并清空本地账号数据。
- `.debug` 包是独立数据目录，可以随时 `pm clear` 重跑首次引导，不影响正式包。
- debug 构建保留完整 `tgnet` / `tmessages` / `OxBridge` 日志，便于验证 Ox 模式是否真的离线。

## 两个模式的测试路径

**Ox 模式（本地、无需账号）**

1. 首次启动 → 六步引导；
2. 第 3 步选 “Not now — start in Ox mode”；
3. 第 4/5 步可 Skip，第 6 步 “Start using OrionekoX”；
4. 聊天列表出现 3 个内置智能体；点任一打开的是 stock `ChatActivity`；
5. 发消息、收回复、删除消息都在本地完成；
6. 整个首次引导期间应完全没有 `tgnet.*connecting`；进入 Ox 模式后同样无新增
   （`adb logcat -d | grep -E "tgnet.*connecting"`）。

**登录模式**

1. 聊天列表右上角 ⋮ → “Sign in to Telegram” → OK；
2. 进入手机号登录页（这会清掉 `.debug` 包里的本地账号数据）；
3. 不登录时点 “Use Ox mode” 可回到 Ox 模式，验证双向切换；
4. 选择登录模式后网络会自动恢复（引导选“Sign in to Telegram”时调用
   `ConnectionsManager.resumeAfterOnboarding()`）；
5. 要真实验证登录模式，输入测试账号验证码即可；正式包的登录态不受影响。

## 注意

- `google-services.json` 里必须有 `io.github.blancatora.orionekox.debug` 的 client 条目，
  否则 `processAfatDebugGoogleServices` 会因找不到匹配包名而失败。
- debug 包未混淆、带完整日志、体积更大，只用于测试，不用于分发。
- `adb shell pm clear io.github.blancatora.orionekox.debug` 只清测试包数据，可用于重跑引导。
