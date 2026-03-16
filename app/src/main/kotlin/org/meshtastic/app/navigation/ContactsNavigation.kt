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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.messaging.MessageViewModel
import org.meshtastic.feature.messaging.QuickChatScreen
import org.meshtastic.feature.messaging.QuickChatViewModel
import org.meshtastic.feature.messaging.ui.contact.AdaptiveContactsScreen
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel
import org.meshtastic.feature.messaging.ui.sharing.ShareScreen

@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.contactsGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
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
private fun ContactsEntryContent(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialContactKey: String? = null,
    initialMessage: String = "",
) {
    val uiViewModel: UIViewModel = koinViewModel()
    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
    val contactsViewModel = koinViewModel<ContactsViewModel>()
    val messageViewModel = koinViewModel<MessageViewModel>()
    initialContactKey?.let { messageViewModel.setContactKey(it) }

    AdaptiveContactsScreen(
        backStack = backStack,
        contactsViewModel = contactsViewModel,
        messageViewModel = messageViewModel,
        scrollToTopEvents = scrollToTopEvents,
        sharedContactRequested = sharedContactRequested,
        requestChannelSet = requestChannelSet,
        onHandleScannedUri = uiViewModel::handleScannedUri,
        onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
        onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
        initialContactKey = initialContactKey,
        initialMessage = initialMessage,
    )
}
