package com.luckyagent.android.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import ru.noties.jlatexmath.JLatexMathDrawable

@Composable
internal fun LatexView(
    latex: String,
    display: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
) {
    val argb = color.toArgb()
    val textSize = if (display && !inline) 52f else if (display) 46f else 40f
    val drawable = remember(latex, display, argb, textSize) {
        runCatching {
            JLatexMathDrawable.builder(latex.ifBlank { " " })
                .textSize(textSize)
                .color(argb)
                .background(AndroidColor.TRANSPARENT)
                .align(if (inline) JLatexMathDrawable.ALIGN_LEFT else JLatexMathDrawable.ALIGN_CENTER)
                .padding(if (inline) 2 else 8)
                .build()
        }.getOrNull()
    }
    if (drawable == null) {
        androidx.compose.material3.Text(latex, color = color, modifier = modifier)
        return
    }
    val scroll = if (inline) modifier else modifier.horizontalScroll(rememberScrollState())
    Box(scroll.heightIn(min = if (inline) 22.dp else 36.dp)) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    adjustViewBounds = true
                    setImageDrawable(drawable)
                }
            },
            update = { view -> view.setImageDrawable(drawable) },
        )
    }
}
