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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.koin.core.annotation.Single

/** API level at which ACCESS_LOCAL_NETWORK became a real runtime permission (Android 17 / API 37). */
private const val LOCAL_NETWORK_PERMISSION_API = 37

@Single
class AndroidLocalNetworkAccess(private val context: Context) : LocalNetworkAccess {
    override fun isGranted(): Boolean =
        // Below API 37 local-network access is implicit via INTERNET. The grant is keyed on targetSdk rather than on
        // install history, so an existing install loses it as soon as it takes an update built against 37.
        Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_API ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
}
