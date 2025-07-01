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

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.contact.ContactsScreen
import com.geeksville.mesh.ui.message.MessageScreen
import com.geeksville.mesh.ui.message.QuickChatScreen
import com.geeksville.mesh.ui.sharing.ShareScreen
import kotlinx.serialization.Serializable

sealed class ContactsRoutes {
    @Serializable
    data object Contacts : Route

    @Serializable
    data class Messages(val contactKey: String, val message: String = "") : Route

    @Serializable
    data class Share(val message: String) : Route

    @Serializable
    data object QuickChat : Route

    @Serializable
    data object ContactsGraph : Graph
}

fun NavGraphBuilder.contactsGraph(
    navController: NavHostController,
    uiViewModel: UIViewModel,
) {
    navigation<ContactsRoutes.ContactsGraph>(
        startDestination = ContactsRoutes.Contacts,
    ) {
        composable<ContactsRoutes.Contacts> {
            ContactsScreen(
                uiViewModel,
                onNavigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) }
            )
        }
        composable<ContactsRoutes.Messages>(
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "$DEEP_LINK_BASE_URI/messages/{contactKey}?message={message}"
                    action = "android.intent.action.VIEW"
                },
            )
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<ContactsRoutes.Messages>()
            MessageScreen(
                contactKey = args.contactKey,
                message = args.message,
                viewModel = uiViewModel,
                navigateToMessages = { navController.navigate(ContactsRoutes.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                onNavigateBack = navController::navigateUp,
            )
        }
    }
    composable<ContactsRoutes.Share>(
        deepLinks = listOf(
            navDeepLink {
                uriPattern = "$DEEP_LINK_BASE_URI/share?message={message}"
                action = "android.intent.action.VIEW"
            }
        )
    ) { backStackEntry ->
        val message = backStackEntry.toRoute<ContactsRoutes.Share>().message
        ShareScreen(uiViewModel) {
            navController.navigate(ContactsRoutes.Messages(it, message)) {
                popUpTo<ContactsRoutes.Share> { inclusive = true }
            }
        }
    }
    composable<ContactsRoutes.QuickChat> {
        QuickChatScreen()
    }
}
