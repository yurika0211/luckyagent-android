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
  - 思考栏按本轮 subagent 工具返回的任务 ID 展示协作进度；同轮多个独立 delegate 在思考面板中组合成节点图，点击节点进入真实任务详情，再打开对应子代理对话。支持实时状态刷新及历史回放，不混入同会话其他轮次或全局任务列表
  - 当前会话同时只钉一张返回卡，按进行中优先，再按最近活动时间选刚结束的那件。标题和中文状态在聊天记录上方，进行中可以取消，结束后可以打开结果，也可以关闭。关闭只收起这张卡，任务仍留在任务列表和电脑上。关掉后刷新或重进不会再自动冒出来；从通知点回来仍会打开这张卡。列表还没回来时可以打开详情，关掉这张占位卡不会记住这个 id。换到别的会话会拿掉通知带来的焦点。通知同时带了会话和任务时，只有这张卡属于那个会话才保留焦点。打开结束后的结果会进入关联对话并收起这张卡。没有关联对话时不显示打开结果。卡片只钉根任务，派生出的子任务不替换标题。黑板、权重和轮次仍在详情里。后台队列没有取消接口，卡片上不放取消
  - 工具调用卡片（名称、参数摘要、结果、running/done/failed）
  - 长文本在界面内截断并显示提示：正文最多 32,000 字符，思考/工具输出 16,000 字符，工具参数 8,000 字符；每条消息最多显示 32 个附件。限制只影响客户端展示，服务端原始会话不受影响。
  - 入站帧另有一层保护，和界面展示上限不是同一件事：超过 8,000,000 字符的 WebSocket 帧不解析，记一条截断错误并照常推进事件游标，避免重连反复重放；64,000 字符以内的完整帧才会原样进入调试通道，超限帧不会把半截 JSON 当正常消息。
  - 普通消息、Lucky 收集消息和 Lucky 命令等待 WebSocket 握手成功后发送，单次等待最多 20 秒；超时保留失败提示，由用户重试
  - 连接按 API 地址、WebSocket URL、Key、鉴权方式及会话匹配；旧 socket 回调不会覆盖新连接，重连先发事件恢复请求再发新消息
  - 瞬时断线按 500 ms × 重试次数递增，最多等待 10 秒再尝试，没有总次数上限；401/403 停止自动重连。聊天菜单、设置页的重连按钮，以及失败普通消息/Lucky 消息的重试会重新握手
  - 发送 / 停止（cancel）
  - 输入栏「生成中」只在当前会话的 Agent 回合仍在执行时出现。重连回放的历史状态不会把它重新点亮；错误会清掉它并给出下一步。说明见 [生成中指示](docs/generating-indicator.md)
  - 发出但电脑还没写进会话的消息单独留在出站表里，气泡本身带状态：「等待发送」「正在提交」「电脑已收下」「电脑已收下，等上一轮结束」「电脑正在处理」「未送达」。重启后读出站表，用同一个 `clientMessageId` 重发，服务端已接受的 run 按请求号回放，不会开出第二轮
  - Agent 运行期间普通消息、命令、页面操作、切换会话和修改连接配置不会中断当前任务；只有 Stop 或 `/stop` 会取消
  - 已确认历史全量保存在手机上，不再限制每会话条数或会话数。打开会话先画本地最新一页，后台按需补齐全文（`include=full`）；元数据没变就直接读本地，只有尾部增长时只插入新行，电脑压缩或删除才整段替换
- **HTML 展示**
  - 聊天正文、标题、列表和表格共用结构化富文本渲染；支持嵌套样式、换行、字符实体、下划线/删除线/高亮、上下标、HTML 列表/表格/图片与 details 折叠
  - 支持语义颜色及常用行内 CSS（颜色、背景、字号、粗斜体、装饰、对齐）；代码与转义标签保持原文
  - HTML 代码块、完整文档和 HTML 附件可进入整页预览，展示 CSS/SVG/DOM 交互；支持源码切换、缩放和保存/下载，切换源码保留页面交互状态
  - 预览通过隔离域名加载当前文档目录内的 CSS、图片、脚本等资源，支持 artifacts 与普通 HTTP(S) 附件；每次重定向重新校验同源及目录范围，越界即阻止请求。鉴权只附加到配置 API 的 `/api/v1/artifacts`，拒绝目录穿越；任意绝对资源 URL 不走应用代拉。代码块与本地附件不隐式获得 API 资源访问权限
  - 页面保留 DOM 交互，不接入原生 JS 桥；CSP 禁止 Worker、connect/form 与 eval。聊天中的外部图片需点击后加载，不携带 API 凭证；artifact 图片共用受限加载器，防止重定向泄露凭证
  - HTML 文档和单个资源上限 2 MiB；每次预览最多 64 次请求（含重定向）、8 MiB 资源总量、4 个并发加载。源码窗口最多显示 32,000 字符，可下载完整文件。复杂 CSS、表格合并和交互由整页预览承载
- **Sessions**
  - 手机主会话右滑进入整页功能导航，左滑或点击会话按钮进入整页会话列表
  - 导航只留「这台电脑」：定时、渠道、插件、技能、服务器配置。任务、本轮轨迹、记忆和后台黑板从聊天里的当前会话卡进入
  - 当前会话卡默认隐藏。左下角加号菜单里有开关，打开后固定在聊天顶部，不跟着消息滚动。卡片上的删除键会再把它藏起来。它打开属于这轮的任务和后台详情，也进入轨迹和记忆
  - 会话搜索、切换 session、加载历史（含历史 tool_calls）；左右滑动、关闭按钮或系统返回可回到主会话
- **基础设置（Settings）**
  - API Base / API Key / Session
  - Bearer vs `X-API-Key`
  - Health 探测、WS 重连、加密存储 Key
  - 连接状态和聊天、导航共用一句中文：未连接、连接中、已连接、正在回复。失败会写明重新连接或打开设置
- **服务器配置**
  - 从导航「这台电脑」进入，和定时、渠道、插件、技能并列，不由聊天承担
  - 按字段编辑、校验预览、保存或从文件重载。保存会影响已连接电脑上的服务，需要重启的项不会自动重启
- **后台黑板**
  - 默认以可拖动、缩放的节点画布展示共享任务黑板、Worker 和任务；归属连线来自后台接口，已完成任务用虚线区分
  - 点击节点查看状态，进入任务详情或执行对话；也可用节点列表选择，保留任务列表视图和筛选
- **Memory**
  - recall 查询、tier stats、真实 wikilink 图谱、动态布局和召回路径回放
- **Plugins**
  - 抽屉与平板导航的“插件”入口位于「这台电脑」
  - 服务端插件列表、搜索/状态筛选、版本及贡献组件详情
  - 从 Git/服务端目录安装、启停、更新和卸载；安装/更新时显式选择执行信任
  - 状态按服务端响应更新，变更需重启服务生效，旧版服务显示升级提示
  - 接口与操作说明见 [插件管理](docs/PLUGIN-MANAGEMENT.md)
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
| Plugins | `PluginsScreen` |
| Settings | `SettingsScreen` |
| 服务器配置 | `RuntimeConfigScreen`，导航「这台电脑」分组 |
| 记忆 | `MemoryScreen` |
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
3. 安装 debug APK 后打开 App。没有地址和密钥时，全屏先要求扫码或手动填写。
4. 扫电脑上 `lh qr` 的码，或填写 `http://<电脑局域网IP>:9090` 和密钥。以 `Bearer ` 开头的密钥按 Bearer 发送，其余按 `X-API-Key`。
5. 连上后会显示「已连接」和电脑名称，然后进入聊天。成功以带密钥的会话列表为准，不把免鉴权的健康检查当成已连接。失败时可以重试、改地址或重新扫码。已经保存过密钥但这次还没验证通过时，也会停在这一步。
6. 回 **Chat** 发一条消息；侧栏可搜 session / 切历史
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
