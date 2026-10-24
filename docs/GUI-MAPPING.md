# GUI → Android mapping

Source of truth for UX: `lucky-agent/UI/GUI`.

| GUI | Android |
|-----|---------|
| `App.tsx` shell / sidebar nav | `LuckyAgentAppRoot`。导航只放「这台电脑」（含服务器配置）；任务、轨迹、记忆和后台黑板从当前会话卡进入。这张卡默认隐藏，由输入栏加号菜单打开 |
| Chat view + composer + bubbles | `ChatScreen` |
| Sessions list / search | `SessionDrawerContent` |
| WebSocket `/api/v1/ws` | `LuckyAgentWsClient` |
| `fetchRuntime('/v1/sessions')` | `LuckyAgentApi.listSessions` |
| Settings (server addr, models…) | `SettingsScreen` overview by default; hosts/tokens/session open via **打开详细设置**。连接状态与导航、聊天共用「未连接 / 连接中 / 已连接 / 正在回复」 |
| Task card status | 状态芯片走中文。模式、来源、角色、事件类型仍用英文短标签，不在这次文案范围内 |
| Memory graph | `MemoryScreen`，导航和页标题为「记忆」 |
| Skills / Gateways / Trajectory | 同名 Screen，JSON 只读骨架 |
| clover CSS tokens | `ui/theme/Color.kt` + `res/values/colors.xml` |

Non-goals for v0:

- Embed Go runtime on device
- Public gRPC without TLS/auth
- Shipping provider API keys on phone

## WS event map (runtime → Android)

| Server (`message.go`) | Android handler |
|-----------------------|-----------------|
| stream_chunk | assistant bubble append (also accepts assistant_delta) |
| stream_end | finalize assistant (full_response) |
| tool_call | tool card (name + args/params/display) |
| tool_result | merge into same step card |
| status / reasoning | activity line |
| error | error bubble |
| cancel status | stop streaming |
