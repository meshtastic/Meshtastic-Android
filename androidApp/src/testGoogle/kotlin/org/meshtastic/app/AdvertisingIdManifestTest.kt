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
package org.meshtastic.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Play's advertising ID declaration can say "No" only while the merged manifest requests none of the permissions that
 * Firebase Analytics' measurement libraries declare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AdvertisingIdManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the merged manifest requests no advertising ID or ad services permission`() {
        // Resolving a measurement component proves the libraries that declare these permissions were merged.
        context.packageManager.getServiceInfo(ComponentName(context, MEASUREMENT_SERVICE), 0)

        val requested =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                .orEmpty()
                .toSet()

        assertEquals(emptySet(), AD_PERMISSIONS intersect requested)
    }

    private companion object {
        const val MEASUREMENT_SERVICE = "com.google.android.gms.measurement.AppMeasurementService"

        val AD_PERMISSIONS =
            setOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            )
    }
}
