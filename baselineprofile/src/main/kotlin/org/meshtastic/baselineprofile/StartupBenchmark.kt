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

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold-start time with and without the Baseline Profile so the win is quantifiable.
 *
 * Run it with:
 * ```
 * ./gradlew :androidApp:benchmarkGoogleReleaseBaselineProfile
 * ```
 *
 * Compare `startupCompilationNone` vs `startupCompilationBaselineProfiles` in the output: the delta
 * is the startup improvement the shipped profile buys. `Partial(Require)` fails loudly if the
 * profile is missing, so this also guards against a release that silently dropped it.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_APP_ID,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
        ) {
            pressHome()
            startActivityAndWait()
        }

    private companion object {
        const val DEFAULT_APP_ID = "com.geeksville.mesh"
    }
}
