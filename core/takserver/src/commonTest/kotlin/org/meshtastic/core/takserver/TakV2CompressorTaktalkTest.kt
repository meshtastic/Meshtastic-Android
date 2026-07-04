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
package org.meshtastic.core.takserver

import org.meshtastic.proto.CotType
import org.meshtastic.proto.GeoChat
import org.meshtastic.proto.TAKPacketV2
import org.meshtastic.proto.TakTalkMessage
import org.meshtastic.proto.TakTalkRoomData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the TakV2Compressor adapter layer's TAKTALK handling.
 *
 * Before 0.3.0, the wire-to-SDK and SDK-to-wire converters in TakV2Compressor.kt had no cases for [TakTalkMessage] or
 * [TakTalkRoomData]. m-t-t and y- events therefore fell through to [TakPacketV2Data.Payload.None] on the receive side,
 * which surfaced as TAKTALK voice push-to-talk messages never reaching the TTS stage on the receiving ATAK device. Chat
 * (b-t-f) was wired but its TAKTALK sidecars (lang, room_id, voice_profile_id) were dropped too.
 *
 * These tests pin the round-trip so any future regression of the same adapter gaps fails fast at unit-test time instead
 * of in the field.
 */
class TakV2CompressorTaktalkTest {

    @Test
    fun `m-t-t taktalk message round-trips with voice marker`() {
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_m_t_t,
                callsign = "ASPEN",
                uid = "TAKTALK-MESSAGE-81b44b48-21c5-4edf-aab2-b6bd2ec1f52f",
                taktalk =
                TakTalkMessage(
                    text = "Testing 123, testing 123.",
                    chatroom_id = "1",
                    lang = "English",
                    from_voice = true,
                ),
            )

        val wire = TakV2Compressor.compress(original)
        val decompressed = TakV2Compressor.decompress(wire)

        assertEquals(CotType.CotType_m_t_t, decompressed.cot_type_id)
        assertEquals("ASPEN", decompressed.callsign)
        assertNotNull(decompressed.taktalk, "taktalk variant must survive round-trip")
        assertEquals("Testing 123, testing 123.", decompressed.taktalk!!.text)
        assertEquals("1", decompressed.taktalk!!.chatroom_id)
        assertEquals("English", decompressed.taktalk!!.lang)
        assertTrue(decompressed.taktalk!!.from_voice, "<voice/> marker must survive")
        // v0.4.0+: `bool pli` was removed (PLI is implicit). A populated `taktalk`
        // arm already proves this did not degrade to an (implicit) PLI.
    }

    @Test
    fun `m-t-t taktalk text-only message has from_voice false`() {
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_m_t_t,
                callsign = "ETHEL",
                uid = "TAKTALK-MESSAGE-text-only",
                taktalk =
                TakTalkMessage(text = "typed message", chatroom_id = "1", lang = "English", from_voice = false),
            )

        val decompressed = TakV2Compressor.decompress(TakV2Compressor.compress(original))

        assertNotNull(decompressed.taktalk)
        assertEquals("typed message", decompressed.taktalk!!.text)
        assertEquals(false, decompressed.taktalk!!.from_voice)
    }

    @Test
    fun `y- taktalk room broadcast round-trips with participant list`() {
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_y,
                uid = "ROOM-DATA-5eac9336-2698-4dbb-9fa2-62b100e469fe",
                // v0.3.2: sender identity lives on envelope packet.callsign,
                // not on the deprecated TakTalkRoomData.sender_callsign field.
                // The SDK builder reconstitutes <sender-callsign> from envelope
                // callsign on emit.
                callsign = "ASPEN",
                taktalk_room =
                TakTalkRoomData(
                    room_id = "30b2755c-c547-44ef-a0cc-cdbd8a15616f",
                    room_name = "test",
                    participants = listOf("ETHEL", "ASPEN"),
                ),
            )

        val decompressed = TakV2Compressor.decompress(TakV2Compressor.compress(original))

        assertEquals(CotType.CotType_y, decompressed.cot_type_id)
        assertNotNull(decompressed.taktalk_room, "taktalk_room variant must survive round-trip")
        assertEquals(
            "ASPEN",
            decompressed.callsign,
            "v0.3.2: <sender-callsign> routes to envelope callsign on round-trip",
        )
        assertEquals("30b2755c-c547-44ef-a0cc-cdbd8a15616f", decompressed.taktalk_room!!.room_id)
        assertEquals("test", decompressed.taktalk_room!!.room_name)
        assertEquals(listOf("ETHEL", "ASPEN"), decompressed.taktalk_room!!.participants)
    }

    @Test
    fun `m-t-t with marti round-trips dest callsigns and survives compression`() {
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_m_t_t,
                uid = "TAKTALK-MESSAGE-marti-test-uuid",
                callsign = "ASPEN",
                taktalk =
                TakTalkMessage(
                    text = "Push-to-talk to ETHEL",
                    chatroom_id = "1",
                    lang = "English",
                    from_voice = true,
                ),
                // Directed-routing recipient list. TAKTALK gates voice TTS on
                // this list matching the receiver's callsign — a regression
                // here silently breaks voice messaging end-to-end.
                marti = org.meshtastic.proto.Marti(dest_callsign = listOf("ETHEL")),
            )

        val decompressed = TakV2Compressor.decompress(TakV2Compressor.compress(original))

        assertEquals(CotType.CotType_m_t_t, decompressed.cot_type_id)
        assertEquals("ASPEN", decompressed.callsign)
        assertNotNull(decompressed.marti, "marti must survive compression round-trip")
        assertEquals(
            listOf("ETHEL"),
            decompressed.marti!!.dest_callsign,
            "marti.dest_callsign must round-trip unchanged so TAKTALK can resolve TTS targets",
        )
    }

    @Test
    fun `b-t-f chat carries TAKTALK sidecars through compressor`() {
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_b_t_f,
                callsign = "ASPEN",
                uid = "GeoChat.test",
                chat =
                GeoChat(
                    message = "Test message",
                    lang = "English",
                    room_id = "30b2755c-c547-44ef-a0cc-cdbd8a15616f",
                    voice_profile_id = "", // empty marker `<voice_profile_id/>`
                ),
            )

        val decompressed = TakV2Compressor.decompress(TakV2Compressor.compress(original))

        assertNotNull(decompressed.chat)
        assertEquals("Test message", decompressed.chat!!.message)
        assertEquals("English", decompressed.chat!!.lang, "TAKTALK <Ea> lang must survive")
        assertEquals(
            "30b2755c-c547-44ef-a0cc-cdbd8a15616f",
            decompressed.chat!!.room_id,
            "TAKTALK <roomId> must survive",
        )
        assertNotNull(
            decompressed.chat!!.voice_profile_id,
            "empty <voice_profile_id/> marker must survive as empty string",
        )
        assertEquals("", decompressed.chat!!.voice_profile_id)
    }

    @Test
    fun `b-t-f chat without TAKTALK sidecars leaves them null on the wire`() {
        // Plain GeoChat from non-TAKTALK ATAK — no sidecars present.  The
        // round-trip must not synthesize empty-string sidecars that would
        // change the rebuilt CoT XML for plain chat messages.
        val original =
            TAKPacketV2(
                cot_type_id = CotType.CotType_b_t_f,
                callsign = "ALPHA-1",
                uid = "GeoChat.plain",
                chat =
                GeoChat(
                    message = "plain chat",
                    // no lang, no room_id, no voice_profile_id
                ),
            )

        val decompressed = TakV2Compressor.decompress(TakV2Compressor.compress(original))

        assertNotNull(decompressed.chat)
        assertEquals("plain chat", decompressed.chat!!.message)
        assertNull(decompressed.chat!!.lang, "non-TAKTALK chat should have null lang on the wire")
        assertNull(decompressed.chat!!.room_id, "non-TAKTALK chat should have null room_id")
        assertNull(decompressed.chat!!.voice_profile_id, "non-TAKTALK chat should have null voice_profile_id")
    }
}
