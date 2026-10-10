# 行内公式：基线对齐

状态：方案。未改渲染代码。

## 现象

句子里的行内公式不坐在文字基线上。`\displaystyle`、`\dfrac` 把积分号和分式拉高之后，公式浮在字行中间。

块级 `$$...$$` 独占一块，没有这个问题。

## 原因

`MarkdownText.kt` 的 `RichMarkdownText` 把一段拆成 `Row(verticalAlignment = CenterVertically)`：

- 文字是 `Text`，字形落在基线上。
- 公式是 `LatexView`，内部是 `ImageView` + `JLatexMathDrawable`。

`CenterVertically` 对齐的是两个控件的外框中线，不是基线。公式越高，中线越偏离文字基线。

`LatexView.kt` 的字号保持不变：

| 场景 | `textSize` |
|---|---|
| 块级 `display && !inline` | 52f |
| 行内且 `displaySize` | 46f |
| 普通行内 | 40f |

`displaySize` 在 `splitInlineMath` 里为真的条件：`$$...$$`，或源码含 `\displaystyle` / `\dfrac`。这个放大是预期行为，不改。

`inline` 目前只改了 `ALIGN_LEFT`、padding=2、`heightIn(min = 22.dp)`。drawable 高度不限，也不提供基线。

## 可用的基线

`JLatexMathDrawable.icon()` 返回 `org.scilab.forge.jlatexmath.TeXIcon`（jlatexmath-android 0.2.0 runtime）：

- `getIconHeight()` / `getIconDepth()`：基线以上、以下
- `getTrueIconHeight()` / `getTrueIconDepth()`：未取整的高度、深度
- `getBaseLine()`：基线比例
- `getBox().getHeight()` / `getDepth()`：公式盒本身，不含 drawable padding

行内 padding 是 2px。基线距图片顶端：

```text
baselineFromTop = paddingTop + iconHeight
imageHeight     = paddingTop + iconHeight + iconDepth + paddingBottom
```

优先用 `getIconHeight()` / `getIconDepth()`，因为它们已经含 insets。实现时用一条已知公式（例如 `x`）对一下 `getBaseLine()` 与 `iconHeight / (iconHeight + iconDepth)`，两者不一致就以 height/depth 为准。

## 改法

只动行内。块级 `LatexBlock` 仍居中、52f、可横向滚动。

1. `LatexView` 增加可选 `onBaseline: (fromTopPx: Int, heightPx: Int) -> Unit`。
   drawable 建好后回调一次。失败回退纯文本时，不回调，行内保持现在的居中。
2. `RichMarkdownText` 的行内 `Row` 改为 `Top`。
   - `Text`：`Modifier.padding(top = max(0, formulaBaseline - textBaseline))`
   - `LatexView`：`Modifier.padding(top = max(0, textBaseline - formulaBaseline))`
   这样两条基线落在同一 y。`textBaseline` 取 `Text` 的 `onTextLayout { it.firstBaseline }`，字体是 `bodyLarge`（16sp / 24sp）。
3. 字号分支不动。46f 继续只在 `displaySize` 时使用。
4. 行内公式宽过屏幕时，整行横向滚动，而不是让公式单独换行。`chunkedByText` 仍按换行拆段。

不把行内公式再降回 40f，也不把 `\displaystyle` 从源码里剥掉。`LatexRender.kt` 那条纯文本降级路径保持原样，它本来就会去掉 `\displaystyle`。

## 不做

- 不改 `splitInlineMath` 的 `displaySize` 判定。
- 不改块级 `$$` 的 52f。
- 不把公式嵌进 `InlineTextContent`。占位宽度要等 drawable 出来才能知道，第一版用 `Row` + padding 更直接。
- 不处理「行内公式跨多行」的折行。超宽走横向滚动。

## 验收

同一段正文里并排看：

```text
当 $x>0$ 时，$\int_{0}^{1}\frac{1}{1+x^{2}}\,dx=\frac{\pi}{4}$。
当 $x>0$ 时，$\displaystyle\int_{0}^{1}\dfrac{1}{1+x^{2}}\,dx=\dfrac{\pi}{4}$。
```

- 普通行内：等号、`x` 与周围汉字/字母的基线一致，公式不被压小。
- `\displaystyle` / `\dfrac`：积分号和分式明显高于文字，等号仍与文字基线对齐，字号仍是 46f。
- 块级 `$$\frac{a}{b}$$`：仍独占一行，52f，外观与现在相同。
- jlatexmath 解析失败：仍回退成源码文本，不留空白。
