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

package com.geeksville.mesh.ui.common.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.ContactsRoutes
import com.geeksville.mesh.navigation.NodesRoutes
import com.geeksville.mesh.navigation.SettingsRoutes
import com.geeksville.mesh.navigation.showLongNameTitle
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.debug.DebugMenuActions
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.settings.radio.RadioConfigMenuActions

@Suppress("CyclomaticComplexMethod")
@Composable
fun MainAppBar(
    modifier: Modifier = Modifier,
    viewModel: UIViewModel = hiltViewModel(),
    navController: NavHostController,
    onAction: (Any?) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    if (currentDestination?.hasRoute<ContactsRoutes.Messages>() == true) {
        return
    }

    val longTitle by viewModel.title.collectAsStateWithLifecycle("")
    val onlineNodeCount by viewModel.onlineNodeCount.collectAsStateWithLifecycle(0)
    val totalNodeCount by viewModel.totalNodeCount.collectAsStateWithLifecycle(0)
    val ourNode by viewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnectedStateFlow.collectAsStateWithLifecycle(false)

    val title: String =
        when {
            currentDestination == null || currentDestination.isTopLevel() -> stringResource(id = R.string.app_name)

            currentDestination.hasRoute<SettingsRoutes.DebugPanel>() -> stringResource(id = R.string.debug_panel)

            currentDestination.hasRoute<ContactsRoutes.QuickChat>() -> stringResource(id = R.string.quick_chat)

            currentDestination.hasRoute<ContactsRoutes.Share>() -> stringResource(id = R.string.share_to)

            currentDestination.showLongNameTitle() -> longTitle

            else -> stringResource(id = R.string.app_name)
        }

    val subtitle =
        if (currentDestination?.hasRoute<NodesRoutes.Nodes>() == true) {
            stringResource(R.string.node_count_template, onlineNodeCount, totalNodeCount)
        } else {
            null
        }

    MainAppBar(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        canNavigateUp = navController.previousBackStackEntry != null && currentDestination?.isTopLevel() == false,
        ourNode = ourNode,
        isConnected = isConnected,
        showNodeChip = ourNode != null && currentDestination?.isTopLevel() == true && isConnected,
        onNavigateUp = navController::navigateUp,
        actions = {
            currentDestination?.let {
                when {
                    it.isTopLevel() -> MainMenuActions(onAction)

                    currentDestination.hasRoute<SettingsRoutes.DebugPanel>() -> DebugMenuActions()

                    currentDestination.hasRoute<SettingsRoutes.Settings>() ->
                        RadioConfigMenuActions(viewModel = viewModel)

                    else -> {}
                }
            }
        },
        onAction = onAction,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MainAppBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    ourNode: Node?,
    isConnected: Boolean,
    showNodeChip: Boolean,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    actions: @Composable () -> Unit,
    onAction: (Any?) -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        subtitle = { subtitle?.let { Text(text = it) } },
        modifier = modifier,
        navigationIcon =
        if (canNavigateUp) {
            {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            }
        } else {
            {
                IconButton(enabled = false, onClick = {}) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.app_icon),
                        contentDescription = stringResource(id = R.string.application_icon),
                    )
                }
            }
        },
        actions = {
            TopBarActions(
                ourNode = ourNode,
                isConnected = isConnected,
                showNodeChip = showNodeChip,
                actions = actions,
                onAction = onAction,
            )
        },
    )
}

@Composable
private fun TopBarActions(
    ourNode: Node?,
    isConnected: Boolean,
    showNodeChip: Boolean,
    actions: @Composable () -> Unit,
    onAction: (Any?) -> Unit,
) {
    AnimatedVisibility(showNodeChip) {
        ourNode?.let { NodeChip(node = it, isThisNode = true, isConnected = isConnected, onAction = onAction) }
    }

    actions()
}

enum class MainMenuAction(@StringRes val stringRes: Int) {
    EXPORT_RANGETEST(R.string.save_rangetest),
    SHOW_INTRO(R.string.intro_show),
    QUICK_CHAT(R.string.quick_chat),
}

@Composable
private fun MainMenuActions(onAction: (MainMenuAction) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }) {
        Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.overflow_menu))
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(colorScheme.background.copy(alpha = 1f)),
    ) {
        MainMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(id = action.stringRes)) },
                onClick = {
                    onAction(action)
                    showMenu = false
                },
                enabled = true,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun MainAppBarPreview(@PreviewParameter(BooleanProvider::class) canNavigateUp: Boolean) {
    AppTheme {
        MainAppBar(
            title = "Title",
            subtitle = "Subtitle",
            ourNode = previewNode,
            isConnected = false,
            showNodeChip = true,
            canNavigateUp = canNavigateUp,
            onNavigateUp = {},
            actions = { MainMenuActions(onAction = {}) },
        ) {}
    }
}
