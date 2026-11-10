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
import android.content.Context
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The SceneView AAR's manifest marks GLES 3.0 as a required feature and requests VIBRATE. The 3D viewer is optional, so
 * the app manifest neutralizes both; these assertions read the merged manifest and fail if either override is dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SceneViewManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `gles 3 is declared but not required`() {
        val features =
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures
        val gles = assertNotNull(features?.firstOrNull { it.reqGlEsVersion == GLES_3_0 }, "no GLES 3.0 uses-feature")
        assertFalse(
            gles.flags and FeatureInfo.FLAG_REQUIRED != 0,
            "GLES 3.0 is required; the viewer must stay optional",
        )
    }

    @Test
    fun `vibrate is not requested`() {
        val permissions =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
        assertFalse(permissions.orEmpty().contains(android.Manifest.permission.VIBRATE))
    }

    private companion object {
        const val GLES_3_0 = 0x00030000
    }
}
