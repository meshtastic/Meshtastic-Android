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
package org.meshtastic.feature.car.service

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.feature.car.alerts.EmergencyHandler
import org.meshtastic.feature.car.screens.HomeScreen
import org.meshtastic.feature.car.util.CrashlyticsCarTagger

class MeshtasticCarSession :
    Session(),
    KoinComponent {

    private val crashlyticsCarTagger: CrashlyticsCarTagger by inject()
    private val stateCoordinator: CarStateCoordinator by inject()
    private val emergencyHandler: EmergencyHandler by inject()

    override fun onCreateScreen(intent: Intent): Screen {
        crashlyticsCarTagger.setCarSession(true)
        stateCoordinator.start()
        emergencyHandler.startCollecting(stateCoordinator.emergencyAlerts)

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    destroy()
                }
            },
        )

        return HomeScreen(carContext, stateCoordinator, emergencyHandler)
    }

    override fun onNewIntent(intent: Intent) {
        // Deep link handling (e.g., open specific conversation from notification)
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        // Handle theme/density changes — templates auto-update
    }

    private fun destroy() {
        emergencyHandler.stopCollecting()
        stateCoordinator.destroy()
        crashlyticsCarTagger.setCarSession(false)
    }
}
