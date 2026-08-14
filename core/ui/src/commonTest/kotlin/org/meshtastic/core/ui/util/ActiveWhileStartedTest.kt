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
package org.meshtastic.core.ui.util

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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ActiveWhileStartedTest {

    @Test
    fun effectFollowsLifecycleEnablementRestartKeysAndDisposal() = runComposeUiTest {
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.CREATED)
        val starts = mutableListOf<Pair<String, Int>>()
        val stops = mutableListOf<Pair<String, Int>>()
        var enabled by mutableStateOf(true)
        var restartKey by mutableStateOf("first")
        var effectVersion by mutableStateOf(1)
        var composed by mutableStateOf(true)

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (composed) {
                    ActiveWhileStarted(restartKey, enabled = enabled) {
                        val startedKey = restartKey
                        val startedVersion = effectVersion
                        starts += startedKey to startedVersion
                        { stops += startedKey to startedVersion }
                    }
                }
            }
        }

        assertEquals(emptyList(), starts, "CREATED must not start the effect")
        assertEquals(emptyList(), stops)

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(listOf("first" to 1), starts)

        runOnIdle { effectVersion = 2 }
        waitForIdle()
        assertEquals(listOf("first" to 1), starts, "updating the callback alone must not restart the effect")

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        waitForIdle()
        assertEquals(listOf("first" to 1), stops, "ON_STOP must invoke the active cleanup callback")

        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(listOf("first" to 1, "first" to 2), starts, "resume must use the latest effect callback")

        runOnIdle { restartKey = "second" }
        waitForIdle()
        assertEquals(listOf("first" to 1, "first" to 2), stops)
        assertEquals(listOf("first" to 1, "first" to 2, "second" to 2), starts)

        runOnIdle { enabled = false }
        waitForIdle()
        assertEquals(listOf("first" to 1, "first" to 2, "second" to 2), stops)

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(3, starts.size, "a disabled effect must remain stopped across lifecycle changes")
        assertEquals(3, stops.size)

        runOnIdle { enabled = true }
        waitForIdle()
        assertEquals(listOf("first" to 1, "first" to 2, "second" to 2, "second" to 2), starts)

        runOnIdle { composed = false }
        waitForIdle()
        assertEquals(listOf("first" to 1, "first" to 2, "second" to 2, "second" to 2), stops)

        lifecycleOwner.moveTo(Lifecycle.State.CREATED)
        lifecycleOwner.moveTo(Lifecycle.State.STARTED)
        waitForIdle()
        assertEquals(4, starts.size, "a disposed effect must not restart")
        assertEquals(4, stops.size, "disposal must not double-clean")
    }

    private class TestLifecycleOwner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle: LifecycleRegistry =
            LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

        fun moveTo(state: Lifecycle.State) {
            lifecycle.currentState = state
        }
    }
}
