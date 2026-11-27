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

import kotlin.math.roundToInt

private const val DOTS_PER_MM = 8

/** Rows held back from a die-cut label's length so the raster never overruns the gap. */
private const val DIE_SLACK_DOTS = 4

/** Ink keeps this far from both ends of the label: die-cut labels end where the gap sensor says, give or take. */
internal const val LABEL_END_DOTS = 3

/** Text keeps this far from the top and bottom of 12 mm stock. The QR does not: it needs the whole head. */
internal const val LABEL_EDGE_DOTS = 2

/** Below this the text column is narrow (30 mm labels) and drops the band. */
private const val NARROW_COLUMN_DOTS = 180
private const val BAND_DOTS = 22
private const val BAND_TEXT_SIZE = 21
private const val MARK_DOTS = 18

/** White kept on each side of the QR's ink, which labeltastic leaves when it crops the code to its width. */
private const val QR_QUIET_DOTS = 2
private const val QR_GAP_DOTS = 6

/** The bare Ms of the M-PWRD mark in meshtastic/design's M-PWRD_BW_Border.svg, in that file's units. */
internal const val MARK_UNITS_WIDE = 1.76724f
internal const val MARK_UNITS_TALL = 0.91943f

/** The feed length of a die-cut label [millimeters] long, as labeltastic's `with_label_length` sizes it. */
fun labelLengthDots(millimeters: Int): Int = millimeters * DOTS_PER_MM - DIE_SLACK_DOTS

/** One drawing step of a label, in printer dots with the origin at the top left of the reading orientation. */
sealed interface LabelOp {
    /** A QR code with its top left module at ([x], [y]). Not held to the text margins. */
    data class Qr(val modules: QrModules, val x: Int, val y: Int, val dotsPerModule: Int) : LabelOp

    /** A black band with white text as tall as it allows, squeezed sideways rather than shrunk when too wide. */
    data class Band(val x: Int, val y: Int, val width: Int, val height: Int, val text: String, val size: Int) : LabelOp

    /** Text starting at [x] with its vertical middle at [yCenter]. */
    data class Text(val text: String, val x: Int, val yCenter: Int, val size: Int) : LabelOp

    /** The M-PWRD mark's Ms, [height] dots tall, with its right and bottom edges at the given dots. */
    data class Mark(val right: Int, val bottom: Int, val height: Int) : LabelOp
}

/** A label in reading orientation: [width] dots along the feed, [height] across the head. */
data class LabelLayout(val width: Int, val height: Int, val ops: List<LabelOp>)

/** What a name tag shows for a node. */
data class NameTagContent(val shortName: String, val longName: String, val nodeId: String)

/** What a channel label says beside its QR. */
data class ChannelLabelContent(val title: String, val radio: String, val action: String)

/** The parts every label shares: the QR on the left, the band over a wide text column, and the mark. */
private class LabelBuilder(val length: Int, qr: FittedQr, bandText: String, val type: LabelTypesetter) {
    val ops = mutableListOf<LabelOp>()
    val markWidth = (MARK_UNITS_WIDE / MARK_UNITS_TALL * MARK_DOTS).roundToInt()
    val x: Int
    val width: Int
    val narrow: Boolean
    val top: Int

    /** Vertical center of the bottom row: the node ID, or what scanning a channel label does. */
    val bottomRowY = LABEL_HEAD_DOTS - LABEL_EDGE_DOTS - BOTTOM_ROW_HALF

    init {
        val side = qr.modules.size * qr.dotsPerModule
        ops += LabelOp.Qr(qr.modules, LABEL_END_DOTS + QR_QUIET_DOTS, (LABEL_HEAD_DOTS - side) / 2, qr.dotsPerModule)
        x = LABEL_END_DOTS + side + 2 * QR_QUIET_DOTS + QR_GAP_DOTS
        width = length - x - LABEL_END_DOTS
        narrow = width < NARROW_COLUMN_DOTS
        top = if (narrow) LABEL_EDGE_DOTS else LABEL_EDGE_DOTS + BAND_DOTS
        if (!narrow) ops += LabelOp.Band(x, LABEL_EDGE_DOTS, width, BAND_DOTS, bandText, BAND_TEXT_SIZE)
        ops += LabelOp.Mark(length - LABEL_END_DOTS, LABEL_HEAD_DOTS - LABEL_EDGE_DOTS, MARK_DOTS)
    }

    fun text(text: String, yCenter: Int, size: Int, x: Int = this.x) {
        ops += LabelOp.Text(text, x, yCenter, size)
    }

    fun text(fitted: FittedText, yCenter: Int) = text(fitted.text, yCenter, fitted.size)

    fun layout() = LabelLayout(length, LABEL_HEAD_DOTS, ops)

    companion object {
        const val BOTTOM_ROW_HALF = 9
    }
}

/** A line's largest text size and its center under the top of the text column. */
private class Line(val size: Int, val y: Int)

/**
 * The name lines of a narrow (30 mm) or a wide column: the short name over the long one, the same when the long name
 * wraps to two lines, and the sizes a long name starts from when it stands alone.
 */
private class NameSizes(
    val big: Line,
    val long: Line,
    val wrappedBig: Line,
    val wrapped: Line,
    val alone: Int,
    val aloneWrap: Int,
)

private val NARROW_SIZES =
    NameSizes(
        big = Line(size = 38, y = 19),
        long = Line(size = 20, y = 50),
        wrappedBig = Line(size = 32, y = 16),
        wrapped = Line(size = 16, y = 40),
        alone = 30,
        aloneWrap = 24,
    )
private val WIDE_SIZES =
    NameSizes(
        big = Line(size = 32, y = 15),
        long = Line(size = 18, y = 40),
        wrappedBig = Line(size = 24, y = 11),
        wrapped = Line(size = 13, y = 27),
        alone = 26,
        aloneWrap = 18,
    )

/** Below this size a long name under a short one wraps to two lines instead. */
private const val LONG_WRAP_BELOW = 12
private const val LONG_WRAP_MIN = 11

/** The same for a long name that stands alone, beside an emoji short name. */
private const val ALONE_WRAP_BELOW = 15
private const val ALONE_WRAP_MIN = 13
private const val NODE_ID_SIZE = 16
private const val LINE_GAP = 2

/** Height of the bottom row on a 30 mm label that carries the node ID, the emoji and the mark. */
private const val FLAIR_ROW_DOTS = 22
private const val FLAIR_GAP = 4
private const val FLAIR_MIN_SIZE = 26
private const val FLAIR_COLUMN_SHARE = 4
private const val FLAIR_TEXT_GAP = 6
private const val NARROW_NAME_MAX = 34
private const val NARROW_NAME_MIN = 12
private const val NARROW_NAME_FALLBACK = 18
private const val NARROW_NAME_LINES = 3
private const val MARK_GAP = 6

/**
 * Labeltastic's name tag (`render_compact`): the QR, a band over the names from 40 mm up, the node ID and the mark on
 * the bottom row. An emoji-only short name sits on the right like a pin and the long name becomes the big line.
 */
internal fun nameTagLayout(
    content: NameTagContent,
    qr: FittedQr,
    lengthDots: Int,
    bandText: String,
    measurer: LabelTextMeasurer,
): LabelLayout {
    val label = LabelBuilder(lengthDots, qr, bandText, LabelTypesetter(measurer))
    val short = cleanLabelText(content.shortName)
    val long = cleanLabelText(content.longName).ifEmpty { "?" }
    val emojiOnly = short.isNotEmpty() && short.codePointList().all { isLabelEmoji(it) || it == ' '.code }
    val flair = if (emojiOnly) short else ""
    // A short name with nothing printable falls back to the end of the node ID.
    val letters = if (emojiOnly) "" else short.ifEmpty { content.nodeId.takeLast(FALLBACK_SHORT_LENGTH).uppercase() }
    if (flair.isNotEmpty() && label.narrow) {
        label.narrowFlairTag(flair, long, content.nodeId)
    } else {
        val textWidth = if (flair.isEmpty()) label.width else label.width - label.flair(flair) - FLAIR_TEXT_GAP
        if (letters.isEmpty()) label.longNameAlone(long, textWidth) else label.names(letters, long, textWidth)
        // The node ID is what people copy down, so it stays readable.
        val id = label.type.fit(content.nodeId, label.width - label.markWidth - MARK_GAP, NODE_ID_SIZE)
        label.text(id, label.bottomRowY)
    }
    return label.layout()
}

private const val FALLBACK_SHORT_LENGTH = 4

/** 30 mm is too narrow to share: the long name takes the column, and the emoji sits between the ID and the mark. */
private fun LabelBuilder.narrowFlairTag(flair: String, long: String, nodeId: String) {
    val rowY = LABEL_HEAD_DOTS - LABEL_EDGE_DOTS - FLAIR_ROW_DOTS / 2
    // A dot under the row's height and a dot up: emoji reach below the baseline further than text does.
    val flairSize = FLAIR_ROW_DOTS - 2
    val flairX = length - LABEL_END_DOTS - markWidth - FLAIR_GAP - type.width(flair, flairSize).toInt()
    text(flair, rowY - 1, flairSize, x = flairX)
    text(type.fit(nodeId, flairX - x - FLAIR_GAP, NODE_ID_SIZE), rowY)

    val available = LABEL_HEAD_DOTS - LABEL_EDGE_DOTS - FLAIR_ROW_DOTS - LINE_GAP - top
    val wrapped =
        (NARROW_NAME_MAX downTo NARROW_NAME_MIN).firstNotNullOfOrNull { size ->
            type
                .wrapLines(long, width, size, NARROW_NAME_LINES)
                ?.takeIf { it.size * (size + LINE_GAP) <= available }
                ?.let { WrappedText(it, size) }
        } ?: type.fit(long, width, NARROW_NAME_FALLBACK).let { WrappedText(listOf(it.text), it.size) }
    val pitch = wrapped.size + LINE_GAP
    val first = top + (available - wrapped.lines.size * pitch) / 2
    wrapped.lines.forEachIndexed { index, line ->
        text(line, first + index * pitch + wrapped.size / 2 + 1, wrapped.size)
    }
}

/** Draws the emoji above the mark, at most a quarter of the column wide, and returns the width it took. */
private fun LabelBuilder.flair(flair: String): Int {
    val markTop = LABEL_HEAD_DOTS - LABEL_EDGE_DOTS - MARK_DOTS - FLAIR_GAP
    var size = markTop - top - 2
    while (size > FLAIR_MIN_SIZE && type.width(flair, size) > maxOf(FLAIR_MIN_SIZE, width / FLAIR_COLUMN_SHARE)) size--
    val flairWidth = type.width(flair, size).toInt()
    text(flair, (top + markTop) / 2, size, x = length - LABEL_END_DOTS - flairWidth)
    return flairWidth
}

private fun LabelBuilder.names(letters: String, long: String, textWidth: Int) {
    val sizes = if (narrow) NARROW_SIZES else WIDE_SIZES
    val fitted = type.fit(long, textWidth, sizes.long.size)
    val wrapped =
        if (fitted.size < LONG_WRAP_BELOW) type.wrapTwo(long, textWidth, sizes.wrapped.size, LONG_WRAP_MIN) else null
    if (wrapped != null) {
        // The short name gives up a little height for the long name's second line.
        text(type.fit(letters, textWidth, sizes.wrappedBig.size), top + sizes.wrappedBig.y)
        text(wrapped.lines[0], top + sizes.wrapped.y, wrapped.size)
        text(wrapped.lines[1], top + sizes.wrapped.y + wrapped.size, wrapped.size)
    } else {
        text(type.fit(letters, textWidth, sizes.big.size), top + sizes.big.y)
        text(fitted, top + sizes.long.y)
    }
}

/** Nothing but emoji in the short name: the long name is the name people read, centered above the ID row. */
private fun LabelBuilder.longNameAlone(long: String, textWidth: Int) {
    val sizes = if (narrow) NARROW_SIZES else WIDE_SIZES
    val fitted = type.fit(long, textWidth, sizes.alone)
    val wrapped =
        if (fitted.size < ALONE_WRAP_BELOW) type.wrapTwo(long, textWidth, sizes.aloneWrap, ALONE_WRAP_MIN) else null
    val middle = (top + bottomRowY - LabelBuilder.BOTTOM_ROW_HALF) / 2
    if (wrapped != null) {
        val first = middle - (wrapped.size + LINE_GAP) / 2
        text(wrapped.lines[0], first, wrapped.size)
        text(wrapped.lines[1], first + wrapped.size + LINE_GAP, wrapped.size)
    } else {
        text(fitted, middle)
    }
}

private const val CHANNEL_TITLE_NARROW = 34
private const val CHANNEL_TITLE_WIDE = 30
private const val CHANNEL_RADIO_NARROW = 18
private const val CHANNEL_RADIO_WIDE = 16
private const val CHANNEL_BLOCK_GAP = 4
private const val CHANNEL_ACTION_SIZE = 14

/**
 * The channel label Meshtastic-Apple prints: the QR, a band from 40 mm up, the channel's name big with region and
 * preset under it, and on the bottom row what scanning does, beside the mark.
 */
internal fun channelLabelLayout(
    content: ChannelLabelContent,
    qr: FittedQr,
    lengthDots: Int,
    bandText: String,
    measurer: LabelTextMeasurer,
): LabelLayout {
    val label = LabelBuilder(lengthDots, qr, bandText, LabelTypesetter(measurer))
    with(label) {
        val title =
            type.fit(cleanLabelText(content.title), width, if (narrow) CHANNEL_TITLE_NARROW else CHANNEL_TITLE_WIDE)
        val radio =
            type.fit(cleanLabelText(content.radio), width, if (narrow) CHANNEL_RADIO_NARROW else CHANNEL_RADIO_WIDE)
        // Name and radio as one block, centered above the bottom row.
        val block = title.size + CHANNEL_BLOCK_GAP + radio.size
        val blockTop = top + (bottomRowY - LabelBuilder.BOTTOM_ROW_HALF - top - block) / 2
        text(title, blockTop + title.size / 2)
        text(radio, blockTop + title.size + CHANNEL_BLOCK_GAP + radio.size / 2)
        text(type.fit(content.action, width - markWidth - MARK_GAP, CHANNEL_ACTION_SIZE), bottomRowY)
    }
    return label.layout()
}
