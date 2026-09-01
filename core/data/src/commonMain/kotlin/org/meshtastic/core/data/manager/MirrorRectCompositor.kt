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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.meshtastic.proto.DisplayFrame

private data class RectArea(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Reassembles RGB565 dirty-rect chunks (LVGL-based device UIs) and composites them into a persistent little-endian
 * RGB565 canvas. Rect geometry is latched from the chunk at offset 0 so a later chunk cannot transpose the blit, and
 * every field is validated before anything is allocated — `total_size` is device-controlled.
 */
internal class MirrorRectCompositor {

    var width = 0
        private set

    var height = 0
        private set

    private var canvas: ByteArray? = null
    private var buffer: ByteArray? = null
    private var received = 0
    private var frameId = 0
    private var area: RectArea? = null

    /** Feeds one chunk; returns true when it completed a rect and the canvas changed. */
    fun handle(chunk: DisplayFrame): Boolean {
        val data = chunk.data_.toByteArray()
        if (!isAcceptable(chunk, data.size)) {
            buffer = null
            return false
        }
        if (chunk.offset == 0) {
            buffer = ByteArray(chunk.total_size)
            frameId = chunk.frame_id
            received = 0
            area = RectArea(chunk.rect_x, chunk.rect_y, rectWidth(chunk), rectHeight(chunk))
        }
        return accumulate(chunk, data)
    }

    private fun accumulate(chunk: DisplayFrame, data: ByteArray): Boolean {
        val buf = buffer ?: return false
        data.copyInto(buf, chunk.offset)
        received += data.size
        val complete = received >= buf.size
        if (complete) {
            composite(chunk, buf)
            buffer = null
        }
        return complete
    }

    /** Snapshot of the composited canvas, or null before the first completed rect. */
    fun snapshot(): ByteArray? = canvas?.copyOf()

    fun reset() {
        canvas = null
        buffer = null
        received = 0
        frameId = 0
        width = 0
        height = 0
        area = null
    }

    /** A rect that omits width or height covers the full panel in that axis. */
    private fun rectWidth(chunk: DisplayFrame) = if (chunk.rect_width > 0) chunk.rect_width else chunk.width

    private fun rectHeight(chunk: DisplayFrame) = if (chunk.rect_height > 0) chunk.rect_height else chunk.height

    private fun isAcceptable(chunk: DisplayFrame, dataSize: Int): Boolean {
        val expected = if (withinPanel(chunk)) rectWidth(chunk) * rectHeight(chunk) * 2 else -1
        val sized = expected == chunk.total_size && chunk.total_size in 1..MAX_RECT_BYTES
        // Continuations must match the buffer allocated at offset 0, not merely their own claim.
        val inSequence =
            chunk.offset == 0 ||
                (buffer?.size == chunk.total_size && chunk.frame_id == frameId && chunk.offset == received)
        val acceptable = sized && inSequence && chunk.offset + dataSize <= chunk.total_size
        if (!acceptable) {
            Logger.w {
                "DisplayMirror: dropping bad rect ${chunk.rect_width}x${chunk.rect_height} total=${chunk.total_size}"
            }
        }
        return acceptable
    }

    /** Subtraction form throughout: `rect_x + rect_width` overflows for hostile values. */
    private fun withinPanel(chunk: DisplayFrame): Boolean {
        val w = rectWidth(chunk)
        val h = rectHeight(chunk)
        return chunk.width in 1..MAX_PANEL_EDGE &&
            chunk.height in 1..MAX_PANEL_EDGE &&
            w in 1..chunk.width &&
            h in 1..chunk.height &&
            chunk.rect_x in 0..(chunk.width - w) &&
            chunk.rect_y in 0..(chunk.height - h)
    }

    private fun composite(chunk: DisplayFrame, rect: ByteArray) {
        if (canvas == null || width != chunk.width || height != chunk.height) {
            canvas = ByteArray(chunk.width * chunk.height * 2)
            width = chunk.width
            height = chunk.height
        }
        val target = canvas ?: return
        val blit = area ?: return
        val rowBytes = blit.width * 2
        for (row in 0 until blit.height) {
            val src = row * rowBytes
            val dst = ((blit.y + row) * width + blit.x) * 2
            rect.copyInto(target, dst, src, src + rowBytes)
        }
    }

    private companion object {
        // Sanity bound for RGB565 panels (largest realistic is 800x480).
        const val MAX_PANEL_EDGE = 1024

        // A single rect can at most cover the largest panel we accept.
        const val MAX_RECT_BYTES = MAX_PANEL_EDGE * MAX_PANEL_EDGE * 2
    }
}
