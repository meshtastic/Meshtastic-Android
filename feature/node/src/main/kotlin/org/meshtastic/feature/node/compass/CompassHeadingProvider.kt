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

package org.meshtastic.feature.node.compass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

private const val ROTATION_MATRIX_SIZE = 9
private const val ORIENTATION_SIZE = 3
private const val DEGREES_IN_CIRCLE = 360f

data class HeadingState(
    val heading: Float? = null,
    val hasSensor: Boolean = true,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
)

class CompassHeadingProvider @Inject constructor(@ApplicationContext private val context: Context) {
    // Emits the current compass heading, preferring rotation vector and falling back to accel+magnetometer.
    fun headingUpdates(): Flow<HeadingState> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            trySend(HeadingState(hasSensor = false))
            close()
            return@callbackFlow
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationSensor == null && (accelerometer == null || magnetometer == null)) {
            trySend(HeadingState(hasSensor = false))
            awaitClose {}
            return@callbackFlow
        }

        val rotationMatrix = FloatArray(ROTATION_MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)
        val accelValues = FloatArray(ORIENTATION_SIZE)
        val magnetValues = FloatArray(ORIENTATION_SIZE)
        var hasAccel = false
        var hasMagnet = false

        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            val heading = (azimuth + DEGREES_IN_CIRCLE) % DEGREES_IN_CIRCLE
                            trySend(HeadingState(heading = heading, hasSensor = true, accuracy = event.accuracy))
                        }

                        Sensor.TYPE_ACCELEROMETER -> {
                            System.arraycopy(event.values, 0, accelValues, 0, accelValues.size)
                            hasAccel = true
                        }

                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            System.arraycopy(event.values, 0, magnetValues, 0, magnetValues.size)
                            hasMagnet = true
                        }
                    }

                    if (rotationSensor == null && hasAccel && hasMagnet) {
                        val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)
                        if (success) {
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            val heading = (azimuth + DEGREES_IN_CIRCLE) % DEGREES_IN_CIRCLE
                            trySend(HeadingState(heading = heading, hasSensor = true, accuracy = event.accuracy))
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // No-op; accuracy is emitted with heading updates.
                }
            }

        rotationSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        if (rotationSensor == null) {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
