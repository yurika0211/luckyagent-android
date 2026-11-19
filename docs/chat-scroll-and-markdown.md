# 聊天跟底、搜索入口和完整 Markdown

对应 issue：#212、#213、#214。

## 滚动行为

打开会话默认显示最新消息。`ScrollAnchor.followLatest` 默认 `true`，旧版没有这个字段的锚点也按最新消息打开；用户主动上翻后保存 `false`，重新打开按消息 key 和偏移恢复历史位置。退出聊天时立即保存，避免快速切换会话丢掉历史浏览状态。

`FollowLatestEffect` 观察实际列表布局，而非仅观察消息数或正文长度。绘制行数包括日期、未读分隔、可选审批错误和固定 `chat-tail`。因此同一条回复增高、WebView 完成测量、键盘改变视口后，都能继续露出真正的底部。真实拖动暂停跟随，惯性停止且已经到底时恢复；点击链接不会切换跟随模式。搜索期间暂停跟随，关闭搜索后恢复原先的位置和模式。

浮动日期的滚动订阅放在单独的组合函数中。日期映射、绘制行和消息映射缓存；重内容只订阅气泡是否可见，不再直接订阅每次布局变化。UI 回归检查跨行和跨日期滚动不会重组父聊天。这证明消除了该重组来源，不代表真机帧率测量。

## 搜索和 Markdown

搜索入口在右上角三点菜单中；输入、上一条、下一条和关闭保留。

搜索直接投影聊天实际绘制的行：普通消息和折叠的工具/思考过程都使用真实行 key，重复消息 ID 不会串行，外显的思考正文不会再在折叠行重复计数。上一条、下一条和高亮共用同一个 key；搜索加载更早历史后仍保留选中行，关闭时按原行 key 恢复位置。

正文和链接读取完整历史内容，展示仍保留 32,000 字符上限。工具参数、工具输出同样参与搜索；命中摘要显示关键词附近的上下文。匹配在后台执行，仅对输入关键词延迟 150 毫秒，持续流式更新不会重置这段等待。

有命中仍继续顺序读取更早历史，直到扫完。失败、服务端仍返回截断正文或分页起点没有前进时停止，保留已找到的结果并显示重试。退出搜索、改词或切换会话会取消扫描；已经在途的共享历史请求仍由正常历史流程处理，旧搜索不会再派下一页或覆盖新搜索状态。旧的截断缓存会重新请求全文。

整条气泡正文一次传给 `MarkdownText`，空行由 Markdown 解析器处理。代码围栏、Mermaid、HTML 和公式不会被气泡动画拆开。气泡整体高度动画保留；Mermaid 原有 3 秒超时和失败回源码逻辑不变。

## 验证

```bash
./gradlew :app:testDebugUnitTest --offline
./gradlew :app:connectedDebugAndroidTest --offline -PisolatedUiTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luckyagent.android.ui.screens.SearchHistoryInstrumentedTest,com.luckyagent.android.ui.screens.ChatSearchNavigationInstrumentedTest,com.luckyagent.android.ui.screens.FollowLatestInstrumentedTest,com.luckyagent.android.ui.screens.ChatSearchMenuInstrumentedTest,com.luckyagent.android.ui.screens.ChatFloatingDateInstrumentedTest,com.luckyagent.android.ui.screens.MarkdownFenceInstrumentedTest
./gradlew :app:assembleDebug --offline
```

UI 测试使用独立包，不清理日常 debug 应用。WebView 验证先推进 Compose 创建原生视图，再用 Android 时钟等待 JavaScript；避免持续推进 Compose 虚拟帧阻塞 WebView 的等待。

## 验证边界

本轮全量单测 545 条通过；定向 UI 22 条通过、1 条跳过。纯流式回复超出展示前缀、且终态未携带完整正文时，超出部分须等历史全文同步后才能搜索。

API 37 模拟器上，Mermaid 真图已实际显示完整的 Start → Middle → Done 节点与连线（围栏体含空行），并保留截图 `docs/images/mermaid-blank-lines-api37.png`。但该自动 UI 用例在 Compose/WebView idle 同步时阻塞；三次调整等待后停止继续排障，显式 `@Ignore`，不把跳过算作通过。围栏解析、普通围栏、HTML、公式及另外三类聊天 UI 回归继续运行。真机滑动帧率和 Mermaid 全屏缩放仍需手工验收。

![API 37 模拟器中含空行的 Mermaid 围栏实际成图](images/mermaid-blank-lines-api37.png)
