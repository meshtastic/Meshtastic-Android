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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import co.touchlab.kermit.Logger

/**
 * Message prefix of Android's Canvas.restore() IllegalStateException; other platforms never throw it. Deliberately also
 * matches the "Underflow in restoreToCount" message variant.
 */
private const val RESTORE_UNDERFLOW_MESSAGE = "Underflow in restore"

/** Reads the native canvas save count. Compose's common Canvas API does not expose it. */
internal expect fun Canvas.platformSaveCount(): Int

/** Pops saves above [count]; a no-op when the stack is already at or below [count]. */
internal expect fun Canvas.platformRestoreToCount(count: Int)

/**
 * Runs [block] with the canvas save stack snapshotted, swallowing only Android's restore-underflow
 * IllegalStateException and rebalancing to the entry depth so a corrupted frame is dropped, not fatal.
 */
internal fun Canvas.withRestoreUnderflowGuard(block: () -> Unit) {
    val saveCount = platformSaveCount()
    try {
        block()
    } catch (e: IllegalStateException) {
        // JVM CancellationException subclasses IllegalStateException; the message filter is what rethrows it.
        if (e.message?.contains(RESTORE_UNDERFLOW_MESSAGE) != true) throw e
        // Error severity so analytics records a Crashlytics non-fatal; downgrade once the upstream fix is verified.
        Logger.e(e) { "Dropped a chart frame: Vico unbalanced the canvas save stack" }
    } finally {
        platformRestoreToCount(saveCount)
    }
}

/**
 * Guards the chart subtree against Vico 3.3.0's canvas restore underflow inside BaseCartesianLayer.draw (Crashlytics
 * issue 7744c73d302c0af1676f31f80802d59f, fatal since 2.8.0). Remove once fixed upstream.
 *
 * After a swallow, Vico's cached offscreen layer canvas may stay corrupted indefinitely (a frozen, blank, or partially
 * clipped chart until the canvas size changes or the screen is recreated); the node-canvas rebalance here cannot reach
 * it. Degraded visuals in that rare case beat the fatal crash.
 */
internal fun Modifier.chartRestoreUnderflowGuard(): Modifier = drawWithContent {
    drawIntoCanvas { canvas -> canvas.withRestoreUnderflowGuard { drawContent() } }
}
