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
package org.meshtastic.feature.messaging.ui.contact

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.conversations
import org.meshtastic.core.ui.component.EmptyDetailPlaceholder
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.icon.Conversations
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.messaging.MessageScreen
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact

import org.meshtastic.core.common.util.MeshtasticUri

@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveContactsScreen(
    backStack: NavBackStack<NavKey>,
    contactsViewModel: org.meshtastic.feature.messaging.ui.contact.ContactsViewModel,
    messageViewModel: org.meshtastic.feature.messaging.MessageViewModel,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    sharedContactRequested: SharedContact?,
    requestChannelSet: ChannelSet?,
    onHandleScannedUri: (MeshtasticUri, onInvalid: () -> Unit) -> Unit,
    onClearSharedContactRequested: () -> Unit,
    onClearRequestChannelUrl: () -> Unit,
    initialContactKey: String? = null,
    initialMessage: String = "",
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    val handleBack: () -> Unit = {
        val currentKey = backStack.lastOrNull()

        if (
            currentKey is ContactsRoutes.Messages ||
            currentKey is ContactsRoutes.Contacts ||
            currentKey is ContactsRoutes.ContactsGraph
        ) {
            // Check if we navigated here from another screen (e.g., from Nodes or Map)
            val previousKey = if (backStack.size > 1) backStack[backStack.size - 2] else null
            val isFromDifferentGraph =
                previousKey != null &&
                    previousKey !is ContactsRoutes.ContactsGraph &&
                    previousKey !is ContactsRoutes.Contacts &&
                    previousKey !is ContactsRoutes.Messages

            if (isFromDifferentGraph) {
                // Navigate back via NavController to return to the previous screen (e.g. Node Details)
                backStack.removeLastOrNull()
            } else {
                // Close the detail pane within the adaptive scaffold
                scope.launch { navigator.navigateBack(backNavigationBehavior) }
            }
        } else {
            scope.launch { navigator.navigateBack(backNavigationBehavior) }
        }
    }

    PredictiveBackHandler(
        enabled = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail,
    ) { progress ->
        try {
            progress.collect { /* Predictive back progress could be used here to drive UI if scaffold supported it */ }
            handleBack()
        } catch (_: CancellationException) {
            // Gesture cancelled
        }
    }

    LaunchedEffect(initialContactKey) {
        if (initialContactKey != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, initialContactKey)
        }
    }

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents.collect { event ->
            if (
                event is ScrollToTopEvent.ConversationsTabPressed &&
                navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
            ) {
                if (navigator.canNavigateBack(backNavigationBehavior)) {
                    navigator.navigateBack(backNavigationBehavior)
                } else {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.List)
                }
            }
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                ContactsScreen(
                    onNavigateToShare = { backStack.add(ChannelsRoutes.ChannelsGraph) },
                    sharedContactRequested = sharedContactRequested,
                    requestChannelSet = requestChannelSet,
                    onHandleScannedUri = onHandleScannedUri,
                    onClearSharedContactRequested = onClearSharedContactRequested,
                    onClearRequestChannelUrl = onClearRequestChannelUrl,
                    viewModel = contactsViewModel,
                    onClickNodeChip = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
                    onNavigateToMessages = { contactKey ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, contactKey) }
                    },
                    onNavigateToNodeDetails = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
                    scrollToTopEvents = scrollToTopEvents,
                    activeContactKey = navigator.currentDestination?.contentKey,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                navigator.currentDestination?.contentKey?.let { contactKey ->
                    key(contactKey) {
                        MessageScreen(
                            contactKey = contactKey,
                            message = if (contactKey == initialContactKey) initialMessage else "",
                            viewModel = messageViewModel,
                            navigateToNodeDetails = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
                            navigateToQuickChatOptions = { backStack.add(ContactsRoutes.QuickChat) },
                            onNavigateBack = handleBack,
                        )
                    }
                }
                    ?: EmptyDetailPlaceholder(
                        icon = MeshtasticIcons.Conversations,
                        title = stringResource(Res.string.conversations),
                    )
            }
        },
    )
}
