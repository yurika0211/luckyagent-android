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
- `GET /api/v1/sessions`
- `GET /api/v1/sessions/{id}`
- `POST /api/v1/chat`（可选；本客户端默认走 WebSocket）
- `WS  /api/v1/ws?session=...`
- `GET/POST /api/v1/memory` 等

**不走尚未补齐 TLS + 鉴权的 gRPC 对外通道。**

## UI 对照 GUI

布局与导航对齐主仓 `UI/GUI`（OpenAI 风格聊天仪表盘）：

| GUI (`UI/GUI`) | Android |
|----------------|---------|
| 左侧 sidebar 导航 | `AppScaffold` + `NavigationBar` / 抽屉 |
| Chat | `ChatScreen` |
| Trajectory | `TrajectoryScreen` |
| Gateways | `GatewaysScreen` |
| Skills | `SkillsScreen` |
| Settings | `SettingsScreen` |
| Memory graph | `MemoryScreen` |
| 会话列表 + 搜索 | `SessionDrawer` |
| 顶部 topbar + composer | `ChatTopBar` + `ComposerBar` |
| 四叶草绿主题 | `CloverTheme`（同色 token） |

主题色采样自 GUI `styles.css`：

- bg `#f9faf4` / side `#eef1e5` / surface `#ffffff`
- text `#16190f` / muted `#565a4b`
- accent `#3f8a37` / leaf `#9dc74b`

## 鉴权（必须）

手机连非本机 `lh serve` 时，服务端需配置 `server.api_keys`。客户端在请求头带：

```http
X-API-Key: <key>
```

或：

```http
Authorization: Bearer <key>
```

Key 只存在应用加密存储（`EncryptedSharedPreferences` / DataStore），不进日志、不进 query string。

局域网建议：

1. 电脑 `lh serve` 监听 `0.0.0.0:9090`（或具体局域网 IP）
2. 配置 `server.api_keys`
3. 手机与电脑同一 Wi-Fi，填 `http://192.168.x.x:9090` + API Key  
4. 更稳：Tailscale / 隧道，而不是裸公网端口

## 本地开发

本机需要 Android SDK + JDK 17。若尚未安装 Android Studio，可用 Android Studio 打开本目录同步 Gradle。

```bash
# 可选：生成 wrapper（有 Gradle 时）
gradle wrapper

# 编译 debug APK
./gradlew :app:assembleDebug

# 装到设备 / 模拟器
./gradlew :app:installDebug
```

首次打开 App：

1. Settings → API Base（例 `http://192.168.1.8:9090`）
2. Settings → API Key（与 `server.api_keys` 一致）
3. 点「探测连接」→ 进入 Chat

模拟器访问宿主机可用 `http://10.0.2.2:9090`。

## 目录

```text
app/
  src/main/
    AndroidManifest.xml
    java/com/luckyagent/android/
      LuckyAgentApp.kt
      MainActivity.kt
      ui/           # Compose 界面（对照 GUI）
      data/         # API / WS / 设置存储
      ui/theme/     # Clover 色板
```

## 与主仓关系

- 主仓：`yurika0211/lucky-agent`（Go + `UI/GUI`）
- 本仓：仅 Android 客户端
- 协议源：`docs/API.md` + `internal/server`
- Electron 桌面壳在主仓 `UI/desktop`；手机端不复用 Electron

## 状态

脚手架已可编译结构齐全：导航、主题、设置、会话列表、聊天 WS 客户端骨架。  
后续迭代：会话历史分页、Markdown/工具卡片、Memory graph 可视化、附件上传、证书 pinning。
