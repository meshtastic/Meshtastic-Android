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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PositionPrecisionPreferenceUiTest {
    private val changes = mutableListOf<Int>()

    // Inspection mode stands in for the Koin-provided units, as it does in previews.
    private fun ComposeUiTest.show(value: Int, usesPublicKey: Boolean) = setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PositionPrecisionPreference(
                value = value,
                enabled = true,
                onValueChanged = { changes += it },
                usesPublicKey = usesPublicKey,
            )
        }
    }

    @Test
    fun preciseLocationIsLockedOffOnAPublicKey() = runComposeUiTest {
        show(value = 32, usesPublicKey = true)

        onNodeWithText("Precise location").assertIsOff().assertIsNotEnabled()
        onNodeWithText("not securely encrypted", substring = true).assertIsDisplayed()
        // 15 bits, the firmware ceiling, is a 729 m radius.
        onNodeWithText("729", substring = true).assertIsDisplayed()
    }

    @Test
    fun preciseLocationStaysAvailableOnAPrivateKey() = runComposeUiTest {
        show(value = 32, usesPublicKey = false)

        onNodeWithText("Precise location").assertIsOn().assertIsEnabled()
    }

    @Test
    fun enablingPositionOnAPublicKeyStartsApproximate() = runComposeUiTest {
        show(value = 0, usesPublicKey = true)

        onNodeWithText("Position enabled").performClick()

        assertEquals(listOf(13), changes)
    }

    @Test
    fun enablingPositionOnAPrivateKeyStartsPrecise() = runComposeUiTest {
        show(value = 0, usesPublicKey = false)

        onNodeWithText("Position enabled").performClick()

        assertEquals(listOf(32), changes)
    }
}
