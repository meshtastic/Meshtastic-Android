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
package org.meshtastic.feature.map.hud

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.annotation.Single
import kotlin.math.max
import kotlin.math.sqrt

private const val QUATERNION_SIZE = 4
private const val ROTATION_MATRIX_SIZE = 9

private const val HEADING_ACCURACY_INDEX = 4
private const val NINETY_FIVE_PERCENT_SIGMAS = 1.96

@Single
class AndroidCameraPoseProvider(private val context: Context) : CameraPoseProvider {

    override fun poseUpdates(): Flow<CameraPoseState> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            trySend(CameraPoseState(hasSensor = false))
            awaitClose {}
            return@callbackFlow
        }
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

        val smoothed = FloatArray(QUATERNION_SIZE)
        val sample = FloatArray(QUATERNION_SIZE)
        val matrix = FloatArray(ROTATION_MATRIX_SIZE)
        val filter = OneEuroQuaternionFilter()
        // Some fusions (measured on a Pixel 9 Pro) report a fixed pi/6 here rather than an estimate, so the value is
        // trusted only once it has moved.
        var firstAccuracy: Float? = null
        var accuracyIsLive = false
        // Sensor callbacks get their own thread, away from the main thread that draws the overlay.
        val thread = HandlerThread("mesh-view-pose").apply { start() }

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    event.toQuaternion(sample)
                    filter.filter(sample, event.timestamp, smoothed)
                    SensorManager.getRotationMatrixFromVector(matrix, smoothed)
                    val rotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
                    trySend(
                        CameraPoseState(
                            pose = matrix.toCameraPose(rotation),
                            calibration = event.accuracy,
                            headingAccuracyDeg =
                            event.headingAccuracyRad()?.let { raw ->
                                if (firstAccuracy == null) firstAccuracy = raw
                                if (raw != firstAccuracy) accuracyIsLive = true
                                // The sensor's bound holds 95% of the time; scale it to the one-sigma figure
                                // every other accuracy on screen uses.
                                raw.takeIf { accuracyIsLive }
                                    ?.let { Math.toDegrees(it.toDouble()) / NINETY_FIVE_PERCENT_SIGMAS }
                            },
                        ),
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

        sensorManager.registerListener(
            listener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME,
            Handler(thread.looper),
        )
        awaitClose {
            sensorManager.unregisterListener(listener)
            thread.quitSafely()
        }
    }
}

/** Rotation-vector values as (x, y, z, w); older sensors omit w, which is recovered from the unit norm. */
@Suppress("MagicNumber")
private fun SensorEvent.toQuaternion(out: FloatArray) {
    val x = values[0]
    val y = values[1]
    val z = values[2]
    out[0] = x
    out[1] = y
    out[2] = z
    out[3] = if (values.size > 3) values[3] else sqrt(max(0f, 1f - x * x - y * y - z * z))
}

/** The rotation vector's heading error estimate, which it reports in radians and as -1 when it has none. */
private fun SensorEvent.headingAccuracyRad(): Float? = values.getOrNull(HEADING_ACCURACY_INDEX)?.takeIf { it >= 0f }

/**
 * The matrix maps device coordinates into east-north-up, so its columns are the device axes in that frame. The back
 * camera looks down device -Z, and which device axis is the screen's right depends on the display rotation.
 */
@Suppress("MagicNumber")
private fun FloatArray.toCameraPose(displayRotation: Int): CameraPose {
    val deviceX = Vec3(this[0].toDouble(), this[3].toDouble(), this[6].toDouble())
    val deviceY = Vec3(this[1].toDouble(), this[4].toDouble(), this[7].toDouble())
    val forward = Vec3(-this[2].toDouble(), -this[5].toDouble(), -this[8].toDouble())
    val (right, up) =
        when (displayRotation) {
            Surface.ROTATION_90 -> -deviceY to deviceX
            Surface.ROTATION_180 -> -deviceX to -deviceY
            Surface.ROTATION_270 -> deviceY to -deviceX
            else -> deviceX to deviceY
        }
    return CameraPose(right = right, up = up, forward = forward)
}

private operator fun Vec3.unaryMinus() = Vec3(-x, -y, -z)
