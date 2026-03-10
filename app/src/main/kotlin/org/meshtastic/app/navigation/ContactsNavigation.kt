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
package org.meshtastic.app.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.messaging.AndroidContactsViewModel
import org.meshtastic.app.messaging.AndroidMessageViewModel
import org.meshtastic.app.messaging.AndroidQuickChatViewModel
import org.meshtastic.app.model.UIViewModel
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.QuickChatScreen
import org.meshtastic.feature.messaging.ui.contact.AdaptiveContactsScreen
import org.meshtastic.feature.messaging.ui.sharing.ShareScreen

@Suppress("LongMethod")
fun NavGraphBuilder.contactsGraph(navController: NavHostController, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    navigation<ContactsRoutes.ContactsGraph>(startDestination = ContactsRoutes.Contacts) {
        composable<ContactsRoutes.Contacts>(
            deepLinks = listOf(navDeepLink<ContactsRoutes.Contacts>(basePath = "$DEEP_LINK_BASE_URI/contacts")),
        ) {
            val uiViewModel: UIViewModel = koinViewModel()
            val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
            val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
            val contactsViewModel = koinViewModel<AndroidContactsViewModel>()
            val messageViewModel = koinViewModel<AndroidMessageViewModel>()

            AdaptiveContactsScreen(
                navController = navController,
                contactsViewModel = contactsViewModel,
                messageViewModel = messageViewModel,
                scrollToTopEvents = scrollToTopEvents,
                sharedContactRequested = sharedContactRequested,
                requestChannelSet = requestChannelSet,
                onHandleScannedUri = uiViewModel::handleScannedUri,
                onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
                onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
            )
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
            val uiViewModel: UIViewModel = koinViewModel()
            val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
            val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
            val contactsViewModel = koinViewModel<AndroidContactsViewModel>()
            val messageViewModel = koinViewModel<AndroidMessageViewModel>()

            AdaptiveContactsScreen(
                navController = navController,
                contactsViewModel = contactsViewModel,
                messageViewModel = messageViewModel,
                scrollToTopEvents = scrollToTopEvents,
                sharedContactRequested = sharedContactRequested,
                requestChannelSet = requestChannelSet,
                onHandleScannedUri = uiViewModel::handleScannedUri,
                onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
                onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
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
        val viewModel = koinViewModel<AndroidContactsViewModel>()
        ShareScreen(
            viewModel = viewModel,
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
        val viewModel = koinViewModel<AndroidQuickChatViewModel>()
        QuickChatScreen(viewModel = viewModel, onNavigateUp = navController::navigateUp)
    }
}
