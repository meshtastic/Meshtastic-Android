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

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Suspend until the phone's significant-motion sensor fires, and return true then; return false at once when the phone
 * has no such sensor or will not arm it, since nothing would wake a sleeping GPS. The sensor fires once per arming.
 */
internal suspend fun awaitSignificantMotion(context: Context): Boolean {
    val sensors = context.getSystemService(SensorManager::class.java)
    val sensor = sensors?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return false
    return suspendCancellableCoroutine { continuation ->
        val trigger =
            object : TriggerEventListener() {
                override fun onTrigger(event: TriggerEvent?) {
                    if (continuation.isActive) continuation.resume(true)
                }
            }
        if (!sensors.requestTriggerSensor(trigger, sensor)) {
            continuation.resume(false)
        } else {
            continuation.invokeOnCancellation { sensors.cancelTriggerSensor(trigger, sensor) }
        }
    }
}
