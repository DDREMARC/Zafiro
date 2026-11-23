package com.dremarc.grokbot.ui.chat.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Block-level elements supported by [MarkdownText]. */
internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    /** [marker] is "•" for bullets or "1." style for ordered items. */
    data class Item(val marker: String, val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
}

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\d+)[.)]\\s+(.*)$")

/**
 * Minimal markdown parser: fenced code, headings, bullet/numbered lists and paragraphs.
 * Inline emphasis is handled by [inlineMarkdown]. Deliberately small, no dependency.
 */
internal fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = source.replace("\r\n", "\n").split('\n')
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trimEnd()
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                blocks += MarkdownBlock.Code(language, code.toString().trimEnd('\n'))
            }
            trimmed.isEmpty() -> flushParagraph()
            HEADING.matches(trimmed) -> {
                flushParagraph()
                val match = HEADING.find(trimmed)!!
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }
            BULLET.matches(trimmed) -> {
                flushParagraph()
                blocks += MarkdownBlock.Item("•", BULLET.find(trimmed)!!.groupValues[1])
            }
            ORDERED.matches(trimmed) -> {
                flushParagraph()
                val match = ORDERED.find(trimmed)!!
                blocks += MarkdownBlock.Item("${match.groupValues[1]}.", match.groupValues[2])
            }
            else -> paragraph.append(line).append('\n')
        }
        i++
    }
    flushParagraph()
    return blocks
}

/** Converts **bold**, *italic* and `code` spans into an [AnnotatedString]. */
internal fun inlineMarkdown(source: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < source.length) {
        if (source.startsWith("**", i)) {
            val end = source.indexOf("**", i + 2)
            if (end > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(source.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }
        if (source[i] == '`') {
            val end = source.indexOf('`', i + 1)
            if (end > i) {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                append(source.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        if (source[i] == '*' && i + 1 < source.length && source[i + 1] != ' ') {
            val end = source.indexOf('*', i + 1)
            if (end > i + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(source.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        append(source[i])
        i++
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, codeBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color,
                )
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, codeBackground),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
                is MarkdownBlock.Item -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = block.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text = inlineMarkdown(block.text, codeBackground),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MarkdownBlock.Code -> CodeBlock(block.language, block.code)
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (language.isNotBlank()) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
