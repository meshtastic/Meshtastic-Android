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
package org.meshtastic.desktop.ui.messaging

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.conversations
import org.meshtastic.core.resources.mark_as_read
import org.meshtastic.core.resources.unread_count
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticImportFAB
import org.meshtastic.core.ui.icon.MarkChatRead
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.messaging.MessageViewModel
import org.meshtastic.feature.messaging.component.EmptyConversationsPlaceholder
import org.meshtastic.feature.messaging.ui.contact.ContactItem
import org.meshtastic.feature.messaging.ui.contact.ContactsViewModel

/**
 * Desktop adaptive contacts screen using [ListDetailPaneScaffold] from JetBrains Material 3 Adaptive.
 *
 * On wide screens, the contacts list is shown on the left and the selected conversation detail on the right. On narrow
 * screens, the scaffold automatically switches to a single-pane layout.
 *
 * Uses the shared [ContactsViewModel] and [ContactItem] from commonMain. The detail pane shows [DesktopMessageContent]
 * with a non-paged message list and send input, backed by the shared [MessageViewModel].
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
@Composable
fun DesktopAdaptiveContactsScreen(
    viewModel: ContactsViewModel,
    onNavigateToShareChannels: () -> Unit = {},
    uiViewModel: UIViewModel = koinViewModel(),
) {
    val contacts by viewModel.contactList.collectAsStateWithLifecycle()
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val unreadTotal by viewModel.unreadCountTotal.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    val sharedContactRequested by uiViewModel.sharedContactRequested.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Scaffold(
                    topBar = {
                        MainAppBar(
                            title = stringResource(Res.string.conversations),
                            subtitle =
                            if (unreadTotal > 0) {
                                stringResource(Res.string.unread_count, unreadTotal)
                            } else {
                                null
                            },
                            ourNode = ourNode,
                            showNodeChip = false,
                            canNavigateUp = false,
                            onNavigateUp = {},
                            actions = {
                                if (unreadTotal > 0) {
                                    IconButton(onClick = { viewModel.markAllAsRead() }) {
                                        Icon(
                                            MeshtasticIcons.MarkChatRead,
                                            contentDescription = stringResource(Res.string.mark_as_read),
                                        )
                                    }
                                }
                            },
                            onClickChip = {},
                        )
                    },
                    floatingActionButton = {
                        if (connectionState == ConnectionState.Connected) {
                            MeshtasticImportFAB(
                                onImport = { uriString ->
                                    uiViewModel.handleScannedUri(
                                        org.meshtastic.core.common.util.MeshtasticUri(uriString),
                                    ) {
                                        // OnInvalid
                                    }
                                },
                                onShareChannels = onNavigateToShareChannels,
                                sharedContact = sharedContactRequested,
                                onDismissSharedContact = { uiViewModel.clearSharedContactRequested() },
                                isContactContext = true,
                            )
                        }
                    },
                ) { contentPadding ->
                    if (contacts.isEmpty()) {
                        EmptyConversationsPlaceholder(modifier = Modifier.padding(contentPadding))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                            items(contacts, key = { it.contactKey }) { contact ->
                                val isActive = navigator.currentDestination?.contentKey == contact.contactKey
                                ContactItem(
                                    contact = contact,
                                    selected = false,
                                    isActive = isActive,
                                    onClick = {
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, contact.contactKey)
                                        }
                                    },
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { contactKey ->
                    val messageViewModel: MessageViewModel = koinViewModel(key = "messages-$contactKey")
                    DesktopMessageContent(contactKey = contactKey, viewModel = messageViewModel)
                } ?: EmptyConversationsPlaceholder(modifier = Modifier)
            }
        },
    )
}
