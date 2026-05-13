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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import org.meshtastic.core.navigation.MultiBackstack

/** Duration in milliseconds for the shared crossfade transition between navigation scenes. */
private const val TRANSITION_DURATION_MS = 350

/**
 * Shared [NavDisplay] wrapper that configures the standard Meshtastic entry decorators, scene strategies, and
 * transition animations for all platform hosts.
 *
 * This version supports multiple backstacks by accepting a [MultiBackstack] state holder.
 */
@Composable
fun MeshtasticNavDisplay(
    multiBackstack: MultiBackstack,
    entryProvider: (key: NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val backStack = multiBackstack.activeBackStack
    MeshtasticNavDisplay(
        backStack = backStack,
        onBack = { multiBackstack.goBack() },
        entryProvider = entryProvider,
        modifier = modifier,
    )
}

/** Shared [NavDisplay] wrapper for a single backstack. */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MeshtasticNavDisplay(
    backStack: NavBackStack<NavKey>,
    onBack: (() -> Unit)? = null,
    entryProvider: (key: NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val listDetailSceneStrategy =
        rememberListDetailSceneStrategy<NavKey>(
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
    val supportingPaneSceneStrategy =
        rememberSupportingPaneSceneStrategy<NavKey>(
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

    val saveableDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val vmStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()

    val activeDecorators =
        remember(backStack, saveableDecorator, vmStoreDecorator) { listOf(saveableDecorator, vmStoreDecorator) }

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider,
        entryDecorators = activeDecorators,
        onBack =
        onBack
            ?: {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            },
        sceneStrategies =
        listOf(
            DialogSceneStrategy(),
            listDetailSceneStrategy,
            supportingPaneSceneStrategy,
            SinglePaneSceneStrategy(),
        ),
        transitionSpec = meshtasticTransitionSpec(),
        popTransitionSpec = meshtasticTransitionSpec(),
        modifier = modifier,
    )
}

/** Shared crossfade [ContentTransform] used for both forward and pop navigation. */
private fun meshtasticTransitionSpec(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)),
        fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)),
    )
}
