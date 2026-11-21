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
import org.meshtastic.sdk.DisplayMirrorFrame

/**
 * Composites complete RGB565 dirty rectangles (LVGL-based device UIs) into a persistent little-endian RGB565 canvas.
 * Every field is validated before anything is allocated, since the geometry is device-controlled.
 */
internal class MirrorRectCompositor {

    var width = 0
        private set

    var height = 0
        private set

    private var canvas: ByteArray? = null

    /** Blits one complete rect; returns true when the canvas changed. */
    fun composite(frame: DisplayMirrorFrame): Boolean {
        if (!isAcceptable(frame)) return false
        val target = canvasFor(frame)
        val rect = frame.data.toByteArray()
        val rowBytes = rectWidth(frame) * 2
        for (row in 0 until rectHeight(frame)) {
            val src = row * rowBytes
            val dst = ((frame.rectY + row) * width + frame.rectX) * 2
            rect.copyInto(target, dst, src, src + rowBytes)
        }
        return true
    }

    private fun canvasFor(frame: DisplayMirrorFrame): ByteArray {
        val held = canvas
        if (held != null && width == frame.width && height == frame.height) return held
        width = frame.width
        height = frame.height
        return ByteArray(frame.width * frame.height * 2).also { canvas = it }
    }

    /** Snapshot of the composited canvas, or null before the first completed rect. */
    fun snapshot(): ByteArray? = canvas?.copyOf()

    fun reset() {
        canvas = null
        width = 0
        height = 0
    }

    /** A rect that omits width or height covers the full panel in that axis. */
    private fun rectWidth(frame: DisplayMirrorFrame) = if (frame.rectWidth > 0) frame.rectWidth else frame.width

    private fun rectHeight(frame: DisplayMirrorFrame) = if (frame.rectHeight > 0) frame.rectHeight else frame.height

    private fun isAcceptable(frame: DisplayMirrorFrame): Boolean {
        val expected = if (withinPanel(frame)) rectWidth(frame) * rectHeight(frame) * 2 else -1
        val acceptable = expected == frame.data.size && expected in 1..MAX_RECT_BYTES
        if (!acceptable) {
            Logger.w {
                "DisplayMirror: dropping bad rect ${frame.rectWidth}x${frame.rectHeight} size=${frame.data.size}"
            }
        }
        return acceptable
    }

    /** Subtraction form throughout: `rectX + rectWidth` overflows for hostile values. */
    private fun withinPanel(frame: DisplayMirrorFrame): Boolean {
        val w = rectWidth(frame)
        val h = rectHeight(frame)
        return frame.width in 1..MAX_PANEL_EDGE &&
            frame.height in 1..MAX_PANEL_EDGE &&
            w in 1..frame.width &&
            h in 1..frame.height &&
            frame.rectX in 0..(frame.width - w) &&
            frame.rectY in 0..(frame.height - h)
    }

    private companion object {
        // Sanity bound for RGB565 panels (largest realistic is 800x480).
        const val MAX_PANEL_EDGE = 1024

        // A single rect can at most cover the largest panel we accept.
        const val MAX_RECT_BYTES = MAX_PANEL_EDGE * MAX_PANEL_EDGE * 2
    }
}
