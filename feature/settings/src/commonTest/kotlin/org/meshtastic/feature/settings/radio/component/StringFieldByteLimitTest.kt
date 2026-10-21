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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Firmware keeps these strings in fixed nanopb buffers whose `max_size` counts the NUL terminator, so the field takes
 * `max_size - 1` bytes and refuses the next one.
 */
@OptIn(ExperimentalTestApi::class)
class StringFieldByteLimitTest {

    @Test
    fun `MQTT password stops at 31 bytes because password max_size is 32`() = runComposeUiTest {
        var stored by mutableStateOf("")
        setContent {
            AppTheme { MqttPasswordPreference(enabled = true, password = stored, onValueChange = { stored = it }) }
        }

        onNode(hasSetTextAction()).performTextInput("a".repeat(31))
        waitForIdle()
        assertEquals(31, stored.length)

        onNode(hasSetTextAction()).performTextInput("a")
        waitForIdle()
        assertEquals(31, stored.length)
    }

    @Test
    fun `MQTT password counts UTF-8 bytes rather than characters`() = runComposeUiTest {
        var stored by mutableStateOf("")
        setContent {
            AppTheme { MqttPasswordPreference(enabled = true, password = stored, onValueChange = { stored = it }) }
        }

        onNode(hasSetTextAction()).performTextInput("é".repeat(15) + "a")
        waitForIdle()
        assertEquals(31, stored.encodeToByteArray().size)

        onNode(hasSetTextAction()).performTextInput("é")
        waitForIdle()
        assertEquals(31, stored.encodeToByteArray().size)
        assertEquals(16, stored.length)
    }

    @Test
    fun `canned message input source stops at 15 bytes because allow_input_source max_size is 16`() = runComposeUiTest {
        var stored by mutableStateOf("")
        setContent {
            AppTheme { AllowInputSourcePreference(enabled = true, value = stored, onValueChange = { stored = it }) }
        }

        onNode(hasSetTextAction()).performTextInput("a".repeat(15))
        waitForIdle()
        assertEquals(15, stored.length)

        onNode(hasSetTextAction()).performTextInput("a")
        waitForIdle()
        assertEquals(15, stored.length)
    }
}
