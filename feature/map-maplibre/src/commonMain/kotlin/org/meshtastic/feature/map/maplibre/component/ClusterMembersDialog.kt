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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.nodes_at_this_location
import org.meshtastic.feature.map.maplibre.geojson.ClusterMember

/**
 * Lists the nodes of a cluster that cannot be zoomed apart, because they share a position.
 *
 * Renders nothing when [members] is empty, so callers can hand it state directly without a guard.
 *
 * The equivalent of the Google flavor's cluster dialog; the entries come straight off the cluster's leaf features, so
 * this needs nothing from the node database.
 */
@Composable
internal fun ClusterMembersDialog(
    members: List<ClusterMember>,
    onMemberClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (members.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = { Text(text = stringResource(Res.string.nodes_at_this_location)) },
        text = {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(items = members, key = { it.nodeNum }) { member ->
                    ClusterMemberRow(member = member, onClick = { onMemberClick(member.nodeNum) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(text = stringResource(Res.string.close)) } },
    )
}

@Composable
private fun ClusterMemberRow(member: ClusterMember, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(
            text = member.longName.ifBlank { member.shortName },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (member.shortName.isNotBlank() && member.longName.isNotBlank()) {
            Text(
                text = member.shortName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
