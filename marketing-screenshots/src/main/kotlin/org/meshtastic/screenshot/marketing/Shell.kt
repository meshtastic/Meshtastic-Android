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
package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.ui.component.AnimatedConnectionsNavIcon
import org.meshtastic.core.ui.navigation.icon

/**
 * The app's navigation shell, from the same [NavigationSuiteScaffold], destinations and icons as
 * `MeshtasticNavigationSuite`, which itself needs the live ViewModels this generator does not have. The scaffold reads
 * the window class: a compact phone gets the bottom bar, anything wider the rail with labels, and the drawer it would
 * promote to at expanded widths is capped to the rail exactly as the app caps it.
 */
@Composable
internal fun AppShell(selected: TopLevelDestination, content: @Composable () -> Unit) {
    val layoutType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2()).let {
            if (it == NavigationSuiteType.NavigationDrawer) NavigationSuiteType.NavigationRail else it
        }
    val showLabels = layoutType == NavigationSuiteType.NavigationRail
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == selected,
                    onClick = {},
                    icon = { NavigationIcon(destination) },
                    label =
                    if (showLabels) {
                        { Text(stringResource(destination.label)) }
                    } else {
                        null
                    },
                )
            }
        },
    ) {
        Row { content() }
    }
}

@Composable
private fun NavigationIcon(destination: TopLevelDestination) {
    if (destination == TopLevelDestination.Connect) {
        AnimatedConnectionsNavIcon(
            connectionState = ConnectionState.Connected,
            deviceType = DeviceType.fromAddress("x00:11:22:33:44:55"),
            meshActivityFlow = emptyFlow(),
        )
    } else {
        Icon(imageVector = vectorResource(destination.icon), contentDescription = stringResource(destination.label))
    }
}

/** Which pane a list-detail screen shows when the window has room for only one. */
internal enum class Pane {
    List,
    Detail,
}

/**
 * The app's list-detail layout: `MeshtasticNavDisplay` hands the nodes and messages routes to a
 * `ListDetailSceneStrategy`, which splits them into a [ListDetailPaneScaffold] with a draggable divider once
 * [calculatePaneScaffoldDirective] grants a second partition - the expanded width class - and shows the one route on
 * top otherwise. This is the same scaffold, directive and drag handle, with the panes given directly.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ListDetail(compactPane: Pane, list: @Composable () -> Unit, detail: @Composable () -> Unit) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    if (directive.maxHorizontalPartitions > 1) {
        ListDetailPaneScaffold(
            directive = directive,
            value =
            ThreePaneScaffoldValue(
                primary = PaneAdaptedValue.Expanded,
                secondary = PaneAdaptedValue.Expanded,
                tertiary = PaneAdaptedValue.Hidden,
            ),
            listPane = { AnimatedPane { list() } },
            detailPane = { AnimatedPane { detail() } },
            paneExpansionState = rememberPaneExpansionState(),
            paneExpansionDragHandle = { state -> PaneExpansionDragHandle(state) },
        )
    } else {
        when (compactPane) {
            Pane.List -> list()
            Pane.Detail -> detail()
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ThreePaneScaffoldScope.PaneExpansionDragHandle(state: PaneExpansionState) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier =
        Modifier.paneExpansionDraggable(
            state = state,
            minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
            interactionSource = interactionSource,
        ),
        interactionSource = interactionSource,
    )
}

/** The conversation list's section header; the app's own is private to its feature module. */
@Composable
internal fun SectionHeader(title: String, count: Int) {
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }
}
