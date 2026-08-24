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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 *
 * The scaffold reports its incoming max height as its own size, so a height-unbounded host (a LazyColumn item, a
 * scrollable column) would make it echo Constraints.Infinity and crash; a plain [Row] split is used there instead.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveTwoPane(
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())

    // Wrap the slots in movable content so their internal state survives when the layout flips between the stacked
    // column and the two-pane scaffold (e.g. on resize / fold), and so neither slot is emitted directly from two
    // branches. The ColumnScope is passed through, so the compact branch keeps the shared-column behaviour.
    val firstPane = remember { movableContentOf<ColumnScope>(first) }
    val secondPane = remember { movableContentOf<ColumnScope>(second) }

    // Hoisted above the height branch so a dragged divider survives the host flipping between bounded and
    // unbounded constraints (the scaffold branch below leaves composition on that flip).
    val paneExpansionState = rememberPaneExpansionState()

    if (directive.maxHorizontalPartitions > 1) {
        // Expanded: split side-by-side. Only a bounded host may use the pane scaffold; an unbounded one
        // gets a plain Row that wraps content height instead of echoing infinity (Crashlytics 788308c5).
        BoxWithConstraints(modifier = modifier) {
            if (constraints.hasBoundedHeight) {
                SplitPaneScaffold(
                    directive = directive,
                    firstPane = firstPane,
                    secondPane = secondPane,
                    paneExpansionState = paneExpansionState,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(directive.horizontalPartitionSpacerSize)) {
                    Column(modifier = Modifier.weight(1f)) { firstPane(this) }
                    Column(modifier = Modifier.weight(1f)) { secondPane(this) }
                }
            }
        }
    } else {
        // Compact / medium: keep both slots stacked in a single column (the supporting content must stay visible on
        // phones — this is not a navigable list-detail flow).
        Column(modifier = modifier) {
            firstPane(this)
            secondPane(this)
        }
    }
}

/** Test tag for the split divider; lets tests prove the scaffold branch (not the Row fallback) rendered. */
const val ADAPTIVE_TWO_PANE_DRAG_HANDLE_TAG: String = "AdaptiveTwoPaneDragHandle"

/** Canonical supporting-pane split with a draggable divider; both panes are forced visible. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun SplitPaneScaffold(
    directive: PaneScaffoldDirective,
    firstPane: @Composable (ColumnScope) -> Unit,
    secondPane: @Composable (ColumnScope) -> Unit,
    paneExpansionState: PaneExpansionState,
) {
    SupportingPaneScaffold(
        directive = directive,
        value =
        ThreePaneScaffoldValue(
            primary = PaneAdaptedValue.Expanded,
            secondary = PaneAdaptedValue.Expanded,
            tertiary = PaneAdaptedValue.Hidden,
        ),
        mainPane = { AnimatedPane { Column { firstPane(this) } } },
        supportingPane = { AnimatedPane { Column { secondPane(this) } } },
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier =
                Modifier.testTag(ADAPTIVE_TWO_PANE_DRAG_HANDLE_TAG)
                    .paneExpansionDraggable(
                        state = state,
                        minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                        interactionSource = interactionSource,
                    ),
                interactionSource = interactionSource,
            )
        },
    )
}

/** Screenshot-test sample; public so `:screenshot-tests` can render it at compact, medium, and expanded widths. */
@Suppress("MagicNumber")
@Composable
fun AdaptiveTwoPaneSample(modifier: Modifier = Modifier) {
    AppTheme {
        Surface(modifier = modifier) {
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
