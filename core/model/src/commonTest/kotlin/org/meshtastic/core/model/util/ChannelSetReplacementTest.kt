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

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.meshtastic.core.model.Channel as ModelChannel

class ChannelSetReplacementTest {

    @Test
    fun `replacement plan normalizes padding and duplicates before slot validation`() {
        val primary = ChannelSettings.Builder().also { wb -> wb.name = "Primary" }.build()
        val uniqueSecondaries =
            (1..7).map { ChannelSettings.Builder().also { wb -> wb.name = "Secondary $it" }.build() }
        val rawSettings = listOf(primary, ChannelSettings.Builder().build(), primary) + uniqueSecondaries

        val plan =
            ChannelSet.Builder()
                .also { wb -> wb.settings = rawSettings }
                .build()
                .toChannelReplacementPlan(
                    currentSettings = emptyList(),
                    fallbackLoraConfig = null,
                    requirePrimary = true,
                )

        assertEquals((listOf(primary) + uniqueSecondaries).map { it.name }, plan.normalizedSettings.map { it.name })
        assertEquals((0..7).toList(), plan.channelWrites.map(Channel::index))
        assertEquals(Channel.Role.PRIMARY, plan.channelWrites.first().role)
        assertEquals(List(7) { Channel.Role.SECONDARY }, plan.channelWrites.drop(1).map(Channel::role))
    }

    @Test
    fun `replacement plan rejects empty profile channel set before producing writes`() {
        assertFailsWith<IllegalArgumentException> {
            ChannelSet.Builder()
                .also { wb -> wb.settings = emptyList() }
                .build()
                .toChannelReplacementPlan(
                    currentSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Existing" }.build()),
                    fallbackLoraConfig = null,
                    requirePrimary = true,
                )
        }
    }

    private val privateKey = ByteArray(32) { it.toByte() }.toByteString()

    private fun channel(name: String, psk: ByteString, precision: Int?) = ChannelSettings.Builder()
        .also { wb ->
            wb.name = name
            wb.psk = psk
            if (precision != null) {
                wb.module_settings =
                    ModuleSettings.Builder().also { mb -> mb.position_precision = precision }.build()
            }
        }
        .build()

    private fun importedPrecisions(vararg settings: ChannelSettings): List<Int?> {
        val plan =
            ChannelSet.Builder()
                .also { wb -> wb.settings = settings.toList() }
                .build()
                .toChannelReplacementPlan(currentSettings = emptyList(), fallbackLoraConfig = null)
        val written = plan.channelWrites.take(settings.size).map { it.settings?.module_settings?.position_precision }
        assertEquals(plan.normalizedSettings.map { it.module_settings?.position_precision }, written)
        return written
    }

    @Test
    fun `an imported public key channel is written with precise location capped`() {
        val defaultKey = ModelChannel.default.settings.psk

        assertEquals(listOf(15), importedPrecisions(channel("Public", defaultKey, precision = 32)))
        assertEquals(listOf(13), importedPrecisions(channel("Public", defaultKey, precision = 13)))
    }

    @Test
    fun `an imported public key channel with no module settings is written with position off`() {
        assertEquals(listOf(0), importedPrecisions(channel("Public", ModelChannel.default.settings.psk, null)))
    }

    @Test
    fun `an imported private key channel keeps what it carried`() {
        assertEquals(listOf(32), importedPrecisions(channel("Private", privateKey, precision = 32)))
        assertEquals(listOf(null), importedPrecisions(channel("Private", privateKey, precision = null)))
    }

    @Test
    fun `an imported secondary with no key follows the primary`() {
        val inheriting = channel("Inherits", ByteString.EMPTY, precision = 32)

        assertEquals(
            listOf(15, 15),
            importedPrecisions(channel("Public", ModelChannel.default.settings.psk, precision = 32), inheriting),
        )
        assertEquals(listOf(32, 32), importedPrecisions(channel("Private", privateKey, precision = 32), inheriting))
    }
}
