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
package com.geeksville.mesh.ui.connections

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import no.nordicsemi.kotlin.ble.environment.android.compose.LocalEnvironmentOwner
import no.nordicsemi.kotlin.ble.environment.android.mock.MockAndroidEnvironment
import org.jetbrains.compose.resources.getString
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.bluetooth_disabled
import org.meshtastic.core.strings.bluetooth_paired_devices
import org.meshtastic.core.ui.theme.AppTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testBluetoothDisabled_showsWarning() {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = false)

        composeTestRule.setContent {
            @Suppress("SpreadOperator")
            CompositionLocalProvider(*(LocalEnvironmentOwner provides mockEnvironment)) {
                AppTheme {
                    ConnectionsScreen(onClickNodeChip = {}, onNavigateToNodeDetails = {}, onConfigNavigate = {})
                }
            }
        }

        // Nordic's ScannerView should internally check the Provided environment
        // and display the appropriate hardware state warning.
        val bluetoothDisabledText = runBlocking { getString(Res.string.bluetooth_disabled) }
        composeTestRule.onNodeWithText(bluetoothDisabledText).assertIsDisplayed()
    }

    @Test
    fun testBluetoothEnabled_showsPairedDevices() {
        val mockEnvironment = MockAndroidEnvironment.Api31(isBluetoothEnabled = true)

        composeTestRule.setContent {
            @Suppress("SpreadOperator")
            CompositionLocalProvider(*(LocalEnvironmentOwner provides mockEnvironment)) {
                AppTheme {
                    ConnectionsScreen(onClickNodeChip = {}, onNavigateToNodeDetails = {}, onConfigNavigate = {})
                }
            }
        }

        // We expect to see the "Paired devices" header or scanning indicator
        val pairedDevicesText = runBlocking { getString(Res.string.bluetooth_paired_devices) }
        composeTestRule.onNodeWithText(pairedDevicesText).assertIsDisplayed()
    }
}
