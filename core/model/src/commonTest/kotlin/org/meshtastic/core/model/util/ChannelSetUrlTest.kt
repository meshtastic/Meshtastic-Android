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
package org.meshtastic.core.model.util

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ChannelSetUrlTest {

    @Test
    fun `legacy fragment add parameter remains add mode alongside other parameters`() {
        val channelSet = CommonUri.parse("$REPLACE_CHANNEL_URL?source=ios&add=true").toChannelSet()

        assertEquals("Custom", channelSet.primaryChannel?.name)
        assertFalse(channelSet.hasLoraConfig())
    }

    @Test
    fun `host and path match case insensitively`() {
        for (url in listOf("HTTPS://MESHTASTIC.ORG/E/#$PAYLOAD", "HTTPS://mEsHtAsTiC.OrG/e/#$PAYLOAD")) {
            assertEquals("LongFast", CommonUri.parse(url).toChannelSet().primaryChannel?.name, url)
        }
    }

    @Test
    fun `www host is accepted`() {
        val channelSet = CommonUri.parse("https://www.meshtastic.org/e/#$PAYLOAD").toChannelSet()

        assertEquals("LongFast", channelSet.primaryChannel?.name)
    }

    @Test
    fun `short and legacy channel paths are accepted`() {
        for (url in listOf("https://meshtastic.org/e#$PAYLOAD", "https://meshtastic.org/channel/e/#$PAYLOAD")) {
            assertEquals("LongFast", CommonUri.parse(url).toChannelSet().primaryChannel?.name, url)
        }
    }

    @Test
    fun `add parameter in the fragment drops the LoRa config`() {
        val channelSet = CommonUri.parse("$REPLACE_CHANNEL_URL?add=true").toChannelSet()

        assertEquals("Custom", channelSet.primaryChannel?.name)
        assertFalse(channelSet.hasLoraConfig())
    }

    @Test
    fun `add parameter in the query drops the LoRa config`() {
        val channelSet = CommonUri.parse("https://meshtastic.org/e/?add=true#$PAYLOAD").toChannelSet()

        assertEquals("Custom", channelSet.primaryChannel?.name)
        assertFalse(channelSet.hasLoraConfig())
    }

    @Test
    fun `a fragment that is not base64 is rejected`() {
        assertFailsWith<MalformedMeshtasticUrlException> {
            CommonUri.parse("https://meshtastic.org/e/#!!!!").toChannelSet()
        }
    }

    @Test
    fun `base64 that is not a channel set is rejected`() {
        // 0x0A 0xFF: field 1 claims 255 bytes and none follow, so the protobuf is truncated.
        assertFailsWith<MalformedMeshtasticUrlException> {
            CommonUri.parse("https://meshtastic.org/e/#Cv8=").toChannelSet()
        }
    }

    @Test
    fun `a link without a channel set is rejected`() {
        for (url in listOf("https://meshtastic.org/e/", "https://meshtastic.org/e/#")) {
            assertFailsWith<MalformedMeshtasticUrlException>(url) { CommonUri.parse(url).toChannelSet() }
        }
    }

    @Test
    fun `a channel set on another host or path is rejected`() {
        for (url in listOf("https://example.com/e/#$PAYLOAD", "https://meshtastic.org/v/#$PAYLOAD")) {
            assertFailsWith<MalformedMeshtasticUrlException>(url) { CommonUri.parse(url).toChannelSet() }
        }
    }

    @Test
    fun `all supported channel counts preserve settings for replace and add`() {
        for (channelCount in 1..8) {
            val original = channelSet(channelCount)
            val replaceUrl = original.getChannelUrl()
            val addUrl = original.getChannelUrl(shouldAdd = true)
            val replace = replaceUrl.toChannelSet()
            val add = addUrl.toChannelSet()
            val replacePayload = replaceUrl.encodedChannelSet()
            val addPayload = addUrl.encodedChannelSet()

            assertEquals(original.settings, replacePayload.settings, "$channelCount-channel replace payload settings")
            assertEquals(
                original.lora_config,
                replacePayload.lora_config,
                "$channelCount-channel replace payload LoRa config",
            )
            assertEquals(original.settings, replace.settings, "$channelCount-channel replace settings")
            assertEquals(original.lora_config, replace.lora_config, "$channelCount-channel replace LoRa config")
            assertEquals(original.settings, addPayload.settings, "$channelCount-channel add payload settings")
            assertNull(addPayload.lora_config, "$channelCount-channel add payload must omit LoRa config")
            assertEquals(original.settings, add.settings, "$channelCount-channel add settings")
            assertNull(add.lora_config, "$channelCount-channel add must not retune")
        }
    }

    private fun CommonUri.encodedChannelSet(): ChannelSet {
        val base64 = requireNotNull(fragment).substringBefore('?').replace('-', '+').replace('_', '/')
        val bytes = requireNotNull(base64.decodeBase64())
        return ChannelSet.ADAPTER.decode(bytes)
    }

    private fun channelSet(channelCount: Int): ChannelSet = ChannelSet.Builder()
        .also { wb ->
            wb.settings =
                (0 until channelCount).map { index ->
                    ChannelSettings.Builder()
                        .also { wb ->
                            wb.name = "Conference-$index"
                            wb.psk = byteArrayOf(index.toByte(), (index + 16).toByte()).toByteString()
                            wb.module_settings =
                                ModuleSettings.Builder().also { wb -> wb.position_precision = index }.build()
                        }
                        .build()
                }
            wb.lora_config =
                LoRaConfig.Builder()
                    .also { wb ->
                        wb.use_preset = true
                        wb.modem_preset = ModemPreset.LONG_FAST
                        wb.region = RegionCode.US
                        wb.hop_limit = 5
                        wb.channel_num = 13
                        wb.tx_enabled = true
                    }
                    .build()
        }
        .build()

    private companion object {
        /** The default LongFast channel with use_preset, hop limit 3 and TX on, as the Python CLI encodes it. */
        const val PAYLOAD = "CgMSAQESBggBQANIAQ"
        const val REPLACE_CHANNEL_URL = "https://meshtastic.org/e/#$PAYLOAD"
    }
}
