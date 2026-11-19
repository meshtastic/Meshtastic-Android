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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.error
import org.meshtastic.core.resources.retry
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons

private const val RETICLE_FRACTION = 0.7f
private const val SCRIM_ALPHA = 0.6f
private val ReticleStroke = 3.dp
private val ReticleCorner = 40.dp

/** A button the scanner offers besides closing, such as reading a QR code from an image instead of the camera. */
class ScannerAction(val label: String, val onClick: () -> Unit)

/**
 * The full-screen scanner every platform shares: the camera behind a reticle, a close button, and an error card in
 * place of the camera when it fails.
 *
 * [camera] draws the camera view. It receives a modifier that places a view exactly inside the reticle's window, for a
 * camera view that covers whatever Compose draws beneath it (a browser's `<video>` element); a camera view Compose can
 * draw over fills the frame and ignores it.
 *
 * @param showReticle whether to draw the reticle, normally once the camera shows a picture.
 * @param errorMessage shown in an error card with Retry and Close; null while the camera works.
 * @param secondaryAction offered at the bottom of the frame and in the error card.
 */
@Composable
fun BarcodeScannerFrame(
    onClose: () -> Unit,
    showReticle: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    secondaryAction: ScannerAction? = null,
    camera: @Composable BoxScope.(windowModifier: Modifier) -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val window = min(maxWidth, maxHeight) * RETICLE_FRACTION - ReticleStroke
            camera(Modifier.align(Alignment.Center).size(window))
            if (showReticle) {
                ScannerReticule()
            }
            if (errorMessage != null) {
                ScannerErrorContent(
                    message = errorMessage,
                    onRetry = onRetry,
                    onClose = onClose,
                    secondaryAction = secondaryAction,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (secondaryAction != null) {
                FilledTonalButton(
                    onClick = secondaryAction.onClick,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(24.dp),
                ) {
                    Text(text = secondaryAction.label)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(
                    imageVector = MeshtasticIcons.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ScannerErrorContent(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    secondaryAction: ScannerAction?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.padding(24.dp), shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.error),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose) { Text(text = stringResource(Res.string.close)) }
                secondaryAction?.let { TextButton(onClick = it.onClick) { Text(text = it.label) } }
                FilledTonalButton(onClick = onRetry) { Text(text = stringResource(Res.string.retry)) }
            }
        }
    }
}

@Composable
private fun ScannerReticule() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val reticleSize = width.coerceAtMost(height) * RETICLE_FRACTION
        val left = (width - reticleSize) / 2
        val top = (height - reticleSize) / 2
        val rect = Rect(left, top, left + reticleSize, top + reticleSize)

        // Darken everything outside the window.
        clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = SCRIM_ALPHA))
        }

        val cornerLength = ReticleCorner.toPx()
        val corners =
            Path().apply {
                // Top left
                moveTo(left, top + cornerLength)
                lineTo(left, top)
                lineTo(left + cornerLength, top)

                // Top right
                moveTo(left + reticleSize - cornerLength, top)
                lineTo(left + reticleSize, top)
                lineTo(left + reticleSize, top + cornerLength)

                // Bottom right
                moveTo(left + reticleSize, top + reticleSize - cornerLength)
                lineTo(left + reticleSize, top + reticleSize)
                lineTo(left + reticleSize - cornerLength, top + reticleSize)

                // Bottom left
                moveTo(left + cornerLength, top + reticleSize)
                lineTo(left, top + reticleSize)
                lineTo(left, top + reticleSize - cornerLength)
            }

        drawPath(corners, Color.White, style = Stroke(ReticleStroke.toPx()))
    }
}
