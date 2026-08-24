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
package org.meshtastic.feature.node.metrics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Covers the save-stack guard around chart drawing (Crashlytics 7744c73d302c0af1676f31f80802d59f). */
class ChartDrawGuardTest {

    private fun newCanvas(): Canvas = Canvas(ImageBitmap(4, 4))

    @Test
    fun guardRebalancesLeakedSavesToEntryDepth() {
        val canvas = newCanvas()
        val entryDepth = canvas.platformSaveCount()
        canvas.withRestoreUnderflowGuard {
            canvas.save()
            canvas.saveLayer(Rect(0f, 0f, 4f, 4f), Paint())
        }
        assertEquals(entryDepth, canvas.platformSaveCount())
    }

    @Test
    fun guardSwallowsRestoreUnderflowException() {
        val canvas = newCanvas()
        val entryDepth = canvas.platformSaveCount()
        // Message thrown by android.graphics.Canvas.restore, the crash this guard exists for.
        canvas.withRestoreUnderflowGuard {
            throw IllegalStateException("Underflow in restore - more restores than saves")
        }
        assertEquals(entryDepth, canvas.platformSaveCount())
    }

    @Test
    fun guardRethrowsUnrelatedIllegalStateException() {
        val canvas = newCanvas()
        assertFailsWith<IllegalStateException> { canvas.withRestoreUnderflowGuard { error("unrelated") } }
    }

    @Test
    fun guardRethrowsOtherExceptionTypes() {
        val canvas = newCanvas()
        assertFailsWith<IllegalArgumentException> {
            canvas.withRestoreUnderflowGuard { throw IllegalArgumentException("boom") }
        }
    }

    @Test
    fun guardToleratesStackDrivenBelowSnapshot() {
        val canvas = newCanvas()
        val base = canvas.platformSaveCount()
        canvas.save()
        canvas.withRestoreUnderflowGuard {
            // Skia silently ignores restores at the stack floor, so this drives the count below the snapshot.
            canvas.restore()
            canvas.restore()
        }
        // restoreToCount cannot re-raise a popped stack; it must tolerate the deficit without throwing.
        assertEquals(base, canvas.platformSaveCount())
    }

    @Test
    fun guardIsNoOpForBalancedDrawing() {
        val canvas = newCanvas()
        val entryDepth = canvas.platformSaveCount()
        canvas.withRestoreUnderflowGuard {
            canvas.save()
            canvas.restore()
        }
        assertEquals(entryDepth, canvas.platformSaveCount())
    }
}
