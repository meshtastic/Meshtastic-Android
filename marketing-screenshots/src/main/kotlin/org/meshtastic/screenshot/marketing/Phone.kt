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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.meshtastic.core.ui.theme.AppTheme

/** The phone form factor: the screen every phone shot is rendered at, and the store canvas it is framed into. */
internal object Phone {
    /** 1080x2160 at 2.5x is the app's 432x864 dp, a common tall-phone window. */
    const val SCREEN_WIDTH_PX = 1080
    const val SCREEN_HEIGHT_PX = 2160
    const val SCREEN_DENSITY = 2.5f

    /** 1242x2484 is Play's 2:1 long-side limit; drawn at 3x so the frame layout is written in dp. */
    const val FRAME_WIDTH_PX = 1242
    const val FRAME_HEIGHT_PX = 2484
    const val FRAME_DENSITY = 3f
}

/** Step two of the pipeline for phones: [screen] inside a captioned bezel on the 1242x2484 store canvas. */
internal fun framePhone(screen: ImageBitmap, caption: Caption): ImageBitmap =
    renderScreen(Phone.FRAME_WIDTH_PX, Phone.FRAME_HEIGHT_PX, Phone.FRAME_DENSITY) { StoreFrame(caption, screen) }

private val Background = Color(0xFF1F2937)
private val BezelColor = Color(0xFF0B0F14)
private val StatusBarColor = Color(0xFF111418)

// The bezel's proportions, in reference dp: a 1:2 screen with a status bar above it and an even inset around both.
private const val SCREEN_W = 300f
private const val SCREEN_H = 600f
private const val STATUS_H = 24f
private const val INSET = 7f
private const val BEZEL_ASPECT = (SCREEN_W + 2 * INSET) / (STATUS_H + SCREEN_H + 2 * INSET)
private val BezelCorner = 34.dp

// Tall enough for a two-line title plus a three-line description at the sizes above.
private val CAPTION_BLOCK_HEIGHT = 232.dp

/**
 * A captioned store frame: the headline and body copy across the top in the app's typography, then a phone bezel with a
 * drawn status bar and [screen] scaled inside it, sized so the whole phone fits above the bottom edge.
 */
@Composable
private fun StoreFrame(caption: Caption, screen: ImageBitmap) {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Column(
            modifier = Modifier.fillMaxSize().background(Background).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The caption block has a fixed height whatever the text wraps to, so the bezel below it is the same
            // size at the same place in all five shots; a shorter caption centres within the block.
            Box(modifier = Modifier.fillMaxWidth().height(CAPTION_BLOCK_HEIGHT), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = caption.title,
                        style =
                        MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 44.sp,
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = caption.description,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp),
                        color = Color.White.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PhoneBezel(screen)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhoneBezel(screen: ImageBitmap) {
    Column(
        modifier =
        Modifier.fillMaxHeight()
            .aspectRatio(BEZEL_ASPECT, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(BezelCorner))
            .background(BezelColor)
            .padding(INSET.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(BezelCorner - INSET.dp))) {
            StatusBar(modifier = Modifier.fillMaxWidth().weight(STATUS_H))
            Image(
                bitmap = screen,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().weight(SCREEN_H),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/** A fixed 9:41 status bar with signal, Wi-Fi and a full battery, drawn rather than captured so it never varies. */
@Composable
private fun StatusBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.background(StatusBarColor).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "9:41",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SignalGlyph()
            WifiGlyph()
            BatteryGlyph()
        }
    }
}

@Composable
private fun SignalGlyph() {
    Canvas(modifier = Modifier.size(width = 14.dp, height = 12.dp)) {
        val path =
            Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                close()
            }
        drawPath(path, Color.White)
    }
}

@Composable
private fun WifiGlyph() {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        for (i in 0 until 3) {
            val radius = size.width * (0.5f - i * 0.16f)
            drawArc(
                color = Color.White,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width / 2 - radius, size.height - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
        drawCircle(Color.White, radius = 1.5.dp.toPx(), center = Offset(size.width / 2, size.height - 1.dp.toPx()))
    }
}

@Composable
private fun BatteryGlyph() {
    Canvas(modifier = Modifier.size(width = 24.dp, height = 12.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        drawRoundRect(Color.White, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        drawRoundRect(
            Color.White,
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = Size(body.width - 4.dp.toPx(), body.height - 4.dp.toPx()),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRoundRect(
            Color.White,
            topLeft = Offset(body.width + 1.dp.toPx(), size.height * 0.3f),
            size = Size(2.dp.toPx(), size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}
