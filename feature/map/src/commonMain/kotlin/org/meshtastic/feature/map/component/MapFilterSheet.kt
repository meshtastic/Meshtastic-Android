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
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_filter_all_roles
import org.meshtastic.core.resources.map_filter_display_title
import org.meshtastic.core.resources.map_filter_nodes_title
import org.meshtastic.core.resources.map_filter_roles_title
import org.meshtastic.core.resources.map_filter_show_ignored
import org.meshtastic.core.resources.map_filter_title
import org.meshtastic.core.resources.node_filter_exclude_mqtt
import org.meshtastic.core.resources.node_filter_include_unknown
import org.meshtastic.core.resources.node_filter_only_direct
import org.meshtastic.core.resources.node_filter_only_online
import org.meshtastic.core.resources.only_favorites
import org.meshtastic.core.resources.show_precision_circle
import org.meshtastic.core.resources.show_waypoints
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.Lens
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.core.ui.icon.role
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.proto.Config

/** The tag the chip for one role carries, so a test can pick out a single role. */
fun roleFilterChipTestTag(role: Config.DeviceConfig.Role): String = "role-filter-${role.name}"

/**
 * The main map's filters.
 *
 * A sheet rather than the dropdown this replaces: eleven controls and a role chip for every device role is more than a
 * menu should hold, and the layers button on the same screen already opens a sheet.
 *
 * State in, actions out, exactly as the dropdown was — neither engine keeps its own copy, and the Google flavour mounts
 * this same composable.
 */
@Composable
fun MapFilterSheet(
    onDismissRequest: () -> Unit,
    filterState: BaseMapViewModel.MapFilterState,
    actions: MapFilterActions,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        MapFilterSheetContent(filterState = filterState, actions = actions)
    }
}

/** The sheet's body, separate from the sheet itself so it can be tested without a window manager to host a modal. */
@Composable
internal fun MapFilterSheetContent(filterState: BaseMapViewModel.MapFilterState, actions: MapFilterActions) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(Res.string.map_filter_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SectionTitle(stringResource(Res.string.map_filter_display_title))
        FilterToggle(
            label = stringResource(Res.string.only_favorites),
            icon = MeshtasticIcons.Favorite,
            checked = filterState.onlyFavorites,
            onToggle = actions.onToggleOnlyFavorites,
        )
        FilterToggle(
            label = stringResource(Res.string.show_waypoints),
            icon = MeshtasticIcons.PinDrop,
            checked = filterState.showWaypoints,
            onToggle = actions.onToggleShowWaypoints,
        )
        FilterToggle(
            label = stringResource(Res.string.show_precision_circle),
            icon = MeshtasticIcons.Lens,
            checked = filterState.showPrecisionCircle,
            onToggle = actions.onToggleShowPrecisionCircle,
        )
        LastHeardSlider(selected = filterState.lastHeardFilter, onSelect = actions.onSelectLastHeard)

        HorizontalDivider()

        SectionTitle(stringResource(Res.string.map_filter_roles_title))
        RoleFilterChips(
            excluded = filterState.excludedRoles,
            onToggle = actions.onToggleRoleExcluded,
            onClear = actions.onClearExcludedRoles,
        )

        HorizontalDivider()

        SectionTitle(stringResource(Res.string.map_filter_nodes_title))
        NodeFilterToggles(filterState = filterState, actions = actions)
    }
}

/**
 * The node-level filters, in the node list's own words — four of these five labels are its string resources, so a user
 * who has met them there does not have to learn them twice.
 */
@Composable
private fun NodeFilterToggles(filterState: BaseMapViewModel.MapFilterState, actions: MapFilterActions) = Column {
    FilterToggle(
        label = stringResource(Res.string.node_filter_only_online),
        checked = filterState.onlyOnline,
        onToggle = actions.onToggleOnlyOnline,
    )
    FilterToggle(
        label = stringResource(Res.string.node_filter_only_direct),
        checked = filterState.onlyDirect,
        onToggle = actions.onToggleOnlyDirect,
    )
    FilterToggle(
        label = stringResource(Res.string.node_filter_exclude_mqtt),
        checked = filterState.excludeMqtt,
        onToggle = actions.onToggleExcludeMqtt,
    )
    // The fifth is ours: the list's `node_filter_show_ignored` reads "Only show ignored Nodes", which is what the
    // list does and the opposite of what this does.
    FilterToggle(
        label = stringResource(Res.string.map_filter_show_ignored),
        checked = filterState.showIgnored,
        onToggle = actions.onToggleShowIgnored,
    )
    FilterToggle(
        label = stringResource(Res.string.node_filter_include_unknown),
        checked = filterState.includeUnknown,
        onToggle = actions.onToggleIncludeUnknown,
    )
}

/**
 * One chip per device role, plus an "All" chip that clears the lot.
 *
 * Selected means shown, which is the way round a user reads a chip row — the state behind it is the complement, a set
 * of *excluded* roles, so that a role introduced by later firmware appears instead of silently vanishing.
 */
@Composable
private fun RoleFilterChips(
    excluded: Set<Config.DeviceConfig.Role>,
    onToggle: (Config.DeviceConfig.Role) -> Unit,
    onClear: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = excluded.isEmpty(),
            onClick = onClear,
            label = { Text(stringResource(Res.string.map_filter_all_roles)) },
        )
        // Deprecated slots are still values on the wire, so a node can report one and has to be filterable.
        @Suppress("DEPRECATION")
        Config.DeviceConfig.Role.entries.forEach { role ->
            FilterChip(
                selected = role !in excluded,
                onClick = { onToggle(role) },
                label = { Text(role.name) },
                leadingIcon = { Icon(imageVector = MeshtasticIcons.role(role), contentDescription = null) },
                modifier = Modifier.testTag(roleFilterChipTestTag(role)),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun FilterToggle(label: String, checked: Boolean, onToggle: () -> Unit, icon: ImageVector? = null) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
    )
}
