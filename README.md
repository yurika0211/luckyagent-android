# luckyagent-android

LuckyAgent 的 **Android 客户端**（独立仓库）。手机只当客户端，Agent / 模型 / 记忆 / 工具仍跑在电脑上的 `lh serve`。

> 不把 Swift/Kotlin 塞进 Go 主仓；与 `lucky-agent`、未来的 `luckyagent-ios` 并列。

## 架构

| 层 | 谁负责 |
|----|--------|
| Agent / 模型 / 记忆 / 工具 | 电脑上的 `lh serve`（Go 主仓） |
| Android | 本仓库（Kotlin / Jetpack Compose） |
| iOS | 以后的 `luckyagent-ios`（SwiftUI） |

两端都只调同一套 HTTP API：

- `GET /api/v1/health/live`
- `GET /api/v1/sessions`（支持 `q` 搜索）
- `GET /api/v1/sessions/{id}`（历史消息）
- `POST /api/v1/chat`（可选；本客户端默认走 WebSocket）
- `WS  /api/v1/ws?session=...`
- `GET /api/v1/memory`、`/api/v1/memory/recall`、`/api/v1/memory/graph`

**不走尚未补齐 TLS + 鉴权的 gRPC 对外通道。**

## 当前能力（可日常联调）

- **Chat**
  - WebSocket 事件：`stream_chunk` / `assistant_delta`、`tool_call`、`tool_result`、`stream_end` / `final`、`error`、`status`、`reasoning`、`cancel`
  - 流式助手气泡 + 轻量 Markdown（标题 / 列表 / 粗斜体 / 行内代码 / 代码块）
  - 工具调用卡片（名称、参数摘要、结果、running/done/failed）
  - 连接状态条 + 断线有限次自动重连（最多 8 次，指数退避）
  - 发送 / 停止（cancel）
  - Agent 运行期间普通消息、命令、页面操作、切换会话和修改连接配置不会中断当前任务；只有 Stop 或 `/stop` 会取消
- **Sessions**
  - 抽屉搜索、切换 session、加载历史（含历史 tool_calls）
- **Settings**
  - API Base / API Key / Session
  - Bearer vs `X-API-Key`
  - Health 探测、WS 重连、加密存储 Key
- **Memory**
  - recall 查询、tier stats、graph 摘要（节点/边数量占位，可视化下一迭代）
- **Trajectory / Gateways / Skills**
  - 只读 JSON 面板（对齐 GUI 入口，便于联调）

## 还缺什么

- 会话历史分页 / 向上加载更多
- Memory graph 真正可视化（力导向或列表关系图）
- 附件上传（图片等多模态）
- 证书 pinning / 正式 release 签名密钥（CI 已支持可选 secrets 签名）
- Trajectory 结构化时间线 UI（部分已卡片化，细节可继续对齐 GUI）

## UI 对照 GUI

| GUI (`UI/GUI`) | Android |
|----------------|---------|
| 左侧 sidebar 导航 | `NavigationBar` / `NavigationRail` |
| Chat | `ChatScreen` |
| Trajectory | `TrajectoryScreen` |
| Gateways | `GatewaysScreen` |
| Skills | `SkillsScreen` |
| Settings | `SettingsScreen` |
| Memory graph | `MemoryScreen` |
| 会话列表 + 搜索 | `SessionDrawer` |
| topbar + composer | `ChatTopBar` + `ComposerBar` |
| 四叶草绿主题 | `CloverTheme` |

主题色：bg `#f9faf4` / side `#eef1e5` / surface `#ffffff` / text `#16190f` / accent `#3f8a37`。

## 鉴权

手机连非本机 `lh serve` 时需 `server.api_keys`。客户端请求头：

```http
X-API-Key: <key>
# 或
Authorization: Bearer <key>
```

Key 存在 `EncryptedSharedPreferences`，不进 URL / 默认不进 body 日志。

## 真机联调步骤

1. 电脑启动 API：`lh serve`（监听 `0.0.0.0:9090`，配好 `server.api_keys`）
2. 手机与电脑同一局域网（或 Tailscale）
3. 安装 debug APK 后打开 App → **Settings**
4. API Base 填 `http://<电脑局域网IP>:9090`（模拟器可用 `http://10.0.2.2:9090`）
5. 填 API Key，选择 Bearer 或 X-API-Key
6. 点「保存并探测」看 Health；点「重连 WS」
7. 回 **Chat** 发一条消息；侧栏可搜 session / 切历史
8. **Memory** 可 recall；工具调用应出现卡片


## CI / CD（GitHub Actions）

工作流：`.github/workflows/android.yml`

| 触发 | 行为 |
|------|------|
| PR → `main` | `assembleDebug`，上传 debug APK artifact |
| push → `main` | `assembleDebug`，上传 debug APK artifact |
| tag `v*` | `assembleRelease`（可选签名）+ 创建/更新 GitHub Release 并挂 APK |
| `workflow_dispatch` | 仅 debug 构建（不发 release） |

**Release 策略：只打 `v*` tag 才 release**（例如 `v0.1.0`）。普通 push/PR 只做 debug CI。

### 发版（tag → Release）

```bash
git tag v0.1.0
git push origin v0.1.0
# Actions: build-release → GitHub Release + APK
```

### 本地等价

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

### 可选：Release 签名 secrets

未配置时 CI 产出 **unsigned release APK**（仍可作构建门禁）。配置后自动签名：

| Secret | 含义 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 base64 |
| `ANDROID_KEYSTORE_PASSWORD` | store 密码 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key 密码 |

```bash
base64 -w0 release.jks > keystore.b64   # macOS: base64 -i release.jks
# 把内容粘到 repo Secret: ANDROID_KEYSTORE_BASE64
```

Artifact 在 Actions  run 页面下载；保留 debug 14 天 / release 30 天。

## 本地构建

需要 **JDK 17** + **Android SDK (API 35)**。

```bash
bash scripts/check-env.sh

cp local.properties.example local.properties
# 编辑 sdk.dir=...

# 若缺 wrapper jar 且本机有 Gradle：
gradle wrapper --gradle-version 8.11.1

./gradlew :app:assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:installDebug
```

无 Android Studio / SDK 的机器只能改源码；编译请在装好 SDK 的环境或 CI 进行。

## 目录

```text
app/src/main/java/com/luckyagent/android/
  data/api/          # HTTP + WebSocket 客户端与模型
  data/settings/     # 加密设置
  ui/screens/        # Chat / Settings / Memory / ...
  ui/components/     # MarkdownText
  ui/theme/          # Clover 色板
scripts/check-env.sh
docs/GUI-MAPPING.md
```

## 与主仓关系

- 主仓：`yurika0211/lucky-agent`（Go + `UI/GUI`）
- 本仓：仅 Android 客户端 → `yurika0211/luckyagent-android`
- 协议：`internal/websocket/message.go` + HTTP `/api/v1/*`
- Electron 在主仓 `UI/desktop`；手机端不复用 Electron，也不内嵌 Go 运行时
