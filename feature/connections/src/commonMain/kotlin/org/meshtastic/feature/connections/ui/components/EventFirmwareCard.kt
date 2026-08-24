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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.event_firmware_running
import org.meshtastic.core.ui.component.EventInfoSheet
import org.meshtastic.core.ui.component.EventPaletteStrip
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.EventBrandingIcon
import org.meshtastic.core.ui.util.brandPalette

/** Size of the event icon in the card — matches the node avatar in the connected-device card above it. */
private val EVENT_ICON_SIZE = 40.dp

/** Closing brand rule under the card, thicker than the app-bar hairline since this is the branding's home. */
private val PALETTE_STRIP_HEIGHT = 4.dp

/**
 * Card announcing that the connected device is running an event firmware edition, and the entry point to
 * [EventInfoSheet] for that event's welcome message, venue, dates, and links.
 *
 * This is where event branding lives: the app bar keeps the Meshtastic logo, so the edition is reported alongside the
 * other facts about the connected device rather than replacing the app's own identity.
 *
 * The caller is responsible for hiding this once the event has ended
 * ([hasEnded][org.meshtastic.core.ui.util.hasEnded]); a finished event gets the "return to standard firmware" card
 * instead.
 */
@Composable
fun EventFirmwareCard(edition: EventFirmwareEdition, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showSheet = true }.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Prefers the hosted iconUrl, then a bundled drawable, then the Meshtastic logo — see EventBrandingIcon.
            EventBrandingIcon(
                edition = edition,
                modifier = Modifier.size(EVENT_ICON_SIZE).clip(CircleShape),
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = edition.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.event_firmware_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = MeshtasticIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EventPaletteStrip(palette = edition.brandPalette(), height = PALETTE_STRIP_HEIGHT)
    }
    if (showSheet) {
        EventInfoSheet(edition = edition, onDismiss = { showSheet = false })
    }
}
