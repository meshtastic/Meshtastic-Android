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

import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.encodeToString
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SecurityConfigScreenTest {
    @Test
    fun `delayed remote public key updates display and copy without recreating screen`() = runComposeUiTest {
        var securityConfig by mutableStateOf(Config.SecurityConfig())
        var renderedFormState: ConfigState<Config.SecurityConfig>? = null
        var copiedPublicKey: ByteString? = null

        setContent {
            AppTheme {
                val formState = rememberConfigState(securityConfig)
                renderedFormState = formState
                SecurityPublicKeyPreference(
                    securityConfig = securityConfig,
                    formState = formState,
                    enabled = true,
                    publicKeyCopyButton = { publicKey ->
                        IconButton(
                            modifier = Modifier.testTag(SECURITY_PUBLIC_KEY_COPY_TEST_TAG),
                            onClick = { copiedPublicKey = publicKey },
                        ) {}
                    },
                )
            }
        }

        val publicKey = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val encodedPublicKey = publicKey.encodeToString()
        onNodeWithText(encodedPublicKey).assertDoesNotExist()

        // Firmware 2.8 redacts the remote private key, so only the public key changes when the delayed response lands.
        securityConfig = Config.SecurityConfig(public_key = publicKey)
        waitForIdle()

        onNodeWithText(encodedPublicKey).assertIsDisplayed()
        onNodeWithTag(SECURITY_PUBLIC_KEY_COPY_TEST_TAG).performClick()
        assertEquals(publicKey, copiedPublicKey)
        runOnIdle { assertEquals(ByteString.EMPTY, checkNotNull(renderedFormState).value.private_key) }

        // A local private-key edit invalidates the device-derived public key for both render and copy.
        runOnIdle {
            val formState = checkNotNull(renderedFormState)
            formState.value = formState.value.copy(private_key = ByteArray(32) { 7 }.toByteString())
        }
        waitForIdle()

        onNodeWithText(encodedPublicKey).assertDoesNotExist()
        copiedPublicKey = null
        onNodeWithTag(SECURITY_PUBLIC_KEY_COPY_TEST_TAG).performClick()
        assertEquals(ByteString.EMPTY, copiedPublicKey)
    }

    @Test
    fun `private key is redacted for a remote node until a new one is entered`() {
        val remote = Config.SecurityConfig(public_key = ByteArray(32) { 1 }.toByteString())
        assertTrue(isPrivateKeyRedacted(remote, isLocal = false))

        // The local node always reports its own key, so nothing is withheld there.
        val local = remote.copy(private_key = ByteArray(32) { 2 }.toByteString())
        assertFalse(isPrivateKeyRedacted(local, isLocal = true))
        assertFalse(isPrivateKeyRedacted(remote, isLocal = true))

        // Older firmware that still returns the remote key has nothing left to redact.
        assertFalse(isPrivateKeyRedacted(local, isLocal = false))
    }
}
