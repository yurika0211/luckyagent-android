# HTML 渲染

状态：已落地。未在设备上点过。

## 做法

只画允许列表里的标签。其余整段保持原文。

- 允许：`b` `strong` `i` `em` `code` `br` `p` `a` `details` `summary`。
- 行内标签先转成已有的 Markdown 标记，再走原来的上色。
- `a` 只留 `http` `https`。别的只显示文字。
- `details` 用 `rememberSaveable` 记住开合，默认收起。
- 以 `<details>` 或 `<p>` 开头、且闭合的一段，收成 `MdBlock.Html`。
- ` ```html ` 能通过解析时同样走 `MdBlock.Html`。解析失败仍是源码。
- 未闭合、嵌套超过 8 层、或出现列表外的标签：整段原文，不执行。

## 不做

- 不执行脚本，不加载远程资源，不读 `style`。
- 不支持 HTML 的 `table` `img`。表格和图片继续走 Markdown。
- 不改服务端，不改桌面 GUI。

## 验收

`HtmlInlineTest` 9 条、`MarkdownParserTest` 里的围栏和 details 都过了。

还没在设备上点过链接和 details 的开合。
