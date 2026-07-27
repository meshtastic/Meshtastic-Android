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
package org.meshtastic.app.widget

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Guards the `tools:node="remove"` of `androidx.glance.appwidget.action.ActionTrampolineActivity` in
 * `androidApp/src/main/AndroidManifest.xml`.
 *
 * That activity's `onCreate` unconditionally `require()`s an `"ACTION_INTENT"` Intent extra, so any launch without it
 * is a FATAL `IllegalArgumentException` ("List adapter activity trampoline invoked without specifying target intent")
 * raised before any of our code runs. Nothing can supply the extra, because nothing in Glance ever creates the intent:
 * `applyTrampolineIntent()` selects this component only for `ActionTrampolineType.ACTIVITY`, and no call site passes
 * `ACTIVITY` — the lazy-collection call sites all pass `BROADCAST`/`SERVICE`/`FOREGROUND_SERVICE`, which route to
 * [androidx.glance.appwidget.action.InvisibleActionTrampolineActivity] instead. It is dead-but-launchable library code,
 * so we take it out of the merged manifest entirely.
 *
 * These assertions run against the *merged* manifest (Robolectric reads it, which is why the AAR-declared invisible
 * trampoline resolves), so they fail if the removal is dropped or if a Glance bump reshuffles these components.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class GlanceTrampolineManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `crash-on-launch activity trampoline is absent from the merged manifest`() {
        assertFailsWith<PackageManager.NameNotFoundException>(
            "$ACTION_TRAMPOLINE is declared in the merged manifest. Glance never targets it, and its onCreate " +
                "require()s an ACTION_INTENT extra, so every launch is a FATAL IllegalArgumentException. Restore " +
                "the tools:node=\"remove\" entry in androidApp/src/main/AndroidManifest.xml.",
        ) {
            context.packageManager.getActivityInfo(ComponentName(context, ACTION_TRAMPOLINE), 0)
        }
    }

    @Test
    fun `invisible trampoline Glance actually uses is still declared and not exported`() {
        // Positive control: proves the merged manifest under test really does include glance-appwidget's own
        // components, so the assertion above is a genuine absence rather than a manifest that was never merged.
        val activityInfo = context.packageManager.getActivityInfo(ComponentName(context, INVISIBLE_TRAMPOLINE), 0)

        assertFalse(
            activityInfo.exported,
            "$INVISIBLE_TRAMPOLINE must stay unexported — it launches intents handed to it verbatim.",
        )
    }

    private companion object {
        const val ACTION_TRAMPOLINE = "androidx.glance.appwidget.action.ActionTrampolineActivity"
        const val INVISIBLE_TRAMPOLINE = "androidx.glance.appwidget.action.InvisibleActionTrampolineActivity"
    }
}
