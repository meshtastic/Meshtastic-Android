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
package org.meshtastic.feature.settings.radio.channel

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.util.toChannelSet
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChannelScreenTest {
    @Test
    fun `channel share state defaults to a replace URL`() {
        val channelSet = ChannelSet(lora_config = LoRaConfig(region = RegionCode.US))

        val url = ChannelShareState().uriString(channelSet)

        assertFalse(url.contains("?add=true"))
        assertEquals(channelSet.lora_config, CommonUri.parse(url).toChannelSet().lora_config)
    }
}
