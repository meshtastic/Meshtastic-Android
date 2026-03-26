/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay

/**
 * Duration in milliseconds for the shared crossfade transition between navigation scenes.
 *
 * This is faster than the library's Android default (700 ms) and matches Material 3 motion guidance for medium-emphasis
 * container transforms (~300-400 ms).
 */
private const val TRANSITION_DURATION_MS = 350

/**
 * Shared [NavDisplay] wrapper that configures the standard Meshtastic entry decorators, scene strategies, and
 * transition animations for all platform hosts.
 *
 * **Entry decorators** (applied to every backstack entry):
 * - [rememberSaveableStateHolderNavEntryDecorator] — saveable state per entry.
 * - [rememberViewModelStoreNavEntryDecorator] — entry-scoped `ViewModelStoreOwner` so that ViewModels obtained via
 *   `koinViewModel()` are automatically cleared when the entry is popped.
 *
 * **Scene strategies** (evaluated in order):
 * - [DialogSceneStrategy] — entries annotated with `metadata = DialogSceneStrategy.dialog()` render as overlay
 *   [Dialog][androidx.compose.ui.window.Dialog] windows with proper backstack lifecycle.
 * - [androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy] — entries annotated with `listPane()`, `detailPane()`, or `extraPane()` render in adaptive list-detail
 *   layout on wider screens.
 * - [androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy] — entries annotated with `mainPane()`, `supportingPane()`, or `extraPane()` render in adaptive
 *   supporting pane layout.
 * - [SinglePaneSceneStrategy] — default single-pane fallback.
 *
 * **Transitions**: A uniform 350 ms crossfade for both forward and pop navigation.
 *
 * @param backStack the navigation backstack, typically from [rememberNavBackStack].
 * @param entryProvider the entry provider built from feature navigation graphs.
 * @param modifier modifier applied to the underlying [NavDisplay].
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MeshtasticNavDisplay(
    backStack: List<NavKey>,
    entryProvider: (key: NavKey) -> NavEntry<NavKey>,
    modifier: Modifier = Modifier,
) {
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<NavKey>()
    val supportingPaneSceneStrategy = rememberSupportingPaneSceneStrategy<NavKey>()
    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider,
        entryDecorators =
        listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        sceneStrategies = listOf(
            DialogSceneStrategy(),
            listDetailSceneStrategy,
            supportingPaneSceneStrategy,
            SinglePaneSceneStrategy()
        ),
        transitionSpec = meshtasticTransitionSpec(),
        popTransitionSpec = meshtasticTransitionSpec(),
        modifier = modifier,
    )
}

/**
 * Shared crossfade [ContentTransform] used for both forward and pop navigation. Returns a lambda compatible with
 * [NavDisplay]'s `transitionSpec` / `popTransitionSpec` parameters.
 */
private fun meshtasticTransitionSpec(): AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(TRANSITION_DURATION_MS)),
        fadeOut(animationSpec = tween(TRANSITION_DURATION_MS)),
    )
}
