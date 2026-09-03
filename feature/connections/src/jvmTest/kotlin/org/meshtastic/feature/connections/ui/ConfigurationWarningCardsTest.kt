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
package org.meshtastic.feature.connections.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.set_your_region
import org.meshtastic.core.resources.transmit_disabled
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ConfigurationWarningCardsTest {

    @Test
    fun `region card renders when connected policy allows local writes`() = runComposeUiTest {
        setWarningCards(
            lockdownState = LockdownState.Unlocked,
            isManaged = false,
            regionUnset = true,
            txDisabled = true,
        )

        onNodeWithText(getString(Res.string.set_your_region)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.transmit_disabled)).assertDoesNotExist()
    }

    @Test
    fun `transmit card renders only when region is already configured`() = runComposeUiTest {
        setWarningCards(
            lockdownState = LockdownState.Disabled,
            isManaged = false,
            regionUnset = false,
            txDisabled = true,
        )

        onNodeWithText(getString(Res.string.set_your_region)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.transmit_disabled)).assertIsDisplayed()
    }

    @Test
    fun `managed policy suppresses configuration warning cards even after lockdown unlock`() = runComposeUiTest {
        setWarningCards(lockdownState = LockdownState.Unlocked, isManaged = true, regionUnset = true, txDisabled = true)

        onNodeWithText(getString(Res.string.set_your_region)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.transmit_disabled)).assertDoesNotExist()
    }

    @Test
    fun `pending lockdown response suppresses configuration warning cards`() = runComposeUiTest {
        setWarningCards(
            lockdownState = LockdownState.AwaitingResponse,
            isManaged = false,
            regionUnset = true,
            txDisabled = true,
        )

        assertNoWarningCards()
    }

    @Test
    fun `pending node readiness suppresses configuration warning cards`() = runComposeUiTest {
        setWarningCards(
            lockdownState = LockdownState.None,
            isManaged = false,
            regionUnset = true,
            txDisabled = true,
            activeNodeInfoReady = false,
        )

        assertNoWarningCards()
    }

    @Test
    fun `virtual device suppresses configuration warning cards`() = runComposeUiTest {
        setWarningCards(
            lockdownState = LockdownState.None,
            isManaged = false,
            regionUnset = true,
            txDisabled = true,
            isPhysicalDevice = false,
        )

        assertNoWarningCards()
    }

    private fun ComposeUiTest.setWarningCards(
        lockdownState: LockdownState,
        isManaged: Boolean,
        regionUnset: Boolean,
        txDisabled: Boolean,
        activeNodeInfoReady: Boolean = true,
        isPhysicalDevice: Boolean = true,
    ) {
        setContent {
            MaterialTheme {
                ConfigurationWarningCards(
                    connectedWithNode = true,
                    activeNodeInfoReady = activeNodeInfoReady,
                    lockdownState = lockdownState,
                    isManaged = isManaged,
                    isPhysicalDevice = isPhysicalDevice,
                    regionUnset = regionUnset,
                    txDisabled = txDisabled,
                    onConfigNavigate = {},
                )
            }
        }
    }

    private fun ComposeUiTest.assertNoWarningCards() {
        onNodeWithText(getString(Res.string.set_your_region)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.transmit_disabled)).assertDoesNotExist()
    }
}
