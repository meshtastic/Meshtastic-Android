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
package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.wifi_provision_sending_credentials
import org.meshtastic.core.resources.wifi_provision_status_applied
import org.meshtastic.core.resources.wifi_provision_status_failed
import org.meshtastic.core.ui.icon.Error
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Success
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus

/** Inline status card matching the web flasher's colored status feedback. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProvisionStatusCard(provisionStatus: ProvisionStatus, isProvisioning: Boolean) {
    val colors = statusCardColors(provisionStatus, isProvisioning)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.first),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusIcon(provisionStatus = provisionStatus, isProvisioning = isProvisioning, tint = colors.second)
            Text(
                text = statusText(provisionStatus, isProvisioning),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                color = colors.second,
            )
        }
    }
}

/** Resolve container + content color pair for the provision status card. */
@Composable
private fun statusCardColors(provisionStatus: ProvisionStatus, isProvisioning: Boolean): Pair<Color, Color> = when {
    isProvisioning -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

    provisionStatus == ProvisionStatus.Success ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

    provisionStatus == ProvisionStatus.Failed ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusIcon(provisionStatus: ProvisionStatus, isProvisioning: Boolean, tint: Color) {
    when {
        isProvisioning -> LoadingIndicator(modifier = Modifier.size(20.dp), color = tint)

        provisionStatus == ProvisionStatus.Success ->
            Icon(MeshtasticIcons.Success, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)

        provisionStatus == ProvisionStatus.Failed ->
            Icon(MeshtasticIcons.Error, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun statusText(provisionStatus: ProvisionStatus, isProvisioning: Boolean): String = when {
    isProvisioning -> stringResource(Res.string.wifi_provision_sending_credentials)
    provisionStatus == ProvisionStatus.Success -> stringResource(Res.string.wifi_provision_status_applied)
    provisionStatus == ProvisionStatus.Failed -> stringResource(Res.string.wifi_provision_status_failed)
    else -> ""
}
