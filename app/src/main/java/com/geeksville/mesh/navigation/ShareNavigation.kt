/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.geeksville.mesh.ui.ShareScreen

fun NavController.navigateToSharedMessage(contactKey: String, message: String) {
    navigate(Route.Messages(contactKey, message)) {
        popUpTo<Route.Share> { inclusive = true }
    }
}

fun NavGraphBuilder.shareScreen(
    navigateUp: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    composable<Route.Share> { backStackEntry ->
        val message = backStackEntry.toRoute<Route.Share>().message
        ShareScreen(
            navigateUp = navigateUp,
        ) { contactKey -> onConfirm(contactKey, message) }
    }
}
