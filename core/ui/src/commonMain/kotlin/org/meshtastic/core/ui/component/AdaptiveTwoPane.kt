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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

/**
 * A two-slot adaptive layout: [first] and [second] are stacked in a single column on compact/medium windows and shown
 * side-by-side once the window is large enough to warrant a second pane.
 *
 * The split decision is delegated to [calculatePaneScaffoldDirective] rather than a hardcoded width breakpoint. The
 * directive only grants a second horizontal partition at the **expanded** width class (>= 840dp) by default — matching
 * the Material adaptive guidance that side-by-side panes are an expanded-width pattern, not a medium (600dp) one — and
 * it is hinge / multi-window aware. This is the same primitive the app's navigation-based scaffolds
 * ([MeshtasticNavDisplay]) use, so both surfaces flip to two panes at the same breakpoint.
 *
 * When split, the panes are hosted in a [SupportingPaneScaffold] so the divider is a draggable [VerticalDragHandle],
 * giving parity with the list-detail / supporting-pane scenes elsewhere in the app. Both slots keep their [ColumnScope]
 * receiver, so callers are unchanged.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveTwoPane(
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())

    if (directive.maxHorizontalPartitions > 1) {
        // Expanded: canonical supporting-pane layout with a draggable divider. Both panes are forced visible because
        // we only reach this branch when the directive allows two partitions.
        SupportingPaneScaffold(
            modifier = modifier,
            directive = directive,
            value =
            ThreePaneScaffoldValue(
                primary = PaneAdaptedValue.Expanded,
                secondary = PaneAdaptedValue.Expanded,
                tertiary = PaneAdaptedValue.Hidden,
            ),
            mainPane = { AnimatedPane { Column { first() } } },
            supportingPane = { AnimatedPane { Column { second() } } },
            paneExpansionState = rememberPaneExpansionState(),
            paneExpansionDragHandle = { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    modifier =
                    Modifier.paneExpansionDraggable(
                        state = state,
                        minTouchTargetSize = 48.dp,
                        interactionSource = interactionSource,
                    ),
                    interactionSource = interactionSource,
                )
            },
        )
    } else {
        // Compact / medium: keep both slots stacked in a single column (the supporting content must stay visible on
        // phones — this is not a navigable list-detail flow).
        Column(modifier = modifier) {
            first()
            second()
        }
    }
}

/** Screenshot-test sample; public so `:screenshot-tests` can render it at compact and expanded widths. */
@Suppress("MagicNumber")
@Composable
fun AdaptiveTwoPaneSample() {
    AppTheme {
        Surface {
            AdaptiveTwoPane(
                first = {
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(text = "Primary pane", modifier = Modifier.padding(24.dp))
                    }
                },
                second = {
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text(text = "Supporting pane", modifier = Modifier.padding(24.dp))
                    }
                },
            )
        }
    }
}
