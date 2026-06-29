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
package org.meshtastic.core.ui.util

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [getChannelReplacementList]. The REPLACE helper must emit an authoritative slot list for QR imports:
 * every imported index becomes a write (PRIMARY at 0, SECONDARY thereafter), and any trailing slots present in the
 * cached set are emitted as DISABLED so the radio stops using them. Critically, positions where the cache already
 * matches the import are NOT skipped — the diff-skip was the source of stale channels.
 */
class ProtoExtensionsTest {
    @Test
    fun index_zero_emits_primary_with_new_settings_even_when_unchanged_from_old() {
        val same = ChannelSettings(name = "Main", psk = byteArrayOf(1, 2, 3).toByteString())

        val result = getChannelReplacementList(new = listOf(same), currentSettings = listOf(same))

        assertEquals(1, result.size)
        assertEquals(Channel.Role.PRIMARY, result.single().role)
        assertEquals(0, result.single().index)
        assertEquals(same, result.single().settings)
    }

    @Test
    fun secondary_indices_emit_secondary_with_new_settings_even_when_unchanged_from_old() {
        val primary = ChannelSettings(name = "Main")
        val secondary = ChannelSettings(name = "Chat")

        val result =
            getChannelReplacementList(new = listOf(primary, secondary), currentSettings = listOf(primary, secondary))

        assertEquals(2, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.SECONDARY, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(secondary, result[1].settings)
    }

    @Test
    fun old_trailing_indices_beyond_new_are_emitted_as_disabled_with_empty_settings() {
        val primary = ChannelSettings(name = "Main")

        val result =
            getChannelReplacementList(
                new = listOf(primary),
                currentSettings = listOf(primary, ChannelSettings(name = "Old")),
            )

        // index 0 PRIMARY (new), index 1 DISABLED (trailing old slot)
        assertEquals(2, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.DISABLED, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(ChannelSettings(), result[1].settings)
    }

    @Test
    fun empty_new_and_empty_old_produces_empty_list() {
        val result = getChannelReplacementList(new = emptyList(), currentSettings = emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun empty_new_with_non_empty_current_emits_disabled_for_every_current_index() {
        val currentSettings =
            listOf(ChannelSettings(name = "A"), ChannelSettings(name = "B"), ChannelSettings(name = "C"))

        val result = getChannelReplacementList(new = emptyList(), currentSettings = currentSettings)

        assertEquals(3, result.size)
        result.forEachIndexed { i, channel ->
            assertEquals(Channel.Role.DISABLED, channel.role, "index $i should be DISABLED")
            assertEquals(i, channel.index)
            assertEquals(ChannelSettings(), channel.settings, "index $i should carry empty settings")
        }
    }

    @Test
    fun single_entry_new_with_multi_entry_current_emits_primary_then_disabled_trailing() {
        val newPrimary = ChannelSettings(name = "Imported")
        val currentSettings =
            listOf(
                ChannelSettings(name = "CurrentPrimary"),
                ChannelSettings(name = "CurrentSecondary"),
                ChannelSettings(name = "CurrentTertiary"),
            )

        val result = getChannelReplacementList(new = listOf(newPrimary), currentSettings = currentSettings)

        assertEquals(3, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(0, result[0].index)
        assertEquals(newPrimary, result[0].settings)
        assertEquals(Channel.Role.DISABLED, result[1].role)
        assertEquals(Channel.Role.DISABLED, result[2].role)
        assertEquals(ChannelSettings(), result[1].settings)
        assertEquals(ChannelSettings(), result[2].settings)
    }

    @Test
    fun new_larger_than_old_emits_primary_plus_secondaries_for_every_new_index() {
        val primary = ChannelSettings(name = "Main")
        val secondaryA = ChannelSettings(name = "Chat")
        val secondaryB = ChannelSettings(name = "Data")

        val result =
            getChannelReplacementList(new = listOf(primary, secondaryA, secondaryB), currentSettings = listOf(primary))

        assertEquals(3, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(0, result[0].index)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.SECONDARY, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(secondaryA, result[1].settings)
        assertEquals(Channel.Role.SECONDARY, result[2].role)
        assertEquals(2, result[2].index)
        assertEquals(secondaryB, result[2].settings)
    }

    // --- mergeChannelSettingsForAdd tests ---

    @Test
    fun merge_preserves_all_existing_channels_in_order() {
        val existing = listOf(ChannelSettings(name = "A"), ChannelSettings(name = "B"))

        val result = mergeChannelSettingsForAdd(existing, incoming = emptyList())

        assertEquals(2, result.size)
        assertEquals("A", result[0].name)
        assertEquals("B", result[1].name)
    }

    @Test
    fun merge_appends_all_incoming_channels_in_order() {
        val incoming = listOf(ChannelSettings(name = "C"), ChannelSettings(name = "D"))

        val result = mergeChannelSettingsForAdd(existing = emptyList(), incoming)

        assertEquals(2, result.size)
        assertEquals("C", result[0].name)
        assertEquals("D", result[1].name)
    }

    @Test
    fun merge_preserves_structurally_equal_channels() {
        val channel = ChannelSettings(name = "LongFast", psk = byteArrayOf(1).toByteString())

        val result = mergeChannelSettingsForAdd(existing = listOf(channel), incoming = listOf(channel))

        assertEquals(2, result.size)
    }

    @Test
    fun merge_preserves_same_name_different_psk() {
        val existingChan = ChannelSettings(name = "A", psk = byteArrayOf(1).toByteString())
        val incomingChan = ChannelSettings(name = "A", psk = byteArrayOf(2).toByteString())

        val result = mergeChannelSettingsForAdd(listOf(existingChan), listOf(incomingChan))

        assertEquals(2, result.size)
    }

    @Test
    fun merge_preserves_same_psk_different_name() {
        val psk = byteArrayOf(1, 2).toByteString()
        val existingChan = ChannelSettings(name = "A", psk = psk)
        val incomingChan = ChannelSettings(name = "B", psk = psk)

        val result = mergeChannelSettingsForAdd(listOf(existingChan), listOf(incomingChan))

        assertEquals(2, result.size)
    }

    @Test
    fun merge_preserves_duplicate_inside_incoming() {
        val a = ChannelSettings(name = "A", psk = byteArrayOf(1).toByteString())
        val b = ChannelSettings(name = "B", psk = byteArrayOf(2).toByteString())

        val result = mergeChannelSettingsForAdd(existing = emptyList(), incoming = listOf(a, a, b))

        assertEquals(3, result.size)
    }

    @Test
    fun merge_both_empty_produces_empty_list() {
        val result = mergeChannelSettingsForAdd(existing = emptyList(), incoming = emptyList())

        assertTrue(result.isEmpty())
    }
}
