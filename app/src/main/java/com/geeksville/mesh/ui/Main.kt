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

package com.geeksville.mesh.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudUpload
import androidx.compose.material.icons.twotone.Contactless
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.People
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.NavGraph
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.showLongNameTitle
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.TopLevelDestination.Companion.isTopLevel
import com.geeksville.mesh.ui.components.MultipleChoiceAlertDialog
import com.geeksville.mesh.ui.components.ScannedQrCodeDialog
import com.geeksville.mesh.ui.components.SimpleAlertDialog

enum class TopLevelDestination(val label: String, val icon: ImageVector, val route: Route) {
    Contacts("Contacts", Icons.AutoMirrored.TwoTone.Chat, Route.Contacts),
    Nodes("Nodes", Icons.TwoTone.People, Route.Nodes),
    Map("Map", Icons.TwoTone.Map, Route.Map),
    Channels("Channels", Icons.TwoTone.Contactless, Route.Channels),
    Settings("Settings", Icons.TwoTone.Settings, Route.Settings),
    ;

    companion object {
        fun NavDestination.isTopLevel(): Boolean = entries.any { hasRoute(it.route::class) }

        fun fromNavDestination(destination: NavDestination?): TopLevelDestination? = entries
            .find { dest -> destination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true }
    }
}

@Suppress("LongMethod")
@Composable
fun MainScreen(
    viewModel: UIViewModel = hiltViewModel(),
    onAction: (MainMenuAction) -> Unit
) {
    val navController = rememberNavController()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val localConfig by viewModel.localConfig.collectAsStateWithLifecycle()
    val requestChannelSet by viewModel.requestChannelSet.collectAsStateWithLifecycle()

    if (connectionState.isConnected()) {
        requestChannelSet?.let { newChannelSet ->
            ScannedQrCodeDialog(viewModel, newChannelSet)
        }
    }
    val title by viewModel.title.collectAsStateWithLifecycle()

    val alertDialogState by viewModel.currentAlert.collectAsStateWithLifecycle()
    alertDialogState?.let { state ->
        if (state.choices.isNotEmpty()) {
            MultipleChoiceAlertDialog(
                title = state.title,
                message = state.message,
                choices = state.choices,
                onDismissRequest = { state.onDismiss?.let { it() } },
            )
        } else {
            SimpleAlertDialog(
                title = state.title,
                message = state.message,
                html = state.html,
                onConfirmRequest = { state.onConfirm?.let { it() } },
                onDismissRequest = { state.onDismiss?.let { it() } },
            )
        }
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = title,
                isManaged = localConfig.security.isManaged,
                connectionState = connectionState,
                navController = navController,
            ) { action ->
                when (action) {
                    MainMenuAction.DEBUG -> navController.navigate(Route.DebugPanel)
                    MainMenuAction.RADIO_CONFIG -> navController.navigate(Route.RadioConfig())
                    MainMenuAction.QUICK_CHAT -> navController.navigate(Route.QuickChat)
                    else -> onAction(action)
                }
            }
        },
        bottomBar = {
            BottomNavigation(
                navController = navController,
            )
        },
        snackbarHost = { SnackbarHost(hostState = viewModel.snackbarState) }
    ) { innerPadding ->
        NavGraph(
            modifier = Modifier.padding(innerPadding),
            uIViewModel = viewModel,
            navController = navController,
        )
    }
}

enum class MainMenuAction(@StringRes val stringRes: Int) {
    DEBUG(R.string.debug_panel),
    RADIO_CONFIG(R.string.device_settings),
    EXPORT_MESSAGES(R.string.save_messages),
    THEME(R.string.theme),
    LANGUAGE(R.string.preferences_language),
    SHOW_INTRO(R.string.intro_show),
    QUICK_CHAT(R.string.quick_chat),
    ABOUT(R.string.about),
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
private fun MainAppBar(
    title: String,
    isManaged: Boolean,
    connectionState: MeshService.ConnectionState,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onAction: (MainMenuAction) -> Unit
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val canNavigateBack = navController.previousBackStackEntry != null
    val isTopLevelRoute = currentDestination?.isTopLevel() == true
    val navigateUp: () -> Unit = navController::navigateUp
    if (currentDestination?.hasRoute<Route.Messages>() == true) {
        return
    }
    TopAppBar(
        title = {
            when {
                currentDestination == null || isTopLevelRoute -> {
                    Text(
                        text = stringResource(id = R.string.app_name),
                    )
                }

                currentDestination.hasRoute<Route.DebugPanel>() ->
                    Text(
                        stringResource(id = R.string.debug_panel),
                    )

                currentDestination.hasRoute<Route.QuickChat>() ->
                    Text(
                        stringResource(id = R.string.quick_chat),
                    )

                currentDestination.hasRoute<Route.Share>() ->
                    Text(
                        stringResource(id = R.string.share_to),
                    )

                currentDestination.showLongNameTitle() -> {
                    Text(
                        title,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = if (canNavigateBack && !isTopLevelRoute) {
            {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.navigate_back),
                    )
                }
            }
        } else {
            {
                IconButton(
                    enabled = false,
                    onClick = { },
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.app_icon),
                        contentDescription = stringResource(id = R.string.application_icon),
                    )
                }
            }
        },
        actions = {
            when {
                currentDestination == null || isTopLevelRoute ->
                    MainMenuActions(isManaged, connectionState, onAction)

                currentDestination.hasRoute<Route.DebugPanel>() ->
                    DebugMenuActions()

                else -> {}
            }
        },
    )
}

@Composable
private fun MainMenuActions(
    isManaged: Boolean,
    connectionState: MeshService.ConnectionState,
    onAction: (MainMenuAction) -> Unit
) {
    val context = LocalContext.current
    val (image, tooltip) = when (connectionState) {
        MeshService.ConnectionState.CONNECTED -> Icons.TwoTone.CloudDone to R.string.connected
        MeshService.ConnectionState.DEVICE_SLEEP -> Icons.TwoTone.CloudUpload to R.string.device_sleeping
        MeshService.ConnectionState.DISCONNECTED -> Icons.TwoTone.CloudOff to R.string.disconnected
    }

    var showMenu by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            Toast.makeText(context, tooltip, Toast.LENGTH_SHORT).show()
        },
    ) {
        Icon(
            imageVector = image,
            contentDescription = stringResource(id = tooltip),
        )
    }
    IconButton(onClick = { showMenu = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Overflow menu",
        )
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false },
        modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 1f)),
    ) {
        MainMenuAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(stringResource(id = action.stringRes)) },
                onClick = {
                    onAction(action)
                    showMenu = false
                },
                enabled = when (action) {
                    MainMenuAction.RADIO_CONFIG -> !isManaged
                    else -> true
                },
            )
        }
    }
}

@Composable
private fun BottomNavigation(
    navController: NavController,
) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination
    val topLevelDestination = TopLevelDestination.fromNavDestination(currentDestination)

    AnimatedVisibility(
        visible = topLevelDestination != null,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 200),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 200),
        ),
    ) {
        NavigationBar {
            TopLevelDestination.entries.forEach {
                val isSelected = it == topLevelDestination
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.name,
                        )
                    },
                    // label = { Text(it.label) },
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            navController.navigate(it.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}
