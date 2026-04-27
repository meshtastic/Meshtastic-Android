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

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.ContactsRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.replaceLast
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.QuickChatScreen
import org.meshtastic.feature.messaging.QuickChatViewModel
import org.meshtastic.feature.messaging.ui.contact.AdaptiveContactsScreen
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel
import org.meshtastic.feature.messaging.ui.sharing.ShareScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.contactsGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent> = MutableSharedFlow(),
) {
    entry<ContactsRoute.ContactsGraph>(metadata = { ListDetailSceneStrategy.listPane() }) {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoute.Contacts>(metadata = { ListDetailSceneStrategy.listPane() }) {
        ContactsEntryContent(backStack = backStack, scrollToTopEvents = scrollToTopEvents)
    }

    entry<ContactsRoute.Messages>(metadata = { ListDetailSceneStrategy.detailPane() }) { args ->
        val contactKey = args.contactKey
        val messageViewModel: org.meshtastic.feature.messaging.MessageViewModel =
            koinViewModel(key = "messages-$contactKey")
        messageViewModel.setContactKey(contactKey)

        org.meshtastic.feature.messaging.MessageScreen(
            contactKey = contactKey,
            message = args.message,
            viewModel = messageViewModel,
            navigateToNodeDetails = { id -> backStack.add(NodesRoute.NodeDetail(id)) },
            navigateToQuickChatOptions =
            dropUnlessResumed { backStack.add(org.meshtastic.core.navigation.ContactsRoute.QuickChat) },
            navigateToFilterSettings = dropUnlessResumed { backStack.add(SettingsRoute.FilterSettings) },
            onNavigateBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoute.Share>(metadata = { ListDetailSceneStrategy.extraPane() }) { args ->
        val message = args.message
        val viewModel = koinViewModel<ContactsViewModel>()
        ShareScreen(
            viewModel = viewModel,
            onConfirm = { contactKey -> backStack.replaceLast(ContactsRoute.Messages(contactKey, message)) },
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<ContactsRoute.QuickChat>(metadata = { ListDetailSceneStrategy.extraPane() }) {
        val viewModel = koinViewModel<QuickChatViewModel>()
        QuickChatScreen(viewModel = viewModel, onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

@Composable
fun ContactsEntryContent(backStack: NavBackStack<NavKey>, scrollToTopEvents: Flow<ScrollToTopEvent>) {
    val uiViewModel: org.meshtastic.core.ui.viewmodel.UIViewModel = koinViewModel()
    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val requestChannelSet by uiViewModel.requestChannelSet.collectAsStateWithLifecycle()
    val contactsViewModel = koinViewModel<ContactsViewModel>()

    AdaptiveContactsScreen(
        backStack = backStack,
        contactsViewModel = contactsViewModel,
        scrollToTopEvents = scrollToTopEvents,
        sharedContactRequested = sharedContactRequested,
        requestChannelSet = requestChannelSet,
        onHandleDeepLink = uiViewModel::handleDeepLink,
        onClearSharedContactRequested = uiViewModel::clearSharedContactRequested,
        onClearRequestChannelUrl = uiViewModel::clearRequestChannelUrl,
    )
}
