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
package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.ChannelOption
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.util.BeaconJoinOption
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.mesh_beacon_offer_add
import org.meshtastic.core.resources.mesh_beacon_offer_channel
import org.meshtastic.core.resources.mesh_beacon_offer_discover
import org.meshtastic.core.resources.mesh_beacon_offer_dismiss
import org.meshtastic.core.resources.mesh_beacon_offer_from_unknown
import org.meshtastic.core.resources.mesh_beacon_offer_join
import org.meshtastic.core.resources.mesh_beacon_offer_preset
import org.meshtastic.core.resources.mesh_beacon_offer_region
import org.meshtastic.core.resources.mesh_beacon_offer_signal
import org.meshtastic.core.resources.mesh_beacon_offer_title
import org.meshtastic.proto.Config.LoRaConfig.RegionCode

/**
 * A single received Mesh Beacon invitation. Presents the advertised channel/region/preset and lets the user survey the
 * mesh first ([onDiscover], shown only when a preset is offered), join it ([onJoin]), or dismiss the invitation.
 */
@Suppress("LongMethod")
@Composable
internal fun MeshBeaconInvitationCard(
    offer: MeshBeaconOffer,
    joinOption: BeaconJoinOption,
    onJoin: () -> Unit,
    onDiscover: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve to a ChannelOption so the preset shows a localized label and "Discover" only appears when the offered
    // preset is one we can actually seed a scan with (matches DiscoveryViewModel.discoverOffer's success condition).
    val presetOption = ChannelOption.from(offer.beacon.offer_preset)
    val region = offer.beacon.offer_region
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(Res.string.mesh_beacon_offer_title),
                style = MaterialTheme.typography.titleMedium,
            )

            val body = offer.message.ifBlank { stringResource(Res.string.mesh_beacon_offer_from_unknown) }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)

            offer.channelName?.let {
                Text(
                    text = stringResource(Res.string.mesh_beacon_offer_channel, it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (region != null && region != RegionCode.UNSET) {
                Text(
                    text = stringResource(Res.string.mesh_beacon_offer_region, region.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            presetOption?.let {
                Text(
                    text = stringResource(Res.string.mesh_beacon_offer_preset, it.displayName()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (offer.rssi != 0 || offer.snr != 0f) {
                Text(
                    text =
                    stringResource(
                        Res.string.mesh_beacon_offer_signal,
                        MetricFormatter.snr(offer.snr),
                        MetricFormatter.rssi(offer.rssi),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.mesh_beacon_offer_dismiss)) }
                Spacer(modifier = Modifier.weight(1f))
                if (presetOption != null) {
                    OutlinedButton(onClick = onDiscover) { Text(stringResource(Res.string.mesh_beacon_offer_discover)) }
                }
                // "Add channel" joins with no reboot (same frequency slot); otherwise "Join" retunes + reboots.
                val joinLabel =
                    if (joinOption == BeaconJoinOption.ADD) {
                        Res.string.mesh_beacon_offer_add
                    } else {
                        Res.string.mesh_beacon_offer_join
                    }
                Button(onClick = onJoin) { Text(stringResource(joinLabel)) }
            }
        }
    }
}
