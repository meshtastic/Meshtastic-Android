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
package org.meshtastic.core.ui.label

import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every character is 0.6 of the font size wide and an emoji as wide as it is tall, so expected widths are sums. */
internal val FixedWidthMeasurer = LabelTextMeasurer { text, size, emoji ->
    text.codePointList().size * size * if (emoji) 1f else 0.6f
}

/**
 * Expected name tag positions and sizes are what labeltastic's `render_compact` at meshtastic/labeltastic#38 draws for
 * the same node, label length and QR size with its text measurement replaced by [FixedWidthMeasurer]'s rule. The
 * channel label, which labeltastic does not have, is Meshtastic-Apple's `ChannelLabelRenderer` worked through by hand.
 * The margins are design#185's.
 */
class LabelLayoutTest {
    // A version 7 code, the size of a keyed contact's: 45 modules at 2 dots, so the text column starts at 103.
    private val keyedQr = FittedQr("", QrModules(45, BooleanArray(45 * 45)), dotsPerModule = 2)
    private val wanderer = NameTagContent("WPG", "Wandering Packet Goblin", "!deadbeef")
    private val pinned = wanderer.copy(shortName = "🐛")

    private fun nameTag(content: NameTagContent, millimeters: Int) =
        nameTagLayout(content, keyedQr, labelLengthDots(millimeters), "HELLO MY NODE IS", FixedWidthMeasurer)

    private fun LabelLayout.texts() = ops.filterIsInstance<LabelOp.Text>()

    @Test
    fun `labels are 4 dots short of 8 per millimeter`() {
        assertEquals(listOf(236, 316), listOf(30, 40).map { labelLengthDots(it) })
    }

    @Test
    fun `the band is on a 40 mm name tag and not on a 30 mm one`() {
        assertEquals(
            listOf(LabelOp.Band(x = 103, y = 2, width = 210, height = 22, text = "HELLO MY NODE IS", size = 21)),
            nameTag(wanderer, 40).ops.filterIsInstance<LabelOp.Band>(),
        )
        assertEquals(emptyList(), nameTag(wanderer, 30).ops.filterIsInstance<LabelOp.Band>())
    }

    @Test
    fun `the QR sits 3 dots in with 2 of white and is centered on the head`() {
        val qr = nameTag(wanderer, 30).ops.filterIsInstance<LabelOp.Qr>().single()

        assertEquals(listOf(5, 3, 2), listOf(qr.x, qr.y, qr.dotsPerModule))
    }

    @Test
    fun `text and the mark keep 2 dots from the top and bottom and 3 from each end`() {
        val channel = ChannelLabelContent("Hiking", "US · LongFast", "Adds 2 channels")
        val layouts =
            listOf(30, 40).flatMap { millimeters ->
                listOf(
                    nameTag(wanderer, millimeters),
                    nameTag(pinned, millimeters),
                    nameTag(wanderer.copy(longName = "Wandering Packet Goblin Extraordinaire"), millimeters),
                    channelLabelLayout(
                        channel,
                        keyedQr,
                        labelLengthDots(millimeters),
                        "JOIN MY MESH",
                        FixedWidthMeasurer,
                    ),
                )
            }
        val type = LabelTypesetter(FixedWidthMeasurer)

        for (layout in layouts) {
            val inside =
                layout.ops.all { op ->
                    when (op) {
                        is LabelOp.Qr -> true

                        is LabelOp.Band ->
                            op.x >= 3 && op.x + op.width <= layout.width - 3 && op.y >= 2 && op.y + op.height <= 94

                        is LabelOp.Text ->
                            op.x >= 3 &&
                                op.x + type.width(op.text, op.size) <= layout.width - 3 &&
                                op.yCenter - op.size / 2 >= 2 &&
                                op.yCenter + op.size / 2 <= 94

                        is LabelOp.Mark -> op.right == layout.width - 3 && op.bottom == 94
                    }
                }
            assertTrue(inside, "outside the margins: ${layout.ops.filterNot { it is LabelOp.Qr }}")
        }
    }

    @Test
    fun `a short and a long name that fit print big and under it with the node ID on the bottom row`() {
        assertEquals(
            listOf(
                LabelOp.Text("WPG", x = 103, yCenter = 39, size = 32),
                LabelOp.Text("Wandering Packet Goblin", x = 103, yCenter = 64, size = 15),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            nameTag(wanderer, 40).texts(),
        )
    }

    @Test
    fun `a long name too long for one line on 30 mm wraps to two under a smaller short name`() {
        assertEquals(
            listOf(
                LabelOp.Text("WPG", x = 103, yCenter = 18, size = 32),
                LabelOp.Text("Wandering", x = 103, yCenter = 42, size = 16),
                LabelOp.Text("Packet Goblin", x = 103, yCenter = 58, size = 16),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            nameTag(wanderer, 30).texts(),
        )
    }

    @Test
    fun `an emoji-only short name is a pin above the mark on 40 mm and the long name is the big line`() {
        assertEquals(
            listOf(
                LabelOp.Text("🐛", x = 267, yCenter = 48, size = 46),
                LabelOp.Text("Wandering", x = 103, yCenter = 40, size = 18),
                LabelOp.Text("Packet Goblin", x = 103, yCenter = 60, size = 18),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            nameTag(pinned, 40).texts(),
        )
    }

    @Test
    fun `the emoji pin is held to a quarter of the text column`() {
        // 286 dots leave a 180-dot column, a quarter of which is 45.
        val texts = nameTagLayout(pinned, keyedQr, 286, "HELLO MY NODE IS", FixedWidthMeasurer).texts()

        assertEquals(
            listOf(
                LabelOp.Text("🐛", x = 238, yCenter = 48, size = 45),
                LabelOp.Text("Wandering", x = 103, yCenter = 41, size = 16),
                LabelOp.Text("Packet Goblin", x = 103, yCenter = 59, size = 16),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            texts,
        )
    }

    @Test
    fun `an emoji-only short name sits between the node ID and the mark on 30 mm`() {
        assertEquals(
            listOf(
                LabelOp.Text("🐛", x = 174, yCenter = 82, size = 20),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 83, size = 12),
                LabelOp.Text("Wandering", x = 103, yCenter = 14, size = 20),
                LabelOp.Text("Packet", x = 103, yCenter = 36, size = 20),
                LabelOp.Text("Goblin", x = 103, yCenter = 58, size = 20),
            ),
            nameTag(pinned, 30).texts(),
        )
    }

    @Test
    fun `a name too long for one line wraps under a smaller short name or is cut with an ellipsis`() {
        val long = wanderer.copy(longName = "Wandering Packet Goblin Extraordinaire")

        assertEquals(
            listOf(
                LabelOp.Text("WPG", x = 103, yCenter = 35, size = 24),
                LabelOp.Text("Wandering Packet Goblin", x = 103, yCenter = 51, size = 13),
                LabelOp.Text("Extraordinaire", x = 103, yCenter = 64, size = 13),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            nameTag(long, 40).texts(),
        )
        assertEquals(
            listOf(
                LabelOp.Text("WPG", x = 103, yCenter = 21, size = 38),
                LabelOp.Text("Wandering Packet Goblin…", x = 103, yCenter = 52, size = 9),
                LabelOp.Text("!deadbeef", x = 103, yCenter = 85, size = 16),
            ),
            nameTag(long, 30).texts(),
        )
    }

    @Test
    fun `a short name with nothing printable falls back to the end of the node ID`() {
        val texts = nameTag(wanderer.copy(shortName = "‍️"), 40).texts()

        assertEquals("BEEF", texts.first().text)
    }

    @Test
    fun `a channel label stacks the name and radio above what scanning does`() {
        // A version 3 code at 3 dots is 87 wide, so the column starts at 100 and is 213 wide.
        val qr = FittedQr("", QrModules(29, BooleanArray(29 * 29)), dotsPerModule = 3)
        val layout =
            channelLabelLayout(
                ChannelLabelContent("Hiking", "US · LongFast", "Adds 2 channels"),
                qr,
                labelLengthDots(40),
                "JOIN MY MESH",
                FixedWidthMeasurer,
            )

        assertEquals(
            listOf(LabelOp.Band(x = 100, y = 2, width = 213, height = 22, text = "JOIN MY MESH", size = 21)),
            layout.ops.filterIsInstance<LabelOp.Band>(),
        )
        assertEquals(
            listOf(
                LabelOp.Text("Hiking", x = 100, yCenter = 40, size = 30),
                LabelOp.Text("US · LongFast", x = 100, yCenter = 67, size = 16),
                LabelOp.Text("Adds 2 channels", x = 100, yCenter = 85, size = 14),
            ),
            layout.texts(),
        )
    }

    // design#185 item 3: the first channel's name, or the preset's for the default channel; region and preset.
    @Test
    fun `the default channel is titled with its preset and a named one says its preset beside the region`() {
        val lora =
            LoRaConfig.Builder()
                .also {
                    it.region = LoRaConfig.RegionCode.US
                    it.use_preset = true
                }
                .build()
        fun content(name: String) = channelLabelContent(
            ChannelSet.Builder()
                .also {
                    it.settings =
                        listOf(ChannelSettings.Builder().also { settings -> settings.name = name }.build())
                    it.lora_config = lora
                }
                .build(),
            modemPresetName = "LongFast",
            defaultChannel = "Default channel",
            action = "Replaces channels",
        )

        assertEquals(ChannelLabelContent("LongFast", "US · Default channel", "Replaces channels"), content(""))
        assertEquals(ChannelLabelContent("Hiking", "US · LongFast", "Replaces channels"), content("Hiking"))
    }
}
