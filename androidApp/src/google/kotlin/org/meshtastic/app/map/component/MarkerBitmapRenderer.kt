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
package org.meshtastic.app.map.component

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import org.meshtastic.app.map.MapsSdkInitializer
import org.meshtastic.core.model.Node

private const val CHIP_CORNER_RADIUS_DP = 4f
private const val CHIP_PADDING_HORIZONTAL_DP = 8f
private const val CHIP_MIN_WIDTH_DP = 64f
private const val CHIP_MIN_HEIGHT_DP = 28f
private const val CHIP_TEXT_SIZE_SP = 14f
private const val EMOJI_TEXT_SIZE_SP = 32f
private const val EMOJI_PADDING_DP = 2f

/**
 * Renders a node chip marker as a [BitmapDescriptor] using Canvas — avoids the off-screen ComposeView pipeline in
 * maps-compose's `MarkerComposable`/`rememberComposeBitmapDescriptor` which can crash with "The ComposeView was
 * measured to have a width or height of zero" during subcomposition races (googlemaps/android-maps-compose#875).
 */
@Composable
fun rememberNodeChipDescriptor(node: Node): BitmapDescriptor {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    return remember(node.num, node.user.short_name, node.colors, node.isIgnored) {
        buildNodeChipDescriptor(context, node, density, fontScale)
    }
}

/**
 * Non-`@Composable` variant of [rememberNodeChipDescriptor] for callers that have no composition to read
 * [LocalContext]/[LocalDensity] from — specifically a [com.google.maps.android.clustering.view.DefaultClusterRenderer]
 * building marker icons on its background render thread. Keeping the cluster icon on this Canvas path (instead of
 * maps-compose's `clusterItemContent` Composable) avoids the off-screen ComposeView in `ComposeUiClusterRenderer`,
 * which has no reachable `ViewTreeLifecycleOwner` from the async render Handler and was our top FATAL
 * (googlemaps/android-maps-compose#325/#875).
 */
fun buildNodeChipDescriptor(context: Context, node: Node, density: Float, fontScale: Float): BitmapDescriptor {
    ensureMapsInitialized(context)
    return renderNodeChipBitmap(node, density, fontScale)
}

/** Renders an emoji waypoint marker as a [BitmapDescriptor] using Canvas. */
@Composable
fun rememberEmojiMarkerDescriptor(codePoint: Int): BitmapDescriptor {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    return remember(codePoint) {
        ensureMapsInitialized(context)
        renderEmojiBitmap(codePoint, density, fontScale)
    }
}

/**
 * [BitmapDescriptorFactory] only works after the Maps SDK has been initialized, which normally happens when a
 * GoogleMap/MapView is created. These descriptors are built during composition, and on the node-detail inline map the
 * icon is computed before that screen's GoogleMap has loaded the SDK — so [BitmapDescriptorFactory.fromBitmap] crashes
 * with "IBitmapDescriptorFactory is not initialized". Delegate to [MapsSdkInitializer], which initializes the SDK
 * synchronously and idempotently (and reports the loaded renderer), so this is a no-op once the SDK is already up.
 */
private fun ensureMapsInitialized(context: Context) {
    MapsSdkInitializer.ensureInitialized(context)
}

private fun renderNodeChipBitmap(node: Node, density: Float, fontScale: Float): BitmapDescriptor {
    val (textColorInt, nodeColorInt) = node.colors
    val scaledDensity = density * fontScale

    val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = CHIP_TEXT_SIZE_SP * scaledDensity
            typeface = Typeface.DEFAULT_BOLD
            color = textColorInt
            textAlign = Paint.Align.CENTER
            isStrikeThruText = node.isIgnored
        }
    val label = node.user.short_name.ifEmpty { "???" }

    val textWidth = textPaint.measureText(label)
    val paddingH = CHIP_PADDING_HORIZONTAL_DP * density
    val minWidth = CHIP_MIN_WIDTH_DP * density
    val minHeight = CHIP_MIN_HEIGHT_DP * density

    val width = maxOf(minWidth, textWidth + paddingH * 2).toInt()
    val height = minHeight.toInt()

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = nodeColorInt }
    val cornerRadius = CHIP_CORNER_RADIUS_DP * density
    canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cornerRadius, cornerRadius, bgPaint)

    val textX = width / 2f
    val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, textX, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun renderEmojiBitmap(codePoint: Int, density: Float, fontScale: Float): BitmapDescriptor {
    val scaledDensity = density * fontScale
    val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = EMOJI_TEXT_SIZE_SP * scaledDensity
            textAlign = Paint.Align.CENTER
        }
    val emoji = String(Character.toChars(codePoint))
    val padding = EMOJI_PADDING_DP * density

    val textWidth = textPaint.measureText(emoji)
    val metrics = textPaint.fontMetrics
    val textHeight = metrics.descent - metrics.ascent

    val width = (textWidth + padding * 2).toInt().coerceAtLeast(1)
    val height = (textHeight + padding * 2).toInt().coerceAtLeast(1)

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    val textX = width / 2f
    val textY = padding - metrics.ascent
    canvas.drawText(emoji, textX, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
