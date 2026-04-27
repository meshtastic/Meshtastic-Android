/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.wear.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import org.meshtastic.wear.presentation.COLOR_BG_DEEP
import org.meshtastic.wear.presentation.COLOR_SURFACE1
import org.meshtastic.wear.presentation.COLOR_SURFACE2
import org.meshtastic.wear.presentation.COLOR_TEAL
import org.meshtastic.wear.presentation.COLOR_TEXT_PRIMARY
import org.meshtastic.wear.presentation.COLOR_TEXT_SECONDARY
import org.meshtastic.wear.presentation.PULSE_DURATION

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.wear.compose.material3.Icon

@Composable
fun MiniPill(text: String, tint: Color, icon: ImageVector? = null) {
    Box(
        modifier =
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(text = text, fontSize = 9.sp, color = tint, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val contentColor = if (selected) COLOR_BG_DEEP else COLOR_TEXT_SECONDARY
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) COLOR_TEAL else COLOR_SURFACE2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = contentColor,
                modifier = Modifier.wrapContentWidth(),
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color, sizeDp: Int) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PULSE_DURATION), RepeatMode.Reverse),
            label = "alpha",
        )
    Canvas(modifier = Modifier.size(sizeDp.dp)) { drawCircle(color = color.copy(alpha = alpha)) }
}

@Composable
fun ConversationRow(title: String, subtitle: String, unread: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE1),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = COLOR_TEXT_PRIMARY)
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = COLOR_TEXT_SECONDARY,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unread > 0) UnreadBadge(unread)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = COLOR_TEAL,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun UnreadBadge(count: Int) {
    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(COLOR_TEAL), contentAlignment = Alignment.Center) {
        Text(text = count.toString(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
    }
}
