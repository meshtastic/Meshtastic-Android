/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ui.label

private const val ZERO_WIDTH_JOINER = 0x200D
private const val TEXT_PRESENTATION = 0xFE0E
private const val EMOJI_PRESENTATION = 0xFE0F
private const val FIRST_SKIN_TONE = 0x1F3FB
private const val LAST_SKIN_TONE = 0x1F3FF
private const val FIRST_PICTOGRAPH = 0x1F000
private const val FIRST_SYMBOL = 0x2600
private const val LAST_DINGBAT = 0x27BF
private const val FIRST_ARROW_SYMBOL = 0x2B00
private const val LAST_ARROW_SYMBOL = 0x2BFF
private const val FIRST_PRINTABLE = 0x20
private const val DELETE = 0x7F
private const val LAST_C1_CONTROL = 0x9F
private const val SUPPLEMENTARY_BASE = 0x10000
private const val SURROGATE_SHIFT = 10
private const val SURROGATE_MASK = 0x3FF
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val ELLIPSIS = "…"

/** Smallest size [LabelTypesetter.fit] shrinks to before it cuts the text. */
internal const val MIN_TEXT_SIZE = 9

/** Measures one run of label text in printer dots; sizes are font pixel sizes. */
fun interface LabelTextMeasurer {
    fun width(text: String, size: Int, emoji: Boolean): Float
}

internal fun String.codePointList(): List<Int> {
    val points = ArrayList<Int>(length)
    var index = 0
    while (index < length) {
        val high = this[index]
        val low = getOrNull(index + 1)
        if (high.isHighSurrogate() && low?.isLowSurrogate() == true) {
            points +=
                SUPPLEMENTARY_BASE +
                (high.code - HIGH_SURROGATE_BASE shl SURROGATE_SHIFT) +
                (low.code - LOW_SURROGATE_BASE)
            index += 2
        } else {
            points += high.code
            index++
        }
    }
    return points
}

internal fun List<Int>.codePointsToString(): String = buildString {
    for (point in this@codePointsToString) {
        if (point >= SUPPLEMENTARY_BASE) {
            val offset = point - SUPPLEMENTARY_BASE
            append((HIGH_SURROGATE_BASE + (offset shr SURROGATE_SHIFT)).toChar())
            append((LOW_SURROGATE_BASE + (offset and SURROGATE_MASK)).toChar())
        } else {
            append(point.toChar())
        }
    }
}

/** Labeltastic's test for which characters draw from the emoji font. */
internal fun isLabelEmoji(codePoint: Int): Boolean = codePoint >= FIRST_PICTOGRAPH ||
    codePoint in FIRST_SYMBOL..LAST_DINGBAT ||
    codePoint in FIRST_ARROW_SYMBOL..LAST_ARROW_SYMBOL

/**
 * Drops what prints as stray boxes in one color (joiners, variation selectors, skin tones, control characters) and
 * collapses whitespace.
 */
internal fun cleanLabelText(text: String): String = text
    .codePointList()
    .filterNot { point ->
        point == ZERO_WIDTH_JOINER ||
            point == TEXT_PRESENTATION ||
            point == EMOJI_PRESENTATION ||
            point in FIRST_SKIN_TONE..LAST_SKIN_TONE ||
            point < FIRST_PRINTABLE ||
            point in DELETE..LAST_C1_CONTROL
    }
    .codePointsToString()
    .split(' ', '\t', '\n', '\r', ' ')
    .filter { it.isNotEmpty() }
    .joinToString(" ")

internal data class LabelRun(val emoji: Boolean, val text: String)

/** Splits text into runs that draw from the text font and runs that draw from the emoji font. */
internal fun labelRuns(text: String): List<LabelRun> {
    val runs = mutableListOf<Pair<Boolean, MutableList<Int>>>()
    for (point in text.codePointList()) {
        val emoji = isLabelEmoji(point)
        if (runs.lastOrNull()?.first == emoji) runs.last().second += point else runs += emoji to mutableListOf(point)
    }
    return runs.map { (emoji, points) -> LabelRun(emoji, points.codePointsToString()) }
}

internal data class FittedText(val text: String, val size: Int)

internal data class WrappedText(val lines: List<String>, val size: Int)

/** The text fitting rules labeltastic's layouts are written against. */
internal class LabelTypesetter(private val measurer: LabelTextMeasurer) {

    fun width(text: String, size: Int): Float =
        labelRuns(text).fold(0f) { total, run -> total + measurer.width(run.text, size, run.emoji) }

    /** Shrinks the size until the text fits; at [minSize], cuts it and adds an ellipsis. */
    fun fit(text: String, maxWidth: Int, size: Int, minSize: Int = MIN_TEXT_SIZE): FittedText {
        val fitting = (size downTo minSize).firstOrNull { width(text, it) <= maxWidth }
        return if (fitting != null) {
            FittedText(text, fitting)
        } else {
            var points = text.codePointList()
            while (points.size > 1 && width(points.codePointsToString() + ELLIPSIS, minSize) > maxWidth) {
                points = points.dropLast(1)
            }
            FittedText(points.codePointsToString() + ELLIPSIS, minSize)
        }
    }

    /** Two lines split at a space that each fit, largest size first. Null when no split fits. */
    fun wrapTwo(text: String, maxWidth: Int, start: Int, minSize: Int): WrappedText? {
        val words = text.split(' ').filter { it.isNotEmpty() }
        return (start downTo minSize).firstNotNullOfOrNull { size ->
            (words.size - 1 downTo 1)
                .map { split -> listOf(words.take(split).joinToString(" "), words.drop(split).joinToString(" ")) }
                .firstOrNull { lines -> lines.all { width(it, size) <= maxWidth } }
                ?.let { WrappedText(it, size) }
        }
    }

    /** Greedy word wrap at [size]. Null when it needs more than [maxLines] or a word is wider than [maxWidth]. */
    fun wrapLines(text: String, maxWidth: Int, size: Int, maxLines: Int): List<String>? {
        val lines = mutableListOf<String>()
        var current = ""
        var tooWide = false
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val trial = if (current.isEmpty()) word else "$current $word"
            if (width(trial, size) <= maxWidth) {
                current = trial
            } else {
                tooWide = tooWide || current.isEmpty() || width(word, size) > maxWidth
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.takeIf { !tooWide && it.size in 1..maxLines }
    }
}
