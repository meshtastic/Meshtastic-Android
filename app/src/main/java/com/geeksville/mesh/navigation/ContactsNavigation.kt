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
import com.geeksville.mesh.ui.contact.AdaptiveContactsScreen
import com.geeksville.mesh.ui.sharing.ShareScreen
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.QuickChatScreen

@Suppress("LongMethod")
fun NavGraphBuilder.contactsGraph(navController: NavHostController, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    navigation<ContactsRoutes.ContactsGraph>(startDestination = ContactsRoutes.Contacts) {
        composable<ContactsRoutes.Contacts>(
            deepLinks = listOf(navDeepLink<ContactsRoutes.Contacts>(basePath = "$DEEP_LINK_BASE_URI/contacts")),
        ) {
            AdaptiveContactsScreen(navController = navController, scrollToTopEvents = scrollToTopEvents)
        }
        composable<ContactsRoutes.Messages>(
            deepLinks =
            listOf(
                navDeepLink<ContactsRoutes.Messages>(
                    basePath =
                    "$DEEP_LINK_BASE_URI/messages", // {contactKey} and ?message={message} are auto-appended
                ),
            ),
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<ContactsRoutes.Messages>()
            AdaptiveContactsScreen(
                navController = navController,
                scrollToTopEvents = scrollToTopEvents,
                initialContactKey = args.contactKey,
                initialMessage = args.message,
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
        val message = backStackEntry.toRoute<ContactsRoutes.Share>().message
        ShareScreen(
            onConfirm = {
                navController.navigate(ContactsRoutes.Messages(it, message)) {
                    popUpTo<ContactsRoutes.Share> { inclusive = true }
                }
            },
            onNavigateUp = navController::navigateUp,
        )
    }
    composable<ContactsRoutes.QuickChat>(
        deepLinks = listOf(navDeepLink<ContactsRoutes.QuickChat>(basePath = "$DEEP_LINK_BASE_URI/quick_chat")),
    ) {
        QuickChatScreen(onNavigateUp = navController::navigateUp)
    }
}
