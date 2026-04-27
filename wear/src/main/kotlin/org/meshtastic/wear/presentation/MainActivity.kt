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
package org.meshtastic.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.wear.presentation.theme.MeshWatchTheme

// ─────────────────────────────────────────────────────────────
//  Activity
// ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberSwipeDismissableNavController()

            MeshWatchTheme {
                AppScaffold {
                    SwipeDismissableNavHost(navController = navController, startDestination = "main_pager") {
                        composable("main_pager") {
                            MainPagerScreen(
                                onNavigateToChats = { navController.navigate("chats_menu") },
                                onNavigateToDMs = { navController.navigate("dm_list") },
                                onThreadSelect = { contactKey -> navController.navigate("chat_view/$contactKey") }
                            )
                        }
                        composable("chats_menu") {
                            ChatsMenuScreen(onChatSelect = { contactKey -> navController.navigate("chat_view/$contactKey") })
                        }
                        composable("dm_list") {
                            DMListScreen(onDMSelect = { contactKey -> navController.navigate("chat_view/$contactKey") })
                        }
                        composable("chat_view/{contactKey}") { backStackEntry ->
                            val contactKey = backStackEntry.arguments?.getString("contactKey") ?: ""
                            ChatScreen(contactKey)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Root pager — swipe left: Nodes → Messages → Settings
// ─────────────────────────────────────────────────────────────
@Composable
fun MainPagerScreen(onNavigateToChats: () -> Unit, onNavigateToDMs: () -> Unit, onThreadSelect: (String) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    HorizontalPagerScaffold(
        pagerState = pagerState,
        pageIndicator = {
            HorizontalPageIndicator(pagerState = pagerState, modifier = Modifier.padding(bottom = 4.dp))
        },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> NodesScreen()
                1 -> MessagesRootScreen(onNavigateToChats, onNavigateToDMs, onThreadSelect)
                2 -> SettingsScreen()
            }
        }
    }
}
