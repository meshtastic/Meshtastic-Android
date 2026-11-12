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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi

/**
 * Save and restore `rememberSaveable` state the way a configuration change does: save the registry, dispose the
 * content, and compose it again from the saved values. Stands in for `StateRestorationTester`, whose skiko actual is
 * unimplemented (CMP-7992). Values pass in memory, so this does not prove they survive an Android `Bundle`.
 */
@OptIn(ExperimentalTestApi::class)
internal class SaveRestoreHarness {
    private var registry: SaveableStateRegistry? = null
    private var saved: Map<String, List<Any?>>? = null
    private var visible by mutableStateOf(true)

    /** Call once inside `setContent`; the single call site keeps `rememberSaveable` keys stable across the cycle. */
    @Composable
    fun Content(content: @Composable () -> Unit) {
        if (visible) {
            val scoped = remember { SaveableStateRegistry(saved) { true } }
            registry = scoped
            CompositionLocalProvider(LocalSaveableStateRegistry provides scoped) { content() }
        }
    }

    fun saveAndRestore(test: ComposeUiTest) {
        test.runOnIdle {
            saved = checkNotNull(registry) { "Content was never composed" }.performSave()
            visible = false
        }
        test.runOnIdle { visible = true }
        test.waitForIdle()
    }
}
