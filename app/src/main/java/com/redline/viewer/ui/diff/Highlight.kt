package com.redline.viewer.ui.diff

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.redline.viewer.ui.theme.TokenColors

private enum class TokKind { Comment, Str, Number, Keyword, Type, Fn, Ident, Punct, Ws, Other }

private data class Pattern(val regex: Regex, val kind: TokKind)

private val PATTERNS = listOf(
    Pattern(Regex("""//[^\n]*"""), TokKind.Comment),
    Pattern(Regex(""""(?:\\.|[^"\\])*""""), TokKind.Str),
    Pattern(Regex("""'(?:\\.|[^'\\])*'"""), TokKind.Str),
    Pattern(Regex("""`(?:\\.|[^`\\])*`"""), TokKind.Str),
    Pattern(Regex("""\b\d+(?:\.\d+)?\b"""), TokKind.Number),
    Pattern(Regex("""\b(?:const|let|var|function|return|if|else|for|while|export|import|from|default|class|extends|new|this|null|undefined|true|false|interface|type|enum|public|private|protected|readonly|async|await|try|catch|throw|switch|case|break|continue|in|of|typeof|instanceof|as)\b"""), TokKind.Keyword),
    Pattern(Regex("""\b(?:string|number|boolean|void|any|never|unknown|object|Array|Map|Set|Promise|Record|HTMLElement|ReadonlyArray|Mutable|ReadonlyReducerState|NavigateAction|ReducerState|ScrollPosition|ScrollRestorer)\b"""), TokKind.Type),
    Pattern(Regex("""[A-Za-z_$][A-Za-z0-9_$]*(?=\()"""), TokKind.Fn),
    Pattern(Regex("""[A-Za-z_$][A-Za-z0-9_$]*"""), TokKind.Ident),
    Pattern(Regex("""[{}()\[\];,.:?=+\-*/<>!&|%^~]+"""), TokKind.Punct),
    Pattern(Regex("""\s+"""), TokKind.Ws),
)

private fun colorFor(kind: TokKind) = when (kind) {
    TokKind.Comment -> TokenColors.Comment
    TokKind.Str -> TokenColors.Str
    TokKind.Number -> TokenColors.Number
    TokKind.Keyword -> TokenColors.Keyword
    TokKind.Type -> TokenColors.Type
    TokKind.Fn -> TokenColors.Fn
    TokKind.Ident -> TokenColors.Ident
    TokKind.Punct -> TokenColors.Punct
    else -> TokenColors.Ident
}

fun highlight(line: String): AnnotatedString = buildAnnotatedString {
    if (line.isEmpty()) return@buildAnnotatedString
    var i = 0
    while (i < line.length) {
        var matched = false
        for (p in PATTERNS) {
            val m = p.regex.matchAt(line, i)
            if (m != null) {
                withStyle(SpanStyle(color = colorFor(p.kind))) { append(m.value) }
                i += m.value.length
                matched = true
                break
            }
        }
        if (!matched) {
            append(line[i])
            i++
        }
    }
}
