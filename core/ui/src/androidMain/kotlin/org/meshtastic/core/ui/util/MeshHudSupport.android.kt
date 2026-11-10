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
package org.meshtastic.core.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberMeshHudSupported(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasOrientation =
            sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
                sensors?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null
        // The view binds the back camera, which FEATURE_CAMERA names; FEATURE_CAMERA_ANY is met by a front one alone.
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA) && hasOrientation
    }
}
