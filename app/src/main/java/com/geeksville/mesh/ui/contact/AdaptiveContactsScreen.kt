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

package com.geeksville.mesh.ui.contact

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.conversations
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.core.ui.icon.Conversations
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.messaging.MessageScreen

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AdaptiveContactsScreen(
    navController: NavHostController,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    initialContactKey: String? = null,
    initialMessage: String = "",
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val backNavigationBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange

    BackHandler(enabled = navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
        scope.launch { navigator.navigateBack(backNavigationBehavior) }
    }

    LaunchedEffect(initialContactKey) {
        if (initialContactKey != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, initialContactKey)
        }
    }
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                ContactsScreen(
                    onNavigateToShare = { navController.navigate(ChannelsRoutes.ChannelsGraph) },
                    onClickNodeChip = {
                        navController.navigate(NodesRoutes.NodeDetailGraph(it)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToMessages = { contactKey ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, contactKey) }
                    },
                    onNavigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
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
                            navigateToNodeDetails = { navController.navigate(NodesRoutes.NodeDetailGraph(it)) },
                            navigateToQuickChatOptions = { navController.navigate(ContactsRoutes.QuickChat) },
                            onNavigateBack = { scope.launch { navigator.navigateBack(backNavigationBehavior) } },
                        )
                    }
                } ?: PlaceholderScreen()
            }
        },
    )
}

@Composable
private fun PlaceholderScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = MeshtasticIcons.Conversations,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.conversations),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
