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

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.meshtastic.core.model.Channel as ModelChannel

class ChannelReconciliationTest {
    private val longFast =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
            }
            .build()
    private val mediumFast =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.MEDIUM_FAST
            }
            .build()
    private val defaultPsk = byteArrayOf(0x01).toByteString()
    private val otherPsk = byteArrayOf(0x09, 0x08).toByteString()

    private fun set(lora: LoRaConfig?, vararg settings: ChannelSettings) = ChannelSet.Builder()
        .also { wb ->
            wb.settings = settings.toList()
            wb.lora_config = lora
        }
        .build()

    @Test
    fun `an unchanged set produces no changes`() {
        val settings =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        assertEquals(emptyList(), planChannelReconciliation(set(longFast, settings), set(longFast, settings)))
    }

    @Test
    fun `a replaced slot retires the previous occupant`() {
        val old =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val changes =
            planChannelReconciliation(
                set(longFast, old),
                set(longFast, ChannelSettings.Builder().also { wb -> wb.psk = otherPsk }.build()),
            )

        val change = changes.single()
        assertEquals(ConversationSlot.Live(0), change.from)
        assertEquals(ConversationSlot.Retired(old.channelIdentity(longFast).token), change.to)
        assertEquals("A", change.displayName)
    }

    @Test
    fun `a reordered channel moves rather than retires`() {
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = otherPsk
                    wb.name = "B"
                }
                .build()
        val changes = planChannelReconciliation(set(longFast, a, b), set(longFast, b, a))

        assertEquals(
            setOf(
                ChannelKeyChange(ConversationSlot.Live(0), ConversationSlot.Live(1), "A"),
                ChannelKeyChange(ConversationSlot.Live(1), ConversationSlot.Live(0), "B"),
            ),
            changes.toSet(),
        )
    }

    /** Effective name comes from the preset, so a preset switch is a channel change with neither raw field moved. */
    @Test
    fun `a preset switch is a channel change`() {
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        val changes = planChannelReconciliation(set(longFast, settings), set(mediumFast, settings))

        assertEquals("LongFast", changes.single().displayName)
        assertTrue(changes.single().to is ConversationSlot.Retired)
    }

    /**
     * The radio can report the same channel in two slots (the handshake writes each slot as it arrives, with no
     * de-duplication). A conversation that still holds its own slot must stay there rather than being dragged to the
     * duplicate — the behaviour the old PSK migration had, and the reason it preferred the same index.
     */
    @Test
    fun `a duplicated channel does not drag a conversation off its own slot`() {
        val settings =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        assertEquals(emptyList(), planChannelReconciliation(set(longFast, settings), set(longFast, settings, settings)))
    }

    /** With the identity gone from its own slot, the lowest duplicate is a deterministic destination. */
    @Test
    fun `a duplicated channel resolves to its lowest slot`() {
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = otherPsk
                    wb.name = "B"
                }
                .build()
        val changes = planChannelReconciliation(set(longFast, b, a), set(longFast, b, b, a, a))

        assertEquals(ConversationSlot.Live(2), changes.single { it.from == ConversationSlot.Live(1) }.to)
    }

    /** A parked channel must not be reclaimed twice when the radio reports it in two slots. */
    @Test
    fun `a duplicated channel is reclaimed only once`() {
        val settings =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val token = settings.channelIdentity(longFast).token
        val changes =
            planChannelReconciliation(
                old = set(longFast, ChannelSettings.Builder().also { wb -> wb.psk = otherPsk }.build()),
                new = set(longFast, settings, settings),
                retiredTokens = setOf(token),
            )

        assertEquals(1, changes.count { it.from == ConversationSlot.Retired(token) })
    }

    /**
     * Two slots can genuinely resolve to the same channel: a blank-named secondary carrying the one-byte default PSK is
     * not a placeholder (`psk.size == 0` is false), so it has the primary's identity. The slot that still holds that
     * identity must keep its own conversation — merging the other one into it is the very failure this repairs.
     */
    @Test
    fun `a duplicate old slot is retired rather than merged into the slot that kept the identity`() {
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        val changes = planChannelReconciliation(set(longFast, settings, settings), set(longFast, settings))

        val change = changes.single()
        assertEquals(ConversationSlot.Live(1), change.from)
        assertTrue(change.to is ConversationSlot.Retired, "slot 1 must not be merged onto slot 0")
    }

    /** With duplicates on both sides the pairing must be one-to-one, never two conversations onto one slot. */
    @Test
    fun `duplicate slots pair off one to one`() {
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = otherPsk
                    wb.name = "B"
                }
                .build()
        // A holds slot 1 in both sets, so it stays; B and the other A trade places around it.
        val changes = planChannelReconciliation(set(longFast, b, a, a), set(longFast, a, a, b))

        assertEquals(
            setOf(
                ChannelKeyChange(ConversationSlot.Live(0), ConversationSlot.Live(2), "B"),
                ChannelKeyChange(ConversationSlot.Live(2), ConversationSlot.Live(0), "A"),
            ),
            changes.toSet(),
        )
        assertEquals(
            changes.size,
            changes.map { it.to }.distinct().size,
            "no two conversations may be sent to the same slot",
        )
    }

    @Test
    fun `a parked channel is reclaimed when it comes back`() {
        val settings =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val token = settings.channelIdentity(longFast).token
        val changes =
            planChannelReconciliation(
                old = set(longFast, ChannelSettings.Builder().also { wb -> wb.psk = otherPsk }.build()),
                new = set(longFast, settings),
                retiredTokens = setOf(token),
            )

        assertTrue(changes.any { it.from == ConversationSlot.Retired(token) && it.to == ConversationSlot.Live(0) })
    }

    /** A blank name only resolves through the preset, so an unknown LoRa config cannot decide identity. */
    @Test
    fun `a missing lora config on either side retires nothing`() {
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        assertEquals(emptyList(), planChannelReconciliation(set(longFast, settings), set(null, settings)))
        assertEquals(emptyList(), planChannelReconciliation(set(null, settings), set(longFast, settings)))
    }

    /**
     * An empty channel list means "not downloaded yet", never "every channel was deleted" — a real radio always has a
     * primary. Retiring against it would archive the whole device's history mid-handshake.
     */
    @Test
    fun `an empty new channel list retires nothing`() {
        val settings =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        assertEquals(
            emptyList(),
            planChannelReconciliation(
                set(longFast, settings),
                ChannelSet.Builder().also { wb -> wb.lora_config = longFast }.build(),
            ),
        )
    }

    /** A gap left by a removed secondary is padded with a bare ChannelSettings; that is not a channel. */
    @Test
    fun `blank padding secondaries are not treated as channels`() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "A"
                }
                .build()
        val changes =
            planChannelReconciliation(
                old = set(longFast, primary, ChannelSettings.Builder().build()),
                new = set(longFast, primary),
            )
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `region is not part of channel identity`() {
        val settings = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        val changes =
            planChannelReconciliation(
                set(longFast, settings),
                set(longFast.newBuilder().also { wb -> wb.region = LoRaConfig.RegionCode.EU_868 }.build(), settings),
            )
        assertEquals(emptyList(), changes)
    }

    /**
     * The default PSK marker `AQ==` expands to the same key on every preset, so the effective name carries the whole
     * distinction between one stock channel and another. This is the case the reported bug ran into.
     */
    @Test
    fun `the default psk is disambiguated across presets`() {
        val stock = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        val tokens =
            listOf(
                longFast,
                mediumFast,
                LoRaConfig.Builder()
                    .also { wb ->
                        wb.use_preset = true
                        wb.modem_preset = ModemPreset.SHORT_FAST
                    }
                    .build(),
            )
                .map { lora -> stock.channelIdentity(lora).token }

        assertEquals(tokens.size, tokens.distinct().size, "each preset must be a distinct channel")
    }

    /**
     * Known limit, tracked in meshtastic/design#157: a custom LoRa config has no preset name, so every blank-named
     * custom channel resolves to "Custom" and two different custom meshes share an identity. This matches the firmware,
     * which also hashes only name and PSK into the channel — custom meshes are separated by frequency, not by channel.
     * Pinned here so that changing it is a deliberate act.
     */
    @Test
    fun `two different custom lora configs are not yet distinguished`() {
        val stock = ChannelSettings.Builder().also { wb -> wb.psk = defaultPsk }.build()
        val slow =
            LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = false
                    wb.bandwidth = 125
                    wb.spread_factor = 11
                    wb.coding_rate = 8
                }
                .build()
        val fast =
            LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = false
                    wb.bandwidth = 250
                    wb.spread_factor = 7
                    wb.coding_rate = 5
                }
                .build()

        assertEquals("Custom", ModelChannel(settings = stock, loraConfig = slow).name)
        assertEquals(stock.channelIdentity(slow).token, stock.channelIdentity(fast).token)
    }

    @Test
    fun `a token distinguishes name from psk and never leaks either`() {
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "ab"
                }
                .build()
                .channelIdentity(longFast)
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.psk = defaultPsk
                    wb.name = "ba"
                }
                .build()
                .channelIdentity(longFast)
        assertNotEquals(a.token, b.token)
        assertEquals(CHANNEL_IDENTITY_TOKEN_LENGTH, a.token.length)
        assertTrue(a.token.all { it.isDigit() || it in 'a'..'f' })
        assertEquals("ChannelIdentity(name=ab, psk=<redacted>)", a.toString())
    }
}
