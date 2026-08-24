/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.feature.settings.AboutScreen
import org.meshtastic.feature.settings.AcknowledgementsScreen
import org.meshtastic.feature.settings.AdministrationScreen
import org.meshtastic.feature.settings.DeviceConfigurationScreen
import org.meshtastic.feature.settings.DeviceLinkDirectoryScreen
import org.meshtastic.feature.settings.ModuleConfigurationScreen
import org.meshtastic.feature.settings.NodeListScreen
import org.meshtastic.feature.settings.SettingsViewModel
import org.meshtastic.feature.settings.appfunctions.AppFunctionsSettingsScreen
import org.meshtastic.feature.settings.appfunctions.AppFunctionsSettingsViewModel
import org.meshtastic.feature.settings.debugging.DebugScreen
import org.meshtastic.feature.settings.debugging.DebugViewModel
import org.meshtastic.feature.settings.filter.FilterSettingsScreen
import org.meshtastic.feature.settings.filter.FilterSettingsViewModel
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseScreen
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.AmbientLightingConfigScreen
import org.meshtastic.feature.settings.radio.component.AudioConfigScreen
import org.meshtastic.feature.settings.radio.component.BluetoothConfigScreen
import org.meshtastic.feature.settings.radio.component.CannedMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.DetectionSensorConfigScreen
import org.meshtastic.feature.settings.radio.component.DeviceConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.DisplayConfigScreen
import org.meshtastic.feature.settings.radio.component.ExternalNotificationConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen
import org.meshtastic.feature.settings.radio.component.MQTTConfigScreen
import org.meshtastic.feature.settings.radio.component.MeshBeaconConfigScreen
import org.meshtastic.feature.settings.radio.component.NeighborInfoConfigScreen
import org.meshtastic.feature.settings.radio.component.NetworkConfigScreen
import org.meshtastic.feature.settings.radio.component.PaxcounterConfigScreen
import org.meshtastic.feature.settings.radio.component.PositionConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.PowerConfigScreen
import org.meshtastic.feature.settings.radio.component.RangeTestConfigScreen
import org.meshtastic.feature.settings.radio.component.RemoteHardwareConfigScreen
import org.meshtastic.feature.settings.radio.component.SecurityConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.SerialConfigScreen
import org.meshtastic.feature.settings.radio.component.StatusMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.StoreForwardConfigScreen
import org.meshtastic.feature.settings.radio.component.TAKConfigScreen
import org.meshtastic.feature.settings.radio.component.TakServerScreen
import org.meshtastic.feature.settings.radio.component.TelemetryConfigScreen
import org.meshtastic.feature.settings.radio.component.UserConfigScreen
import kotlin.reflect.KClass

/**
 * Identifies whether the active back stack is outside settings, local settings, or remote settings for one node.
 * Keeping these states distinct prevents a local settings ViewModel from remaining active on unrelated tabs.
 */
internal sealed interface SettingsRadioConfigSession {
    data object Inactive : SettingsRadioConfigSession

    sealed interface Active : SettingsRadioConfigSession {
        val destination: Int?
        val viewModelKey: String
    }

    data object Local : Active {
        override val destination: Int? = null
        override val viewModelKey: String = "settings-local"
    }

    data class Remote(override val destination: Int) : Active {
        override val viewModelKey: String = "settings-remote-$destination"
    }
}

internal class SettingsRadioConfigViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    fun clear() = viewModelStore.clear()
}

/**
 * Retains settings-session stores across configuration changes. A store remains available while its session is the
 * active settings destination or while a navigation entry still holds a lease during an exit transition. Once neither
 * condition applies, the store is cleared and its [RadioConfigViewModel] collectors are cancelled.
 */
@KoinViewModel
internal class SettingsRadioConfigSessionHolder : ViewModel() {
    private data class SessionStore(
        val owner: SettingsRadioConfigViewModelStoreOwner = SettingsRadioConfigViewModelStoreOwner(),
        var leases: Int = 0,
    )

    private val stores = mutableMapOf<SettingsRadioConfigSession.Active, SessionStore>()
    private var activeSession: SettingsRadioConfigSession = SettingsRadioConfigSession.Inactive
    private var lastActiveSession: SettingsRadioConfigSession.Active? = null

    fun activate(session: SettingsRadioConfigSession) {
        activeSession = session
        if (session is SettingsRadioConfigSession.Active) lastActiveSession = session
        clearUnusedStores()
    }

    /**
     * Resolves the session captured by a settings entry. During an exit transition the active back stack may already be
     * outside settings, so the most recently active session is used instead of throwing from the outgoing composition.
     */
    fun resolveEntrySession(session: SettingsRadioConfigSession): SettingsRadioConfigSession.Active =
        (session as? SettingsRadioConfigSession.Active) ?: lastActiveSession ?: SettingsRadioConfigSession.Local

    fun ownerFor(session: SettingsRadioConfigSession.Active): ViewModelStoreOwner = storeFor(session).owner

    fun retain(session: SettingsRadioConfigSession.Active) {
        storeFor(session).leases += 1
    }

    fun release(session: SettingsRadioConfigSession.Active) {
        stores[session]?.let { store -> if (store.leases > 0) store.leases -= 1 }
        clearUnusedStores()
    }

    private fun storeFor(session: SettingsRadioConfigSession.Active): SessionStore =
        stores.getOrPut(session, ::SessionStore)

    private fun clearUnusedStores() {
        val current = activeSession as? SettingsRadioConfigSession.Active
        val iterator = stores.iterator()
        while (iterator.hasNext()) {
            val (session, store) = iterator.next()
            if (session != current && store.leases == 0) {
                store.owner.clear()
                iterator.remove()
            }
        }
    }

    override fun onCleared() {
        stores.values.forEach { it.owner.clear() }
        stores.clear()
        super.onCleared()
    }
}

/**
 * Observes back-stack mutations without invalidating the app shell for every settings submenu push or pop. The returned
 * state changes only when the logical settings session changes.
 */
@Composable
internal fun rememberSettingsRadioConfigSession(backStack: NavBackStack<NavKey>) =
    produceState(initialValue = settingsRadioConfigSession(backStack.toList()), backStack) {
        snapshotFlow { settingsRadioConfigSession(backStack.toList()) }.distinctUntilChanged().collect { value = it }
    }

@Composable
internal fun settingsRadioConfigViewModel(
    session: SettingsRadioConfigSession.Active,
    viewModelStoreOwner: ViewModelStoreOwner,
): RadioConfigViewModel =
    koinViewModel<RadioConfigViewModel>(key = session.viewModelKey, viewModelStoreOwner = viewModelStoreOwner) {
        parametersOf(session.destination)
    }

/** Returns the retained provider used by all entries in the active settings session. */
@Composable
fun rememberSettingsRadioConfigViewModelProvider(
    backStack: NavBackStack<NavKey>,
): @Composable (SettingsRoute.Settings?) -> RadioConfigViewModel {
    val session by rememberSettingsRadioConfigSession(backStack)
    val holder: SettingsRadioConfigSessionHolder = koinViewModel()
    SideEffect { holder.activate(session) }

    return remember(backStack, holder) {
        @Composable { settingsRoot ->
            // Capture the session once for this navigation entry. An outgoing entry keeps its original local/remote
            // session during crossfades even after the active top-level back stack has changed.
            val entrySession =
                remember(settingsRoot, holder) {
                    val candidate =
                        if (settingsRoot != null) {
                            settingsRoot.destNum?.let(SettingsRadioConfigSession::Remote)
                                ?: SettingsRadioConfigSession.Local
                        } else {
                            settingsRadioConfigSession(backStack.toList())
                        }
                    holder.resolveEntrySession(candidate)
                }
            val owner = remember(entrySession, holder) { holder.ownerFor(entrySession) }

            DisposableEffect(holder, entrySession) {
                holder.retain(entrySession)
                onDispose { holder.release(entrySession) }
            }

            settingsRadioConfigViewModel(session = entrySession, viewModelStoreOwner = owner)
        }
    }
}

internal fun settingsRadioConfigSession(backStack: List<NavKey>): SettingsRadioConfigSession {
    if (backStack.none(NavKey::usesRadioConfigSettingsSession)) return SettingsRadioConfigSession.Inactive

    // Remote administration always carries a Settings root with its destination. Local configuration can also be
    // opened directly from another graph (currently Connections -> LoRa), so the absence of a root is still an active
    // local session while a radio-config route remains on the active back stack.
    val settingsRoot = backStack.filterIsInstance<SettingsRoute.Settings>().lastOrNull()
    return settingsRoot?.destNum?.let(SettingsRadioConfigSession::Remote) ?: SettingsRadioConfigSession.Local
}

private fun NavKey.usesRadioConfigSettingsSession(): Boolean = this is SettingsRoute.Settings ||
    this == SettingsRoute.DeviceConfiguration ||
    this == SettingsRoute.ModuleConfiguration ||
    this == SettingsRoute.Administration ||
    ConfigRoute.entries.any { it.route == this } ||
    ModuleRoute.entries.any { it.route == this }

internal fun settingsDestination(backStack: List<NavKey>): Int? =
    (settingsRadioConfigSession(backStack) as? SettingsRadioConfigSession.Active)?.destination

internal fun shouldAddSettingsRoute(current: NavKey?, route: Route): Boolean = current != route

private fun NavBackStack<NavKey>.addSettingsRoute(route: Route) {
    if (shouldAddSettingsRoute(lastOrNull(), route)) add(route)
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EntryProviderScope<NavKey>.settingsGraph(
    backStack: NavBackStack<NavKey>,
    radioConfigViewModelProvider: @Composable (SettingsRoute.Settings?) -> RadioConfigViewModel,
) {
    entry<SettingsRoute.Settings> { args ->
        val isTabRoot = backStack.firstOrNull() == args
        SettingsMainScreen(
            settingsViewModel = koinViewModel(),
            radioConfigViewModel = radioConfigViewModelProvider(args),
            onClickNodeChip = { backStack.add(NodesRoute.NodeDetail(it)) },
            onNavigate = backStack::addSettingsRoute,
            onBack = if (isTabRoot) null else dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.DeviceConfiguration> {
        DeviceConfigurationScreen(
            viewModel = radioConfigViewModelProvider(null),
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigate = backStack::addSettingsRoute,
        )
    }

    entry<SettingsRoute.ModuleConfiguration> {
        val settingsViewModel: SettingsViewModel = koinViewModel()
        val hiddenFeaturesUnlocked by settingsViewModel.hiddenFeaturesUnlocked.collectAsStateWithLifecycle()
        ModuleConfigurationScreen(
            viewModel = radioConfigViewModelProvider(null),
            hiddenFeaturesUnlocked = hiddenFeaturesUnlocked,
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigate = backStack::addSettingsRoute,
        )
    }

    entry<SettingsRoute.Administration> {
        AdministrationScreen(
            viewModel = radioConfigViewModelProvider(null),
            onBack = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.CleanNodeDb> {
        val viewModel: CleanNodeDatabaseViewModel = koinViewModel()
        CleanNodeDatabaseScreen(viewModel = viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
    }

    ConfigRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class, routeInfo, radioConfigViewModelProvider) { viewModel ->
            when (routeInfo) {
                ConfigRoute.USER ->
                    UserConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.CHANNELS ->
                    ChannelConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.DEVICE ->
                    DeviceConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.POSITION ->
                    PositionConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.POWER ->
                    PowerConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.NETWORK ->
                    NetworkConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.DISPLAY ->
                    DisplayConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.LORA ->
                    LoRaConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.BLUETOOTH ->
                    BluetoothConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ConfigRoute.SECURITY ->
                    SecurityConfigScreenCommon(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
            }
        }
    }

    ModuleRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class, routeInfo, radioConfigViewModelProvider) { viewModel ->
            when (routeInfo) {
                ModuleRoute.MQTT ->
                    MQTTConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.SERIAL ->
                    SerialConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.EXT_NOTIFICATION ->
                    ExternalNotificationConfigScreenCommon(
                        viewModel = viewModel,
                        onBack = dropUnlessResumed { backStack.removeLastOrNull() },
                    )

                ModuleRoute.STORE_FORWARD ->
                    StoreForwardConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.RANGE_TEST ->
                    RangeTestConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.TELEMETRY ->
                    TelemetryConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.CANNED_MESSAGE ->
                    CannedMessageConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.AUDIO ->
                    AudioConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.REMOTE_HARDWARE ->
                    RemoteHardwareConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.NEIGHBOR_INFO ->
                    NeighborInfoConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.AMBIENT_LIGHTING ->
                    AmbientLightingConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.DETECTION_SENSOR ->
                    DetectionSensorConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.PAXCOUNTER ->
                    PaxcounterConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.STATUS_MESSAGE ->
                    StatusMessageConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.TAK ->
                    TAKConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })

                ModuleRoute.MESH_BEACON ->
                    MeshBeaconConfigScreen(viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
            }
        }
    }

    entry<SettingsRoute.TakServer> { TakServerScreen(onBack = dropUnlessResumed { backStack.removeLastOrNull() }) }

    entry<SettingsRoute.DebugPanel> {
        val viewModel: DebugViewModel = koinViewModel()
        DebugScreen(viewModel = viewModel, onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() })
    }

    entry<SettingsRoute.About> {
        val settingsViewModel: SettingsViewModel = koinViewModel()
        AboutScreen(
            appVersionName = settingsViewModel.appVersionName,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onNavigateToAcknowledgements = dropUnlessResumed { backStack.add(SettingsRoute.Acknowledgements) },
        )
    }

    entry<SettingsRoute.Acknowledgements> {
        AcknowledgementsScreen(
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            jsonProvider = { getAboutLibrariesJson() },
        )
    }

    entry<SettingsRoute.FilterSettings> {
        val viewModel: FilterSettingsViewModel = koinViewModel()
        FilterSettingsScreen(viewModel = viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
    }

    entry<SettingsRoute.NodeList> {
        val settingsViewModel: SettingsViewModel = koinViewModel()
        NodeListScreen(
            settingsViewModel = settingsViewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoute.DeviceLinks> {
        DeviceLinkDirectoryScreen(
            viewModel = koinViewModel(),
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }
    entry<SettingsRoute.AppFunctionsSettings> {
        val viewModel: AppFunctionsSettingsViewModel = koinViewModel()
        AppFunctionsSettingsScreen(viewModel = viewModel, onBack = dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

/** Expect declaration for the platform-specific settings main screen. */
@Composable
expect fun SettingsMainScreen(
    settingsViewModel: SettingsViewModel,
    radioConfigViewModel: RadioConfigViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigate: (Route) -> Unit,
    onBack: (() -> Unit)? = null,
)

/** Expect declarations for platform-specific config screens. */
fun <R : Route> EntryProviderScope<NavKey>.configComposable(
    route: KClass<R>,
    routeInfo: Enum<*>,
    radioConfigViewModelProvider: @Composable (SettingsRoute.Settings?) -> RadioConfigViewModel,
    content: @Composable (RadioConfigViewModel) -> Unit,
) {
    addEntryProvider(route) {
        val viewModel = radioConfigViewModelProvider(null)
        // Remote settings need a blocking progress overlay from the first frame. Local settings already have their
        // connect-time repository snapshot, so their route refresh stays non-blocking and does not flash a 0% overlay.
        remember { viewModel.ensureLoadingForRemote().let { true } }
        LaunchedEffect(Unit) { viewModel.loadConfigRoute(routeInfo) }
        content(viewModel)
    }
}
