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
import com.geeksville.mesh.ui.contact.Contacts
import com.geeksville.mesh.ui.message.QuickChatScreen
import com.geeksville.mesh.ui.sharing.ShareScreen
import kotlinx.serialization.Serializable

sealed class ContactsRoutes {
    @Serializable data class Messages(val contactKey: String? = null, val message: String? = null) : Route

    @Serializable data class Share(val message: String) : Route

    @Serializable data object QuickChat : Route

    @Serializable data object ContactsGraph : Graph
}

@Suppress("LongMethod")
fun NavGraphBuilder.contactsGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<ContactsRoutes.ContactsGraph>(startDestination = ContactsRoutes.Messages()) {
        composable<ContactsRoutes.Messages>(
            deepLinks =
            listOf(
                navDeepLink<ContactsRoutes.Messages>(basePath = "$DEEP_LINK_BASE_URI/contacts"),
                navDeepLink<ContactsRoutes.Messages>(basePath = "$DEEP_LINK_BASE_URI/messages"),
            ),
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<ContactsRoutes.Messages>()
            Contacts(
                contactKey = args.contactKey,
                message = args.message,
                uiViewModel = uiViewModel,
                onNavigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                onNavigateToShare = { navController.navigate(ChannelsRoutes.Channels) },
                onNavigateToQuickChat = { navController.navigate(ContactsRoutes.QuickChat) },
            )
        }
    }
    composable<ContactsRoutes.Share>(
        deepLinks =
        listOf(
            navDeepLink<ContactsRoutes.Share>(
                basePath = "$DEEP_LINK_BASE_URI/share", // ?message={message} is auto-appended
            ),
        ),
    ) { backStackEntry ->
        val args = backStackEntry.toRoute<ContactsRoutes.Share>()
        ShareScreen(uiViewModel) {
            navController.navigate(ContactsRoutes.Messages(contactKey = it, message = args.message)) {
                popUpTo<ContactsRoutes.Share> { inclusive = true }
            }
        }
    }
    composable<ContactsRoutes.QuickChat>(
        deepLinks = listOf(navDeepLink<ContactsRoutes.QuickChat>(basePath = "$DEEP_LINK_BASE_URI/quick_chat")),
    ) {
        QuickChatScreen()
    }
}
