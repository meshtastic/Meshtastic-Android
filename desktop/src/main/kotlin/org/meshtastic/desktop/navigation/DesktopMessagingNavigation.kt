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
package org.meshtastic.desktop.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.desktop.ui.messaging.DesktopAdaptiveContactsScreen
import org.meshtastic.desktop.ui.messaging.DesktopMessageContent
import org.meshtastic.feature.messaging.MessageViewModel
import org.meshtastic.feature.messaging.QuickChatScreen
import org.meshtastic.feature.messaging.QuickChatViewModel
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel
import org.meshtastic.feature.messaging.ui.sharing.ShareScreen

/**
 * Registers real messaging/contacts feature composables into the desktop navigation graph.
 *
 * The contacts screen uses a desktop-specific adaptive composable with Material 3 Adaptive list-detail scaffolding,
 * backed by shared `ContactsViewModel` from commonMain. The list pane shows contacts and the detail pane shows
 * `DesktopMessageContent` using shared `MessageViewModel` with a non-paged message list.
 */
fun EntryProviderScope<NavKey>.desktopMessagingGraph(backStack: NavBackStack<NavKey>) {
    entry<ContactsRoutes.ContactsGraph> {
        val viewModel: ContactsViewModel = koinViewModel()
        DesktopAdaptiveContactsScreen(viewModel = viewModel)
    }

    entry<ContactsRoutes.Contacts> {
        val viewModel: ContactsViewModel = koinViewModel()
        DesktopAdaptiveContactsScreen(viewModel = viewModel)
    }

    entry<ContactsRoutes.Messages> { route ->
        val viewModel: MessageViewModel = koinViewModel(key = "messages-${route.contactKey}")
        DesktopMessageContent(
            contactKey = route.contactKey,
            viewModel = viewModel,
            initialMessage = route.message,
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoutes.Share> { route ->
        val viewModel: ContactsViewModel = koinViewModel()
        ShareScreen(
            viewModel = viewModel,
            onConfirm = { contactKey ->
                backStack.removeLastOrNull()
                backStack.add(ContactsRoutes.Messages(contactKey, route.message))
            },
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoutes.QuickChat> {
        val viewModel: QuickChatViewModel = koinViewModel()
        QuickChatScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }
}
