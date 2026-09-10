/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.ntsocial.meshlink.core.ui.util.ChannelsOnlyBarcodeScanner
import com.ntsocial.meshlink.core.ui.util.LocalBarcodeScannerProvider
import com.ntsocial.meshlink.core.ui.util.LocalBarcodeScannerSupported
import com.ntsocial.meshlink.core.ui.util.LocalNfcScannerSupported
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ImportFabUiTest {

    @Test
    fun importFab_expands_onButtonClick_whenSupported() = runComposeUiTest {
        val testTag = "import_fab"
        setContent {
            CompositionLocalProvider(
                LocalBarcodeScannerSupported provides true,
                LocalNfcScannerSupported provides true,
            ) {
                MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = testTag)
            }
        }

        // Expand the FAB
        onNodeWithTag(testTag).performClick()

        // Verify menu items are visible using their tags
        onNodeWithTag("nfc_import").assertIsDisplayed()
        onNodeWithTag("qr_import").assertIsDisplayed()
        onNodeWithTag("url_import").assertIsDisplayed()
    }

    @Test
    fun importFab_hidesNfcAndQr_whenNotSupported() = runComposeUiTest {
        val testTag = "import_fab"
        setContent {
            CompositionLocalProvider(
                LocalBarcodeScannerSupported provides false,
                LocalNfcScannerSupported provides false,
            ) {
                MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = testTag)
            }
        }

        // Expand the FAB
        onNodeWithTag(testTag).performClick()

        // Verify menu items are visible using their tags
        onNodeWithTag("nfc_import").assertDoesNotExist()
        onNodeWithTag("qr_import").assertDoesNotExist()
        onNodeWithTag("url_import").assertIsDisplayed()
    }

    @Test
    fun importFab_doesNotExposeChannelsOnlyScannerToGeneralImports() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalBarcodeScannerProvider provides
                    {
                        object : ChannelsOnlyBarcodeScanner {
                            override val isSupported = true

                            override fun startChannelScan() =
                                error("General import must not start a Channels-only scan")
                        }
                    },
            ) {
                MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = "import_fab")
            }
        }
        onNodeWithTag("import_fab").performClick()
        onNodeWithTag("qr_import").assertDoesNotExist()
        onNodeWithTag("url_import").assertIsDisplayed()
    }

    @Test
    fun importFab_showsUrlDialog_whenUrlItemClicked() = runComposeUiTest {
        val testTag = "import_fab"
        setContent { MeshtasticImportFAB(onImport = {}, isContactContext = true, testTag = testTag) }

        onNodeWithTag(testTag).performClick()
        onNodeWithTag("url_import").performClick()

        // The URL dialog should be shown.
        // We'll search for its title indirectly or check if an AlertDialog appeared.
    }

    @Test
    fun importFab_showsShareChannels_whenCallbackProvided() = runComposeUiTest {
        val testTag = "import_fab"
        setContent {
            MeshtasticImportFAB(onImport = {}, onShareChannels = {}, isContactContext = false, testTag = testTag)
        }

        onNodeWithTag(testTag).performClick()
        onNodeWithTag("share_channels").assertIsDisplayed()
    }

    @Test
    fun importFab_showsSharedContactDialog_whenProvided() = runComposeUiTest {
        val contact = SharedContact(user = User(long_name = "Suzume Goddess"), node_num = 1)
        setContent {
            MeshtasticImportFAB(
                onImport = {},
                sharedContact = contact,
                onDismissSharedContact = {},
                importDialog = { shared, _ -> Text(text = "Importing ${shared.user?.long_name}") },
            )
        }

        // Check if goddess is here
        onNodeWithText("Importing Suzume Goddess").assertIsDisplayed()
    }
}
