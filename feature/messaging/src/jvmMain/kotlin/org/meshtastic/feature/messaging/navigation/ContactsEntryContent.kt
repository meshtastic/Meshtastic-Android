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

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.MessageScreen
import org.meshtastic.feature.messaging.MessageViewModel
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel
import org.meshtastic.feature.messaging.ui.contact.AdaptiveContactsScreen

@Composable
actual fun ContactsEntryContent(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialContactKey: String?,
    initialMessage: String,
) {
    val viewModel: ContactsViewModel = koinViewModel()
    AdaptiveContactsScreen(
        backStack = backStack,
        contactsViewModel = viewModel,
        messageViewModel = koinViewModel(), // Used for desktop detail pane
        scrollToTopEvents = scrollToTopEvents,
        sharedContactRequested = null,
        requestChannelSet = null,
        onHandleScannedUri = { _, _ -> },
        onClearSharedContactRequested = {},
        onClearRequestChannelUrl = {},
        initialContactKey = initialContactKey,
        initialMessage = initialMessage,
        detailPaneCustom = { contactKey ->
            val messageViewModel: MessageViewModel = koinViewModel(key = "messages-$contactKey")
            MessageScreen(
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
