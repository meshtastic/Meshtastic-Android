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
package org.meshtastic.app.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapLifecycleEffectsTest {

    @Test
    fun activeEffectStopsInBackgroundAndResumesOnlyWhenEnabled() = runComposeUiTest {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.CREATED)
        var enabled by mutableStateOf(true)
        var composed by mutableStateOf(true)
        var starts = 0
        var stops = 0

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (composed) {
                    ActiveWhileStarted(enabled) {
                        starts += 1
                        { stops += 1 }
                    }
                }
            }
        }

        assertEquals(0, starts)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(1, starts)

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        waitForIdle()
        assertEquals(1, stops, "ON_STOP must synchronously release active map work")

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(2, starts, "ON_START must resume an enabled map tracker")

        runOnIdle { enabled = false }
        waitForIdle()
        assertEquals(2, stops, "disabling tracking must release active work")

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(2, starts, "a disabled tracker must remain stopped after resume")

        runOnIdle { enabled = true }
        waitForIdle()
        assertEquals(3, starts)

        runOnIdle { composed = false }
        waitForIdle()
        assertEquals(3, stops, "composition disposal must release active map work")
    }

    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

        fun moveTo(state: Lifecycle.State) {
            lifecycle.currentState = state
        }
    }
}
