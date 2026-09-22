package com.luckyagent.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyagent.android.ui.theme.CloverBgSide
import com.luckyagent.android.ui.theme.CloverLine
import com.luckyagent.android.ui.theme.CloverText
import com.luckyagent.android.ui.theme.CloverText2

/**
 * Lightweight Markdown renderer: **bold**, *italic*, `code`, ```fences```,
 * # headings, -/* lists, [links](url). No external dependency.
 */
@Composable
fun MarkdownText(
    markdown: String,
    color: Color = CloverText,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { splitBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> {
                    SelectionContainer {
                        Text(
                            text = block.body,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = CloverText2,
                                lineHeight = 18.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(CloverBgSide, RoundedCornerShape(8.dp))
                                .border(1.dp, CloverLine, RoundedCornerShape(8.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp),
                        )
                    }
                }
                is MdBlock.Heading -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }.copy(color = color, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                is MdBlock.ListItem -> {
                    Text(
                        text = buildAnnotatedString {
                            append("• ")
                            append(inlineMarkdown(block.text))
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(color = color),
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(color = color),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class ListItem(val text: String) : MdBlock()
    data class Code(val body: String) : MdBlock()
}

private fun splitBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        val t = para.toString().trimEnd()
        if (t.isNotBlank()) out += MdBlock.Paragraph(t)
        para.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            flushPara()
            i++
            val code = StringBuilder()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[i])
                i++
            }
            if (i < lines.size) i++ // closing fence
            out += MdBlock.Code(code.toString())
            continue
        }
        val heading = Regex("""^(#{1,3})\s+(.*)$""").matchEntire(line.trimEnd())
        if (heading != null) {
            flushPara()
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            i++
            continue
        }
        val list = Regex("""^\s*[-*]\s+(.*)$""").matchEntire(line)
        if (list != null) {
            flushPara()
            out += MdBlock.ListItem(list.groupValues[1])
            i++
            continue
        }
        if (line.isBlank()) {
            flushPara()
            i++
            continue
        }
        if (para.isNotEmpty()) para.append('\n')
        para.append(line)
        i++
    }
    flushPara()
    return out
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    // patterns: **bold**, *italic*, `code`, [label](url)
    val pattern = Regex(
        """(\*\*[^*]+\*\*)|(\*[^*]+\*)|(`[^`]+`)|(\[[^\]]+\]\([^)]+\))"""
    )
    var last = 0
    for (m in pattern.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val token = m.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x143F8A37),
                    ),
                ) { append(token.removeSurrounding("`")) }
            }
            token.startsWith("*") && token.endsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
            }
            token.startsWith("[") -> {
                val lm = Regex("""\[([^\]]+)\]\(([^)]+)\)""").matchEntire(token)
                if (lm != null) {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF3F8A37),
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) { append(lm.groupValues[1]) }
                } else append(token)
            }
            else -> append(token)
        }
        last = m.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
