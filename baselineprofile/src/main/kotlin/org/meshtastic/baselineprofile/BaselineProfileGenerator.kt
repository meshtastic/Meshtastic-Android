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
package org.meshtastic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the app's critical user journey.
 *
 * Run it with:
 * ```
 * ./gradlew :androidApp:generateGoogleReleaseBaselineProfile
 * ```
 *
 * The [androidx.baselineprofile] plugin on `:androidApp` drives this against the auto-created
 * `nonMinifiedRelease` variant and merges the result into
 * `androidApp/src/google/generated/baselineProfiles/`. Commit that output so release builds ship it.
 *
 * The journey is intentionally minimal (cold start → first frame) because CI has no paired radio.
 * Extend it with post-connection screens (node list, map, message thread) once a fake transport or
 * connected device is available in the harness — the more representative the journey, the better the
 * profile.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            // The plugin injects the target applicationId (handles the google debug/release suffix).
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_APP_ID,
            // Also produce a startup profile (dexlayout hints) for faster cold start, not just AOT rules.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }

    private companion object {
        const val DEFAULT_APP_ID = "com.geeksville.mesh"
    }
}
