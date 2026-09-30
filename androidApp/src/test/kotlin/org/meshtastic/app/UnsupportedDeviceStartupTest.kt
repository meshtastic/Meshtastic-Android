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

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(application = UnsupportedDeviceApplication::class, sdk = [34])
class UnsupportedDeviceStartupTest {

    private val application = ApplicationProvider.getApplicationContext<MeshUtilApplication>()

    @Test
    fun `a device that cannot load SQLite starts no Koin and stops every entry point`() {
        assertFalse(application.isSupportedDevice)
        assertNull(GlobalContext.getOrNull())
        application.appEntryPoints().forEach {
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                application.packageManager.getComponentEnabledSetting(it),
                it.className,
            )
        }
    }

    @Test
    fun `MainActivity opens on an unsupported device without reaching Koin`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertFalse(activity.isFinishing)
        assertNull(GlobalContext.getOrNull())
    }
}

/** Must not be private: Robolectric instantiates the `@Config` application reflectively. */
internal class UnsupportedDeviceApplication : MeshUtilApplication() {
    override fun loadBundledSqlite() =
        throw UnsatisfiedLinkError("dlopen failed: library \"libsqliteJni.so\" not found")
}
