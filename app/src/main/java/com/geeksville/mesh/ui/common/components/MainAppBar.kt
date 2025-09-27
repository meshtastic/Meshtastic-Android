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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.geeksville.mesh.ui.debug.DebugMenuActions
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.theme.AppTheme

@Suppress("CyclomaticComplexMethod")
@Composable
fun MainAppBar(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    ourNode: Node?,
    onAction: (NodeMenuAction) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    if (currentDestination?.hasRoute<ContactsRoutes.Messages>() == true) {
        return
    }

    val title: String =
        when {
            currentDestination == null -> ""

            currentDestination.hasRoute<SettingsRoutes.DebugPanel>() -> stringResource(id = R.string.debug_panel)

            currentDestination.hasRoute<ContactsRoutes.QuickChat>() -> stringResource(id = R.string.quick_chat)

            currentDestination.hasRoute<ContactsRoutes.Share>() -> stringResource(id = R.string.share_to)

            else -> ""
        }

    MainAppBar(
        modifier = modifier,
        title = title,
        subtitle = null,
        canNavigateUp = navController.previousBackStackEntry != null,
        ourNode = ourNode,
        showNodeChip = false,
        onNavigateUp = navController::navigateUp,
        actions = {
            currentDestination?.let {
                when {
                    currentDestination.hasRoute<SettingsRoutes.DebugPanel>() -> DebugMenuActions()
                    else -> {}
                }
            }
        },
        onAction = onAction,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    ourNode: Node?,
    showNodeChip: Boolean,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    actions: @Composable () -> Unit,
    onAction: (NodeMenuAction) -> Unit,
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
                        imageVector = ImageVector.vectorResource(id = com.geeksville.mesh.R.drawable.app_icon),
                        contentDescription = stringResource(id = R.string.application_icon),
                    )
                }
            }
        },
        actions = {
            TopBarActions(ourNode = ourNode, showNodeChip = showNodeChip, actions = actions, onAction = onAction)
        },
    )
}

@Composable
private fun TopBarActions(
    ourNode: Node?,
    showNodeChip: Boolean,
    actions: @Composable () -> Unit,
    onAction: (NodeMenuAction) -> Unit,
) {
    AnimatedVisibility(visible = showNodeChip, enter = fadeIn(), exit = fadeOut()) {
        ourNode?.let { NodeChip(modifier = Modifier.padding(horizontal = 16.dp), node = it, onAction = onAction) }
    }

    actions()
}

@PreviewLightDark
@Composable
private fun MainAppBarPreview(@PreviewParameter(BooleanProvider::class) canNavigateUp: Boolean) {
    AppTheme {
        MainAppBar(
            title = "Title",
            subtitle = "Subtitle",
            ourNode = previewNode,
            showNodeChip = true,
            canNavigateUp = canNavigateUp,
            onNavigateUp = {},
            actions = {},
        ) {}
    }
}
