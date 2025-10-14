/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.InsetDivider
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.feature.node.model.MetricsState

@Composable
fun DeviceDetailsSection(state: MetricsState, modifier: Modifier = Modifier) {
    val node = state.node ?: return
    val deviceHardware = state.deviceHardware ?: return
    val hwModelName = deviceHardware.displayName
    val isSupported = deviceHardware.activelySupported
    TitledCard(stringResource(R.string.device), modifier = modifier) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier =
            Modifier.align(Alignment.CenterHorizontally)
                .size(100.dp)
                .clip(CircleShape)
                .background(color = Color(node.colors.second).copy(alpha = .5f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DeviceHardwareImage(deviceHardware, Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(16.dp))

        InsetDivider()

        ListItem(
            text = stringResource(R.string.hardware),
            leadingIcon = Icons.Default.Router,
            supportingText = hwModelName,
            copyable = true,
            trailingIcon = null,
        )

        InsetDivider()

        ListItem(
            text =
            if (isSupported) {
                stringResource(R.string.supported)
            } else {
                stringResource(R.string.supported_by_community)
            },
            leadingIcon =
            if (isSupported) {
                Icons.TwoTone.Verified
            } else {
                ImageVector.vectorResource(org.meshtastic.feature.node.R.drawable.unverified)
            },
            leadingIconTint = if (isSupported) colorScheme.StatusGreen else colorScheme.StatusRed,
            trailingIcon = null,
        )
    }
}

@Composable
private fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).build(),
        contentScale = ContentScale.Inside,
        contentDescription = deviceHardware.displayName,
        placeholder = painterResource(org.meshtastic.feature.node.R.drawable.hw_unknown),
        error = painterResource(org.meshtastic.feature.node.R.drawable.hw_unknown),
        fallback = painterResource(org.meshtastic.feature.node.R.drawable.hw_unknown),
        modifier = modifier.padding(16.dp),
    )
}
