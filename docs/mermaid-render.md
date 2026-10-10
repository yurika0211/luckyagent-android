# Mermaid 渲染

状态：已落地。未在设备上验过图。

## 做法

` ```mermaid ` 走 `MermaidView`。其余围栏仍是等宽源码。

- `MdBlock.Code` 带 `lang`。未闭合的围栏留在 `MdBlock.Code`，不画图。
- 闭合且语言是 `mermaid` 时变成 `MdBlock.Mermaid`。
- `MermaidView` 用 `WebView` 加载 `file:///android_asset/mermaid/index.html`。
- `mermaid.min.js` 版本见 `assets/mermaid/VERSION`（11.17.2），不走网络。
- 非 `file:///android_asset/mermaid/` 的请求被 `shouldInterceptRequest` 丢掉。
- 源码经 `JSONObject` 传给 `renderMermaid`，不拼进 HTML。
- 页面 `onPageFinished` 之后才渲染。成功回传高度，上限 480dp。
- 渲染失败，或 3 秒没有回调，撤掉 WebView，显示源码。

## 不做

- 不做节点点击。缩放用 WebView 默认行为。
- 不认 ` ``` ` 以外的 mermaid。
- 不改服务端，不改桌面 GUI。
- 不给普通代码块加语法高亮。

## 验收

单元测试覆盖了围栏语言、闭合的 mermaid、未闭合时仍是源码。

还没在设备上确认：图能画出来、写坏时回退源码、飞行模式仍能画、离开页面再回来不崩。
