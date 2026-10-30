# SVG 预览

状态：已用用户提供的 Kruskal 静态 SVG 在 Android 模拟器上验证实际绘制；本轮补上了首屏挂载和静态图脚本开销的收敛，仍需在真实设备上做最终帧率验收。

## 做法

完整的静态 SVG 走独立预览路径（仍使用 WebView），不丢掉混排 HTML 中的其它内容。

- 消息卡片按 `viewBox` 或声明的宽高比布局；未声明尺寸的图保留在 HTML 路径。
- 图比一屏的 72% 更高时，在卡片里滚动；滚动时图保持卡片宽度，不会左右被裁。
- 右上角打开全屏：图铺满宽度、可以竖向滚动、可以缩放看细节。消息里的源码页显示完整文本。
  HTML 附件的源码页仍只显示前 32,000 字符，完整文件走下载。
- LazyColumn 的预取行先保持冷态，首个布局稳定后只给真实可见行挂载 WebView；卡片离开组合时销毁 WebView。
  历史里滑出屏幕的图不再常驻，也不会在打开历史时为整个预取窗口同时启动渲染进程。
- 静态图有 `viewBox` 或声明宽高时不启用 JavaScript、不注入尺寸桥、不执行 `getBBox`；只有旧的无尺寸路径才保留一次性尺寸探针。
- 卡片高度变了（量到尺寸、旋转）会重新加载对应的页面，不会停在第一次的布局上。
- 图填满卡片时，手指拖动交给消息列表；图在卡片里滚动时，先滚图，到顶或到底再交给列表。
- 原生 WebView 明确使用 `MATCH_PARENT`，浏览器视口与卡片尺寸一致；SVG 布局不扫描整页 DOM。
- 预览页使用标准 HTML 模式、视口高度和未转义的 CSS 数据，避免 SVG 高度被算成 0。
- 尺寸只读根节点的 `getBBox`，不再扫描全部节点。
- 源码解析在 `MarkdownParseCache` 中后台完成并缓存；普通重组和滚动不重新解析。
- 历史消息内容或布局模式变化时更新现有 WebView；内容未变时不重复加载。

## 只处理"就是一张图"的来源

下面这些仍然走原来的 HTML 预览和资源策略：

- 带 `script`、`foreignObject`、`src` 或外部 `href` 的 SVG。同文档的 `#id` 引用算图片内部，仍然按静态图处理。
- 带 `on*` 内联事件属性的 SVG。事件属性也是脚本。
- 图旁边还有文字或别的标签的来源。只留图会丢内容。
- 超过 `INLINE_HTML_ACTIVE_LIMIT`（48,000 字符）的来源，沿用原有的内联页上限。

页面级 `<style>`（`<head>` 里的样式）会带进预览页，导出工具把类名写在头部的图不会变成默认填充。

## 不做

- 不改图片附件的 480dp 上限。
- 不改 `details` 折叠。
- 不改服务端，不改桌面 GUI。

## 验收

`SvgPreviewTest` 覆盖框、卡片高度、滚动、脚本回退、事件属性回退、同文档引用、混排回退、
体积上限、完整源码包装、头部样式保留、CSS 原文和残缺 HTML。

`SvgPreviewInstrumentedTest` 使用 `androidTest/assets/svg/kruskal-static.svg` 原样复现：

- 裸 SVG 和 `html` 围栏在聊天卡片中画出蓝边、灰边和四个节点；
- 全屏、源码切换、返回卡片后仍可见；
- 同一消息位置从空 SVG 更新为该图，不保留旧页面。
- 静态图页面不包含尺寸桥脚本；首屏只挂载可见行，滚出窗口后不保留 WebView。

测试同时检查 DOM 和设备截图中的实际蓝/灰像素，保存截图及布局诊断，避免“节点存在但图为空”误判通过。
- `multiSvgHistoryWritesFrameIntervalEvidence` 在六张 SVG 历史中滚动，使用 `Choreographer` 记录帧间隔，并在测试输出目录写入 `svg-frame-metrics.json`（p50/p95/max）。它只采集证据，不把模拟器或设备的帧率伪装成固定通过标准。
使用独立测试包，不清理用户日常使用的 debug 应用：

```sh
./gradlew :app:connectedDebugAndroidTest -PisolatedUiTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luckyagent.android.ui.components.SvgPreviewInstrumentedTest \
  --offline
```

在真机上验收时，先确认 `adb devices` 状态为 `device`，再保存测试输出中的 `svg-frame-metrics.json`；至少记录复杂图首次显示和六图历史连续上滑的 p95/max 帧间隔，并注明设备型号、Android 版本和刷新率。

只收到截到 `@k` 的动画 HTML（缺少后续图形和闭合标签）时，不能凭空还原图形；
动画和滚动流畅度仍需完整输入及真实设备性能验证。最终验收应记录复杂图、多图历史的首帧和滚动帧耗时，
不能只用 DOM、截图、单测或构建通过代替帧率证据。
