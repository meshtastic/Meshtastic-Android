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

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BundledSqliteGuardTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager

    private fun state(component: ComponentName) = packageManager.getComponentEnabledSetting(component)

    @Test
    fun `a library that fails to link means the device is unsupported`() {
        assertFalse(bundledSqliteLoads { throw UnsatisfiedLinkError("dlopen failed: library not found") })
    }

    @Test
    fun `a library that loads means the device is supported`() {
        assertTrue(bundledSqliteLoads {})
    }

    @Test
    fun `a failure other than linking is not mistaken for an unsupported device`() {
        assertFailsWith<IllegalStateException> { bundledSqliteLoads { throw IllegalStateException("bug") } }
    }

    @Test
    fun `entry points are every receiver and service the app declares and nothing from a library`() {
        val expected = buildSet {
            add("org.meshtastic.core.service.BootCompleteReceiver")
            add("org.meshtastic.core.service.MeshService")
            add("org.meshtastic.core.service.ConversationActionService")
            add("org.meshtastic.core.nfc.MeshtasticHostApduService")
            add("org.meshtastic.feature.widget.LocalStatsWidgetReceiver")
            if (BuildConfig.FLAVOR == "google") add("org.meshtastic.app.ai.appfunctions.MeshtasticAppFunctionService")
        }

        val entryPoints = context.appEntryPoints()

        assertEquals(expected, entryPoints.map { it.className }.toSet())
        assertTrue(entryPoints.all { it.packageName == context.packageName })
    }

    @Test
    fun `library components are not entry points`() {
        assertFalse(isAppEntryPoint("androidx.work.impl.background.systemjob.SystemJobService"))
        assertFalse(isAppEntryPoint("androidx.glance.appwidget.MyPackageReplacedReceiver"))
        assertFalse(isAppEntryPoint("com.google.firebase.provider.FirebaseInitProvider"))
    }

    @Test
    fun `disabling then restoring returns every entry point to its manifest default`() {
        val entryPoints = context.appEntryPoints()

        context.disableAppEntryPoints()
        entryPoints.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(it), it.className) }

        context.restoreAppEntryPoints()
        entryPoints.forEach { assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, state(it), it.className) }
    }

    @Test
    fun `restoring leaves a component someone else enabled as it is`() {
        val meshService = ComponentName(context, "org.meshtastic.core.service.MeshService")
        packageManager.setComponentEnabledSetting(
            meshService,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        context.restoreAppEntryPoints()

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state(meshService))
    }
}
