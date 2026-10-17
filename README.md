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
  - 同一 query 的多轮推理、工具过程和中途说明统一折叠，最终答复保留在主会话；历史加载与实时工具轮次采用同一规则
  - 工具调用卡片（名称、参数摘要、结果、running/done/failed）
  - 长文本在界面内截断并显示提示：正文最多 32,000 字符，思考/工具输出 16,000 字符，工具参数 8,000 字符；每条消息最多显示 32 个附件。限制只影响客户端展示，服务端原始会话不受影响。
  - 入站帧另有一层保护，和界面展示上限不是同一件事：超过 8,000,000 字符的 WebSocket 帧不解析，记一条截断错误并照常推进事件游标，避免重连反复重放；64,000 字符以内的完整帧才会原样进入调试通道，超限帧不会把半截 JSON 当正常消息。
  - 连接状态条 + 断线有限次自动重连（最多 8 次，指数退避）
  - 发送 / 停止（cancel）
  - Agent 运行期间普通消息、命令、页面操作、切换会话和修改连接配置不会中断当前任务；只有 Stop 或 `/stop` 会取消
- **HTML 展示**
  - 聊天正文、标题、列表和表格共用结构化富文本渲染；支持嵌套样式、换行、字符实体、下划线/删除线/高亮、上下标、HTML 列表/表格/图片与 details 折叠
  - 支持语义颜色及常用行内 CSS（颜色、背景、字号、粗斜体、装饰、对齐）；代码与转义标签保持原文
  - HTML 代码块、完整文档和 HTML 附件可进入整页预览，展示 CSS/SVG/DOM 交互；支持源码切换、缩放和保存/下载，切换源码保留页面交互状态
  - 预览通过隔离域名加载当前文档目录内的 CSS、图片、脚本等资源，支持 artifacts 与普通 HTTP(S) 附件；每次重定向重新校验同源及目录范围，越界即阻止请求。鉴权只附加到配置 API 的 `/api/v1/artifacts`，拒绝目录穿越；任意绝对资源 URL 不走应用代拉。代码块与本地附件不隐式获得 API 资源访问权限
  - 页面保留 DOM 交互，不接入原生 JS 桥；CSP 禁止 Worker、connect/form 与 eval。聊天中的外部图片需点击后加载，不携带 API 凭证；artifact 图片共用受限加载器，防止重定向泄露凭证
  - HTML 文档和单个资源上限 2 MiB；每次预览最多 64 次请求（含重定向）、8 MiB 资源总量、4 个并发加载。源码窗口最多显示 32,000 字符，可下载完整文件。复杂 CSS、表格合并和交互由整页预览承载
- **Sessions**
  - 手机主会话右滑进入整页功能导航，左滑或点击会话按钮进入整页会话列表
  - 会话搜索、切换 session、加载历史（含历史 tool_calls）；左右滑动、关闭按钮或系统返回可回到主会话
- **基础设置（Settings）**
  - API Base / API Key / Session
  - Bearer vs `X-API-Key`
  - Health 探测、WS 重连、加密存储 Key
- **config.json**
  - 从功能导航进入独立的服务器配置页，按字段编辑、校验预览、保存或从文件重载
  - 进入该页面时加载服务器配置；基础设置只管理设备端配置
- **后台黑板**
  - 默认以可拖动、缩放的节点画布展示共享任务黑板、Worker 和任务；归属连线来自后台接口，已完成任务用虚线区分
  - 点击节点查看状态，进入任务详情或执行对话；也可用节点列表选择，保留任务列表视图和筛选
- **Memory**
  - recall 查询、tier stats、真实 wikilink 图谱、动态布局和召回路径回放
- **Trajectory / Gateways / Skills**
  - 只读 JSON 面板（对齐 GUI 入口，便于联调）

## 还缺什么

- 证书 pinning / 正式 release 签名密钥（CI 已支持可选 secrets 签名）
- Trajectory 仍需真机检查长 JSON、复制和筛选交互
- Memory graph 仍需真机检查窄屏缩放、节点拖拽、局部图谱和详情可读性
- Session 时间戳、附件失败重试等本轮 UI 改动仍需真机回归

## UI 对照 GUI

| GUI (`UI/GUI`) | Android |
|----------------|---------|
| 左侧 sidebar 导航 | 整页 `NavigationPage` / 宽屏 `NavigationRail` |
| Chat | `ChatScreen` |
| 后台任务黑板 | `BackgroundScreen` / `BackgroundBlackboardCanvas` |
| Trajectory | `TrajectoryScreen` |
| Gateways | `GatewaysScreen` |
| Skills | `SkillsScreen` |
| Settings | `SettingsScreen` |
| config.json | `RuntimeConfigScreen` |
| Memory graph | `MemoryScreen` |
| 会话列表 + 搜索 | `SessionDrawer` |
| topbar + composer | `ChatTopBar` + `ComposerBar` |
| 四叶草绿主题 | `CloverTheme` |

主题色：bg `#f8f5ee` / side `#f2efe8` / surface `#fffcf7` / text `#2b2925` / accent `#356f42`。

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

普通 PR 不会创建 GitHub Release，只有推送 `v*` tag 才会发布版本。

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

### HTML 渲染回归

UI 测试使用独立包名，避免测试清理卸载日常使用的 debug 应用：

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest -PisolatedUiTest=true
./gradlew :app:assembleDebug
```

第二条命令恢复正常 debug 包名。验收覆盖标题/列表/表格内 HTML、链接点击、代码文字、details 流式状态、CSS/DOM 预览、源码切换保留状态、HTML 附件和深色大字显示。
