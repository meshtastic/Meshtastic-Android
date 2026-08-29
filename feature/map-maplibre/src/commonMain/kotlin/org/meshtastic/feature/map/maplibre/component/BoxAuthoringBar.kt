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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.geofence_box_author_hint
import org.meshtastic.core.resources.geofence_box_use_view

/**
 * The bar shown while a waypoint's geofence box is being drawn on the map.
 *
 * Says what to do — tap two opposite corners — and offers the two ways out the Google flavor offers: cancel, which
 * returns to the editor with the box unchanged, and "use this view", which commits whatever the map is currently
 * showing. That second button matters for more than convenience: it is the only path to a box that needs no precise
 * taps, so the feature stays reachable with a keyboard or a screen reader.
 */
@Composable
internal fun BoxAuthoringBar(onCancel: () -> Unit, onUseVisibleRegion: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = ROW_PADDING_H.dp, vertical = ROW_PADDING_V.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.geofence_box_author_hint),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(GAP.dp))
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onUseVisibleRegion) { Text(stringResource(Res.string.geofence_box_use_view)) }
        }
    }
}

private const val ROW_PADDING_H = 16
private const val ROW_PADDING_V = 8
private const val GAP = 8
