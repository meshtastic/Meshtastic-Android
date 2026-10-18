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
package org.meshtastic.core.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.no_device_selected

/**
 * Quick Settings tile for the radio link: shows the connection and connects or disconnects the selected radio. An
 * active tile, so it listens only when [QuickSettingsTileController] asks the system to refresh it.
 */
class MeshQuickSettingsTileService :
    TileService(),
    KoinComponent {

    private val controller: QuickSettingsTileController by inject()
    private val scope: CoroutineScope = MainScope()
    private var listenJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        controller.startObserving()
    }

    // An active tile is bound only on request, so one added while the state is steady would stay blank.
    override fun onTileAdded() {
        super.onTileAdded()
        requestListeningState(this, ComponentName(this, MeshQuickSettingsTileService::class.java))
    }

    override fun onStartListening() {
        super.onStartListening()
        listenJob?.cancel()
        listenJob = scope.launch { controller.state.collect { render(it) } }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isSecure && isLocked) unlockAndRun(::handleClick) else handleClick()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleClick() {
        scope.launch { if (controller.onClick()) openConnections() }
    }

    private suspend fun render(state: QuickSettingsTileState) {
        val tile = qsTile ?: return
        tile.state = state.state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                when (val subtitle = state.subtitle) {
                    is QuickSettingsTileSubtitle.NodeName -> subtitle.shortName
                    QuickSettingsTileSubtitle.Connected -> getStringSuspend(Res.string.connected)
                    QuickSettingsTileSubtitle.Connecting -> getStringSuspend(Res.string.connecting)
                    QuickSettingsTileSubtitle.DeviceSleeping -> getStringSuspend(Res.string.device_sleeping)
                    QuickSettingsTileSubtitle.Disconnected -> getStringSuspend(Res.string.disconnected)
                    QuickSettingsTileSubtitle.NoDeviceSelected -> getStringSuspend(Res.string.no_device_selected)
                }
        }
        tile.updateTile()
    }

    private fun openConnections() {
        val intent =
            Intent(Intent.ACTION_VIEW, "$DEEP_LINK_BASE_URI/connections".toUri())
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
