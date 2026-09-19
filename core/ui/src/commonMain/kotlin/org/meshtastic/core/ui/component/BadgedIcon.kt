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

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Badge size as a fraction of the icon's shorter side. Sized so the person glyph keeps its torso at 18dp. */
private const val BADGE_FRACTION = 0.48f

/** Radius of the knockout behind the badge, as a fraction of the badge box. Slightly wider, for a visible gap. */
private const val KNOCKOUT_FRACTION = 0.58f

/**
 * One glyph made of two: [icon] with [badge] set into its lower-trailing corner.
 *
 * Drawn, not laid out, so it measures as a single [Icon]. The base is knocked out behind the badge or the two
 * silhouettes merge at list sizes. The gap is cut rather than filled, so any background shows through.
 */
@Composable
fun BadgedIcon(
    icon: ImageVector,
    badge: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    badgeTint: Color = tint,
) {
    val badgePainter = rememberVectorPainter(badge)
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier =
        modifier.drawWithContent {
            val badgeSize = size.minDimension * BADGE_FRACTION
            val corner = Offset(size.width - badgeSize, size.height - badgeSize)
            val center = Offset(corner.x + badgeSize / 2f, corner.y + badgeSize / 2f)
            val knockout = Path().apply { addOval(Rect(center, badgeSize * KNOCKOUT_FRACTION)) }

            clipPath(knockout, ClipOp.Difference) { this@drawWithContent.drawContent() }
            translate(corner.x, corner.y) {
                with(badgePainter) { draw(Size(badgeSize, badgeSize), colorFilter = ColorFilter.tint(badgeTint)) }
            }
        },
    )
}
