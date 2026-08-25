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
package org.meshtastic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks down the `CALLSIGN//Long name` composition the app has to agree with firmware's `handleSetHamMode()` on: the
 * app builds it optimistically and re-splits whatever the device reports back, so a disagreement would show the
 * operator a name their node does not have.
 */
class HamNameTest {

    @Test
    fun compose_joins_the_two_halves_with_the_separator() {
        assertEquals("KD2ABC//Attic Heltec", HamName.compose("KD2ABC", "Attic Heltec"))
    }

    @Test
    fun compose_omits_the_separator_when_the_long_name_is_unset() {
        // Firmware reads unset and whitespace-only alike, so neither may leave a dangling separator behind.
        assertEquals("KD2ABC", HamName.compose("KD2ABC", ""))
        assertEquals("KD2ABC", HamName.compose("KD2ABC", "   "))
    }

    @Test
    fun split_separates_a_composed_name() {
        assertEquals("KD2ABC" to "Attic Heltec", HamName.split("KD2ABC//Attic Heltec"))
    }

    @Test
    fun split_reads_a_name_without_a_separator_as_all_call_sign() {
        assertEquals("KD2ABC" to "", HamName.split("KD2ABC"))
        assertEquals("" to "", HamName.split(""))
    }

    @Test
    fun split_keeps_a_later_separator_inside_the_long_name() {
        // Only the first separator divides the halves; firmware composes on the first one too.
        assertEquals("KD2ABC" to "a//b", HamName.split("KD2ABC//a//b"))
    }

    @Test
    fun split_round_trips_every_name_compose_can_build() {
        listOf(
            "KD2ABC" to "Attic Heltec",
            "KD2ABC" to "",
            "" to "Attic Heltec",
            "KD2ABC" to "a//b",
        ).forEach { (callSign, longName) ->
            assertEquals(callSign to longName, HamName.split(HamName.compose(callSign, longName)))
        }
    }

    @Test
    fun the_widest_pair_the_proto_can_carry_fits_the_firmware_owner_name() {
        val widest = HamName.compose("KD2ABCD", "Attic Heltec 3")
        assertEquals(HamName.MAX_CALL_SIGN_BYTES, "KD2ABCD".utf8Size())
        assertEquals(HamName.MAX_LONG_NAME_BYTES, "Attic Heltec 3".utf8Size())
        // MAX_LONG_NAME_BYTES in firmware's NodeDB is 24; the composed pair has to arrive whole.
        assertTrue(widest.utf8Size() <= 24, "composed name is ${widest.utf8Size()} bytes")
    }

    @Test
    fun forOnboarding_leaves_a_name_that_can_be_a_call_sign_alone() {
        assertEquals("KD2ABC", HamName.forOnboarding("KD2ABC"))
        assertEquals("KD2ABCD", HamName.forOnboarding("KD2ABCD"))
        assertEquals("", HamName.forOnboarding(""))
    }

    @Test
    fun forOnboarding_leaves_an_already_composed_name_alone() {
        assertEquals("KD2ABC//Attic Heltec", HamName.forOnboarding("KD2ABC//Attic Heltec"))
    }

    @Test
    fun forOnboarding_demotes_an_over_long_name_to_the_long_name_half() {
        // "Attic Heltec" cannot be a call sign, but it is a perfectly good descriptive name — keep it rather than
        // making the operator retype it.
        assertEquals("" to "Attic Heltec", HamName.split(HamName.forOnboarding("Attic Heltec")))
    }

    @Test
    fun forOnboarding_clips_a_demoted_name_to_the_proto_cap() {
        val (callSign, longName) = HamName.split(HamName.forOnboarding("Attic Heltec Node Number Three"))

        assertEquals("", callSign)
        assertEquals("Attic Heltec N", longName)
        assertEquals(HamName.MAX_LONG_NAME_BYTES, longName.utf8Size())
    }

    @Test
    fun forOnboarding_clips_on_a_code_point_boundary() {
        // Each emoji is a 4-byte, two-UTF-16-char code point, so 14 bytes of budget holds three. The fourth must be
        // dropped whole rather than cut into an unpaired surrogate.
        val grinning = "\uD83D\uDE00"
        val (_, longName) = HamName.split(HamName.forOnboarding(grinning.repeat(5)))

        assertEquals(12, longName.utf8Size())
        assertEquals(longName, longName.encodeToByteArray().decodeToString(), "clipping split a code point")
    }

    @Test
    fun forOnboarding_keeps_the_long_name_when_only_the_call_sign_is_over_long() {
        assertEquals("" to "Attic Heltec", HamName.split(HamName.forOnboarding("Old Node Name//Attic Heltec")))
    }

    @Test
    fun forUnlicensing_keeps_a_composed_name_the_node_actually_has() {
        assertEquals("KD2ABC//Attic Heltec", HamName.forUnlicensing("KD2ABC//Attic Heltec"))
        assertEquals("Attic Heltec", HamName.forUnlicensing("Attic Heltec"))
    }

    @Test
    fun forUnlicensing_drops_the_separator_left_by_an_abandoned_onboarding() {
        // Toggling licensed on and back off without entering a callsign must not name the node "//Attic Heltec".
        assertEquals("Attic Heltec", HamName.forUnlicensing(HamName.forOnboarding("Attic Heltec")))
        assertEquals("", HamName.forUnlicensing(""))
    }
}
