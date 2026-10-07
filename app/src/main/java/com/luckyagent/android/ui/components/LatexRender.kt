package com.luckyagent.android.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * Turns a small LaTeX subset into readable text. Display-size formulas keep
 * larger fractions and limits; inline formulas stay on the line.
 */
internal fun renderLatex(source: String): AnnotatedString {
    val cleaned = source
        .replace("\\displaystyle", "")
        .replace("\\textstyle", "")
        .replace("\\limits", "")
        .trim()
    return buildAnnotatedString { appendLatex(cleaned) }
}

private fun AnnotatedString.Builder.appendLatex(source: String) {
    var i = 0
    while (i < source.length) {
        when (source[i]) {
            '\\' -> {
                val command = readCommand(source, i + 1)
                i = command.next
                when (command.name) {
                    "frac", "dfrac", "tfrac" -> {
                        val num = readGroup(source, i)
                        val den = readGroup(source, num.next)
                        i = den.next
                        appendLatex(num.body)
                        append("/")
                        appendLatex(den.body)
                    }
                    "sqrt" -> {
                        val body = readGroup(source, i)
                        i = body.next
                        append("√")
                        if (body.body.length > 1) append("(")
                        appendLatex(body.body)
                        if (body.body.length > 1) append(")")
                    }
                    "sum" -> append("∑")
                    "prod" -> append("∏")
                    "int" -> append("∫")
                    "infty" -> append("∞")
                    "alpha" -> append("α")
                    "beta" -> append("β")
                    "gamma" -> append("γ")
                    "delta" -> append("δ")
                    "epsilon" -> append("ε")
                    "theta" -> append("θ")
                    "lambda" -> append("λ")
                    "mu" -> append("μ")
                    "pi" -> append("π")
                    "sigma" -> append("σ")
                    "phi" -> append("φ")
                    "omega" -> append("ω")
                    "Gamma" -> append("Γ")
                    "Delta" -> append("Δ")
                    "Sigma" -> append("Σ")
                    "Omega" -> append("Ω")
                    "cdot" -> append("·")
                    "times" -> append("×")
                    "div" -> append("÷")
                    "pm" -> append("±")
                    "leq", "le" -> append("≤")
                    "geq", "ge" -> append("≥")
                    "neq" -> append("≠")
                    "approx" -> append("≈")
                    "to", "rightarrow" -> append("→")
                    "leftarrow" -> append("←")
                    "ldots", "dots" -> append("…")
                    "quad" -> append("  ")
                    "qquad" -> append("    ")
                    ",", ";", ":", "!", " " -> Unit
                    "left", "right" -> Unit
                    "text", "mathrm", "mathbf" -> {
                        val body = readGroup(source, i)
                        i = body.next
                        if (command.name == "mathbf") {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(body.body) }
                        } else {
                            append(body.body)
                        }
                    }
                    else -> append(command.name)
                }
            }
            '^' -> {
                val script = readScript(source, i + 1)
                i = script.next
                withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em)) {
                    appendLatex(script.body)
                }
            }
            '_' -> {
                val script = readScript(source, i + 1)
                i = script.next
                withStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em)) {
                    appendLatex(script.body)
                }
            }
            '{', '}' -> i++
            else -> {
                append(source[i])
                i++
            }
        }
    }
}

private data class CommandRead(val name: String, val next: Int)
private data class GroupRead(val body: String, val next: Int)

private fun readCommand(source: String, start: Int): CommandRead {
    if (start >= source.length) return CommandRead("", start)
    val first = source[start]
    if (!first.isLetter()) return CommandRead(first.toString(), start + 1)
    var end = start + 1
    while (end < source.length && source[end].isLetter()) end++
    return CommandRead(source.substring(start, end), end)
}

private fun readGroup(source: String, start: Int): GroupRead {
    var i = start
    while (i < source.length && source[i].isWhitespace()) i++
    if (i >= source.length || source[i] != '{') return GroupRead("", i)
    var depth = 0
    val body = StringBuilder()
    while (i < source.length) {
        when (source[i]) {
            '{' -> {
                if (depth > 0) body.append('{')
                depth++
            }
            '}' -> {
                depth--
                if (depth == 0) return GroupRead(body.toString(), i + 1)
                body.append('}')
            }
            else -> body.append(source[i])
        }
        i++
    }
    return GroupRead(body.toString(), i)
}

private fun readScript(source: String, start: Int): GroupRead {
    var i = start
    while (i < source.length && source[i].isWhitespace()) i++
    if (i >= source.length) return GroupRead("", i)
    if (source[i] == '{') return readGroup(source, i)
    return GroupRead(source[i].toString(), i + 1)
}
