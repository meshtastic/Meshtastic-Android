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
package org.meshtastic.feature.messaging.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle


import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.QuickChatScreen
import org.meshtastic.feature.messaging.QuickChatViewModel
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel
import org.meshtastic.feature.messaging.ui.sharing.ShareScreen
import org.meshtastic.feature.messaging.ui.contact.AdaptiveContactsScreen

@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.contactsGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent> = MutableSharedFlow(),
) {
    entry<ContactsRoutes.ContactsGraph> {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoutes.Contacts> {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoutes.Messages> { args ->
        ContactsEntryContent(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            initialContactKey = args.contactKey,
            initialMessage = args.message,
        )
    }

    entry<ContactsRoutes.Share> { args ->
        val message = args.message
        val viewModel = koinViewModel<ContactsViewModel>()
        ShareScreen(
            viewModel = viewModel,
            onConfirm = {
                // Navigation 3 - replace Top with Messages manually, but for now we just pop and add
                backStack.removeLastOrNull()
                backStack.add(ContactsRoutes.Messages(it, message))
            },
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoutes.QuickChat> {
        val viewModel = koinViewModel<QuickChatViewModel>()
        QuickChatScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
}

@Composable
fun ContactsEntryContent(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialContactKey: String? = null,
    initialMessage: String = "",
) {
    val uiViewModel: org.meshtastic.core.ui.viewmodel.UIViewModel = koinViewModel()
    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
    val contactsViewModel = koinViewModel<ContactsViewModel>()

    AdaptiveContactsScreen(
        backStack = backStack,
        contactsViewModel = contactsViewModel,
        messageViewModel = koinViewModel(), // Ignored by custom detail pane below
        scrollToTopEvents = scrollToTopEvents,
        sharedContactRequested = sharedContactRequested,
        requestChannelSet = requestChannelSet,
        onHandleDeepLink = uiViewModel::handleDeepLink,
        onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
        onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
        initialContactKey = initialContactKey,
        initialMessage = initialMessage,
        detailPaneCustom = { contactKey ->
            val messageViewModel: org.meshtastic.feature.messaging.MessageViewModel = koinViewModel(key = "messages-$contactKey")
            messageViewModel.setContactKey(contactKey)
            
            org.meshtastic.feature.messaging.MessageScreen(
                contactKey = contactKey,
                message = if (contactKey == initialContactKey) initialMessage else "",
                viewModel = messageViewModel,
                navigateToNodeDetails = {
                    backStack.add(org.meshtastic.core.navigation.NodesRoutes.NodeDetailGraph(it))
                },
                navigateToQuickChatOptions = { backStack.add(org.meshtastic.core.navigation.ContactsRoutes.QuickChat) },
                onNavigateBack = { backStack.removeLastOrNull() },
            )
        },
    )
}
