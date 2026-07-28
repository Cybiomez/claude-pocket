package dev.claudepocket.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Лёгкий markdown: заголовки, **жирный**, *курсив*, `код`, ```блоки```, списки.
// Без внешних библиотек — достаточно для чата; при желании заменяется целиком здесь.
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        val segments = splitCodeBlocks(text)
        for (seg in segments) {
            if (seg.isCode) {
                // Контрастный фон + рамка — иначе блок сливается с пузырём
                // ассистента (у него тоже surfaceVariant) и «блока не видно».
                val codeShape = RoundedCornerShape(8.dp)
                val code = seg.text.trimEnd()
                val clipboard = LocalClipboardManager.current
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(codeShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), codeShape),
                ) {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                            .padding(end = 28.dp),   // место под кнопку копирования
                    )
                    // Кнопка быстрого копирования всего блока
                    DisableSelection {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(code)) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy, "Скопировать код",
                                Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            } else {
                for (line in seg.text.split('\n')) {
                    val t = line.trimEnd()
                    if (t.isBlank()) continue
                    when {
                        t.startsWith("### ") -> Text(inline(t.removePrefix("### ")), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        t.startsWith("## ") -> Text(inline(t.removePrefix("## ")), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        t.startsWith("# ") -> Text(inline(t.removePrefix("# ")), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        t.startsWith("- ") || t.startsWith("* ") -> Text(buildAnnotatedString { append("•  "); append(inline(t.drop(2))) }, fontSize = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                        Regex("^\\d+\\. ").containsMatchIn(t) -> Text(inline(t), fontSize = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                        else -> Text(inline(t), fontSize = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

private data class Seg(val text: String, val isCode: Boolean)

private fun splitCodeBlocks(text: String): List<Seg> {
    val out = mutableListOf<Seg>()
    var rest = text
    while (true) {
        val i = rest.indexOf("```")
        if (i < 0) { if (rest.isNotBlank()) out += Seg(rest, false); break }
        if (rest.substring(0, i).isNotBlank()) out += Seg(rest.substring(0, i), false)
        val afterFence = rest.substring(i + 3)
        val nl = afterFence.indexOf('\n')
        val bodyStart = if (nl in 0..30) nl + 1 else 0 // срезаем язык после ```
        val end = afterFence.indexOf("```", bodyStart)
        if (end < 0) { out += Seg(afterFence.substring(bodyStart), true); break }
        out += Seg(afterFence.substring(bodyStart, end), true)
        rest = afterFence.substring(end + 3)
    }
    return out
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > 0) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(s.substring(i + 2, end)); pop()
                    i = end + 2
                } else { append(s[i]); i++ }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > 0) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x22888888)))
                    append(s.substring(i + 1, end)); pop()
                    i = end + 1
                } else { append(s[i]); i++ }
            }
            s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' -> {
                val end = s.indexOf('*', i + 1)
                if (end > 0) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(s.substring(i + 1, end)); pop()
                    i = end + 1
                } else { append(s[i]); i++ }
            }
            else -> { append(s[i]); i++ }
        }
    }
}
