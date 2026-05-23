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
package org.meshtastic.feature.car.alerts

import android.media.AudioManager
import android.media.ToneGenerator
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.feature.car.model.EmergencyAlert

/**
 * Manages emergency alert state for the car display. Observes incoming packets for emergency-priority messages,
 * maintains active alert list, and triggers audio notifications.
 */
@Single
class EmergencyHandler {

    private var scope: CoroutineScope? = null
    private val _activeAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val activeAlerts: StateFlow<List<EmergencyAlert>> = _activeAlerts.asStateFlow()

    private val _latestAlert = MutableStateFlow<EmergencyAlert?>(null)
    val latestAlert: StateFlow<EmergencyAlert?> = _latestAlert.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Logger.e(tag = "EmergencyHandler", throwable = throwable) { "Emergency flow collection failed" }
    }

    fun startCollecting(emergencyFlow: Flow<EmergencyAlert>) {
        scope?.cancel()
        scope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler).also { newScope ->
                newScope.launch {
                    emergencyFlow.collect { alert ->
                        addAlert(alert)
                        _latestAlert.value = alert
                        playEmergencyTone()
                    }
                }
            }
    }

    fun stopCollecting() {
        scope?.cancel()
        scope = null
        toneGenerator?.release()
        toneGenerator = null
    }

    fun dismissAlert(nodeNum: Int) {
        _activeAlerts.value =
            _activeAlerts.value.map { alert -> if (alert.nodeNum == nodeNum) alert.copy(isActive = false) else alert }
    }

    fun clearAll() {
        _activeAlerts.value = emptyList()
    }

    private fun addAlert(alert: EmergencyAlert) {
        val current = _activeAlerts.value.toMutableList()
        // Replace existing alert from same node, or add new
        val existingIndex = current.indexOfFirst { it.nodeNum == alert.nodeNum }
        if (existingIndex >= 0) {
            current[existingIndex] = alert
        } else {
            current.add(0, alert) // newest first
        }
        _activeAlerts.value = current
    }

    @Suppress("TooGenericExceptionCaught") // ToneGenerator may throw various runtime exceptions
    private fun playEmergencyTone() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
        } catch (e: RuntimeException) {
            Logger.w(tag = "EmergencyHandler", throwable = e) { "Emergency tone playback failed" }
        }
    }

    companion object {
        private const val TONE_VOLUME = 80
        private const val TONE_DURATION_MS = 1000
    }
}
