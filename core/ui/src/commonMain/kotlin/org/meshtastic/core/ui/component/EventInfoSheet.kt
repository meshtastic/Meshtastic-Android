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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.event_use_event_fonts
import org.meshtastic.core.ui.icon.CalendarMonth
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.LinkIcon
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Place
import org.meshtastic.core.ui.theme.LocalEventFontsToggle
import org.meshtastic.core.ui.util.EventBrandingIcon
import org.meshtastic.core.ui.util.accentColorOrNull

/**
 * Bottom sheet shown when the user taps the event branding in [MainAppBar]. Surfaces the event metadata the bundled
 * `event_firmware.json` carries — welcome message, location, dates, and links — themed with the edition's accent color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventInfoSheet(edition: EventFirmwareEdition, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val accent = edition.accentColorOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            EventHeader(edition, accent)

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (edition.welcomeMessage.isNotBlank()) {
                    Text(text = edition.welcomeMessage, style = MaterialTheme.typography.bodyLarge)
                }

                val iconTint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
                edition.location?.takeIf { it.isNotBlank() }?.let { InfoRow(MeshtasticIcons.Place, it, iconTint) }
                dateRange(edition)?.let { InfoRow(MeshtasticIcons.CalendarMonth, it, iconTint) }

                val links = edition.links.filter { it.url.isNotBlank() }
                if (links.isNotEmpty()) {
                    HorizontalDivider()
                    links.forEach { link ->
                        LinkRow(label = link.label.ifBlank { link.url }, tint = iconTint) {
                            uriHandler.openUri(link.url)
                        }
                    }
                }

                // Only shown when the event actually ships custom fonts and the platform can load them (Google flavor).
                val fontsToggle = LocalEventFontsToggle.current
                if (fontsToggle.available) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.event_use_event_fonts),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = fontsToggle.enabled, onCheckedChange = fontsToggle.onChange)
                    }
                }
            }
        }
    }
}

/** Accent-colored header band with the event icon + display name; falls back to the theme surface when no accent. */
@Composable
private fun EventHeader(edition: EventFirmwareEdition, accent: Color?) {
    val background = accent ?: MaterialTheme.colorScheme.surfaceVariant
    val foreground = accent?.contentColorFor() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().background(background).padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EventBrandingIcon(
            edition = edition,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentDescription = null,
        )
        Text(
            text = edition.displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = foreground,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkRow(label: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MeshtasticIcons.LinkIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            imageVector = MeshtasticIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dates as displayed: a `start – end` range, a single date, or `null` when neither is set. Raw ISO (no l10n parse). */
private fun dateRange(edition: EventFirmwareEdition): String? {
    val start = edition.eventStart
    val end = edition.eventEnd
    return when {
        start != null && end != null && start != end -> "$start – $end"
        start != null -> start
        else -> end
    }
}

/** Black or white, whichever reads against this background color. */
private fun Color.contentColorFor(): Color = if (luminance() > LUMINANCE_MIDPOINT) Color.Black else Color.White

private const val LUMINANCE_MIDPOINT = 0.5f
