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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs against the merged manifest of the variant under test, library components included, so it catches an AAR that
 * declares an exported activity whose class the app does not ship. Another app can launch such a component, and the
 * launch crashes the process on instantiation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ExportedActivityManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every exported activity resolves to a class on the classpath`() {
        val exported =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities
                .orEmpty()
                .filter { it.exported }
                .map { it.targetActivity ?: it.name }

        assertTrue(MAIN_ACTIVITY in exported, "Merged manifest under test has no exported MainActivity: $exported")

        val missing = exported.filterNot { it.isLoadable() }
        assertEquals(emptyList(), missing, "Exported activities with no class in the app: $missing")
    }

    @Test
    fun `jetbrains ui-tooling preview activity is absent from the merged manifest`() {
        assertFailsWith<PackageManager.NameNotFoundException>(
            "$JETBRAINS_PREVIEW_ACTIVITY is declared in the merged manifest. Restore its tools:node=\"remove\" " +
                "entry in androidApp/src/main/AndroidManifest.xml.",
        ) {
            context.packageManager.getActivityInfo(ComponentName(context, JETBRAINS_PREVIEW_ACTIVITY), 0)
        }
    }

    private fun String.isLoadable(): Boolean = try {
        Class.forName(this, false, context.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    private companion object {
        const val MAIN_ACTIVITY = "org.meshtastic.app.MainActivity"
        const val JETBRAINS_PREVIEW_ACTIVITY = "org.jetbrains.androidx.compose.ui.tooling.PreviewActivity"
    }
}
