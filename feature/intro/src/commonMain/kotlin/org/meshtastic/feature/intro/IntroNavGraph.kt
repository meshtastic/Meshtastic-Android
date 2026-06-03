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
package org.meshtastic.feature.intro

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Navigation graph for the application onboarding flow.
 *
 * The flow has been reduced to a single screen that lists every runtime permission the app needs. The [backStack] and
 * [viewModel] parameters are retained for call-site compatibility but are no longer used now that the multi-step intro
 * has been removed.
 */
internal fun EntryProviderScope<NavKey>.introGraph(
    backStack: NavBackStack<NavKey>,
    viewModel: IntroViewModel,
    onDone: () -> Unit,
) {
    entry<Welcome> {
        val permissions = LocalIntroPermissions.current
        PermissionsScreen(permissions = permissions, onContinue = onDone)
    }
}
