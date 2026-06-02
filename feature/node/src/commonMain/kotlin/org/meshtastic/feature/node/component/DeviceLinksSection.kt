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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.collapsed
import org.meshtastic.core.resources.device_links_i_want_one
import org.meshtastic.core.resources.device_links_open_in_browser
import org.meshtastic.core.resources.expanded
import org.meshtastic.core.ui.icon.ExpandLess
import org.meshtastic.core.ui.icon.ExpandMore
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons

/**
 * Collapsible "I want one" section listing msh.to vendor/variant and marketplace links for the viewed device. Renders
 * nothing when there are no matching links. Ported from the Meshtastic-Apple `DeviceLinksSection`.
 */
@Composable
fun DeviceLinksSection(links: List<DeviceLink>, modifier: Modifier = Modifier) {
    if (links.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(Res.string.device_links_i_want_one)
    val expandStateDescription = stringResource(if (expanded) Res.string.expanded else Res.string.collapsed)

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp).animateContentSize()) {
            Row(
                modifier =
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = expandStateDescription }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Icon(
                    imageVector = if (expanded) MeshtasticIcons.ExpandLess else MeshtasticIcons.ExpandMore,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
            }
            if (expanded) {
                links.forEach { DeviceLinkRow(it) }
            }
        }
    }
}

@Composable
private fun DeviceLinkRow(link: DeviceLink) {
    val uriHandler = LocalUriHandler.current
    // Vendor and product-variant links are emphasized; marketplace links (region-tagged) are quieter.
    val prominent = link.isVendor || link.regions == null
    val openLabel = stringResource(Res.string.device_links_open_in_browser)
    val label = link.description ?: link.shortCode

    Row(
        modifier =
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button) { uriHandler.openUri(link.url) }
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { contentDescription = "$openLabel: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = MeshtasticIcons.Language,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colorScheme.primary,
        )
    }
}
