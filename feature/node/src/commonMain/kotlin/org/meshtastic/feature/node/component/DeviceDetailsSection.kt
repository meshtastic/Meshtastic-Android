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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_unverified
import org.meshtastic.core.resources.img_hw_unknown
import org.meshtastic.core.resources.supported
import org.meshtastic.core.resources.supported_by_community
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Verified
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

/**
 * Device "hero" section showing the hardware image, device name, and support status. Used as the top section of the
 * combined node identity card.
 */
@Composable
internal fun DeviceHeroSection(
    bgColor: Long,
    deviceHardware: DeviceHardware,
    reportedTarget: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceAvatar(bgColor, deviceHardware, size = 80)

        Spacer(Modifier.width(16.dp))

        Column {
            val deviceText = reportedTarget?.let { "${deviceHardware.displayName} ($it)" } ?: deviceHardware.displayName
            Text(
                text = deviceText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            SupportStatusBadge(deviceHardware.activelySupported)
        }
    }
}

@Composable
private fun DeviceAvatar(bgColor: Long, deviceHardware: DeviceHardware, size: Int = 64) {
    Box(
        modifier =
        Modifier.size(size.dp)
            .clip(CircleShape)
            .background(color = Color(bgColor).copy(alpha = .5f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DeviceHardwareImage(deviceHardware, Modifier.fillMaxSize())
    }
}

@Composable
private fun SupportStatusBadge(isSupported: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(
            imageVector =
            if (isSupported) {
                MeshtasticIcons.Verified
            } else {
                org.jetbrains.compose.resources.vectorResource(Res.drawable.ic_unverified)
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isSupported) colorScheme.StatusGreen else colorScheme.StatusRed,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text =
            if (isSupported) {
                stringResource(Res.string.supported)
            } else {
                stringResource(Res.string.supported_by_community)
            },
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    val fallbackPainter = org.jetbrains.compose.resources.painterResource(Res.drawable.img_hw_unknown)
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current).data(imageUrl).build(),
        contentScale = ContentScale.Inside,
        contentDescription = deviceHardware.displayName,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        fallback = fallbackPainter,
        modifier = modifier.padding(16.dp),
    )
}
