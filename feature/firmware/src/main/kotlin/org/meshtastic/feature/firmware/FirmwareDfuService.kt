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

package org.meshtastic.feature.firmware

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import no.nordicsemi.android.dfu.DfuBaseService
import org.meshtastic.core.model.BuildConfig

class FirmwareDfuService : DfuBaseService() {
    override fun onCreate() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(NOTIFICATION_CHANNEL_DFU, "Firmware Update", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Firmware update status"
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
        super.onCreate()
    }

    override fun getNotificationTarget(): Class<out Activity>? = try {
        // Best effort to find the main activity
        @Suppress("UNCHECKED_CAST")
        Class.forName("com.geeksville.mesh.MainActivity") as Class<out Activity>
    } catch (_: ClassNotFoundException) {
        null
    }

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
