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
package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeApplicationCoroutineScope
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionViewModel
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the device choice and confirmation steps put on screen and expose to a user or a screen reader. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class WifiProvisionScreenTest {

    private val named = DiscoveredDevice(name = "mpwrd-nm-A1B2", address = "aa:bb:cc:dd:ee:01", rssi = -52)
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a device row is a button that shows its name and the address in capitals when the address is a MAC`() =
        runComposeUiTest {
            var clicks = 0
            setContent {
                AppTheme { DeviceRow(device = named, showAddress = true, onClick = { clicks++ }) }
            }

            onNodeWithText("mpwrd-nm-A1B2").assertIsDisplayed().assertHasClickAction()
            onNodeWithText("AA:BB:CC:DD:EE:01").assertIsDisplayed()
            onNodeWithText("mpwrd-nm-A1B2")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            assertEquals(1, clicks)
        }

    @Test
    fun `a device row does not show an address the user cannot check`() = runComposeUiTest {
        setContent { AppTheme { DeviceRow(device = named, showAddress = false, onClick = {}) } }

        onNodeWithText("mpwrd-nm-A1B2").assertIsDisplayed()
        onNodeWithText("AA:BB:CC:DD:EE:01").assertDoesNotExist()
    }

    @Test
    fun `a device row shows the signal as visible dBm text and describes it to a screen reader`() = runComposeUiTest {
        setContent { AppTheme { DeviceRow(device = named, showAddress = true, onClick = {}) } }

        onNodeWithText("-52 dBm", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Signal strength -52 dBm", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a device row with no signal reading shows no signal text`() = runComposeUiTest {
        setContent {
            AppTheme { DeviceRow(device = named.copy(rssi = null), showAddress = true, onClick = {}) }
        }

        onNodeWithText("dBm", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the confirmation names the device and offers one Connect and one Cancel`() = runComposeUiTest {
        var connects = 0
        var cancels = 0
        setContent {
            AppTheme {
                ConfirmDeviceContent(
                    address = "aa:bb:cc:dd:ee:01",
                    name = "mpwrd-nm-A1B2",
                    onConnect = { connects++ },
                    onCancel = { cancels++ },
                )
            }
        }

        onNodeWithText("Set up Wi-Fi on mpwrd-nm-A1B2?").assertIsDisplayed()
        onNodeWithText("AA:BB:CC:DD:EE:01").assertIsDisplayed()

        onNodeWithText("Connect").performClick()
        assertEquals(1, connects)
        assertEquals(0, cancels)

        onNodeWithText("Cancel").performClick()
        assertEquals(1, connects)
        assertEquals(1, cancels)
    }

    @Test
    fun `the confirmation of an unnamed device does not print a placeholder name in its title`() = runComposeUiTest {
        setContent {
            AppTheme {
                ConfirmDeviceContent(address = "aa:bb:cc:dd:ee:01", name = null, onConnect = {}, onCancel = {})
            }
        }

        onNodeWithText("Set up Wi-Fi on this device?").assertIsDisplayed()
    }

    @Test
    fun `the explanatory text keeps one readable width in a wide window`() = runComposeUiTest {
        setContent {
            AppTheme {
                Box(Modifier.requiredWidth(1200.dp)) {
                    WifiProvisionScreenContent(
                        uiState = WifiProvisionUiState(phase = Phase.DeviceList, devices = listOf(named)),
                        picksDevice = false,
                        snackbarHostState = remember { SnackbarHostState() },
                        actions = WifiProvisionActions.None,
                    )
                }
            }
        }

        val width =
            onNodeWithText("Choose the device to set up", substring = true).getBoundsInRoot().let { it.right - it.left }
        assertTrue(width <= 560.dp, "text spans $width in a 1200.dp window")
    }

    @Test
    fun `opening the screen with a link address connects nothing until Connect is tapped`() = runComposeUiTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        scanner.emitDevice(FakeBleDevice("00:00:00:00:00:0C", name = "chosen"))
        val viewModel =
            WifiProvisionViewModel(
                bleScanner = scanner,
                bleConnectionFactory = FakeBleConnectionFactory(connection),
                dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                applicationScope = FakeApplicationCoroutineScope(dispatcher),
            )

        setContent {
            AppTheme { WifiProvisionScreen(onNavigateUp = {}, address = "00:00:00:00:00:0C", viewModel = viewModel) }
        }
        waitForIdle()

        onNodeWithText("Set up Wi-Fi on chosen?").assertIsDisplayed()
        assertEquals(0, connection.connectAndAwaitCalls)

        onNodeWithText("Connect").performClick()
        waitForIdle()

        assertEquals(1, connection.connectAndAwaitCalls)
        assertEquals("00:00:00:00:00:0C", connection.device?.address)
    }
}
