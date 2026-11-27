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

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.model.util.toSharedContact
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sizing rule is labeltastic's `qr_image` ((modules + 2) x dots must fit the 96-dot head, never under 2 dots) and
 * design#185 item 4. Module counts per payload length are the QR standard's version table at error correction L:
 * version 7 (45 modules) holds 154 bytes, version 8 (49 modules) is the first that no longer fits.
 */
class LabelQrTest {
    private val key = ByteArray(32) { it.toByte() }.toByteString()

    private fun contact(longName: String) = SharedContact.Builder()
        .also {
            it.node_num = 0xDEADBEEF.toInt()
            it.user =
                User.Builder()
                    .also { user ->
                        user.id = "!deadbeef"
                        user.long_name = longName
                        user.short_name = "WPG"
                        user.public_key = key
                    }
                    .build()
        }
        .build()

    private fun nameTag(longName: String) =
        LabelSpec.NameTag(NameTagContent("WPG", longName, "!deadbeef"), contact(longName), "HELLO MY NODE IS")

    private fun longNameIn(payload: String) = CommonUri.parse(payload).toSharedContact().user?.long_name

    @Test
    fun `a code prints at the largest whole number of dots that fits and never under 2`() {
        assertEquals(listOf(4, 3, 2, 2, null, null), listOf(21, 29, 45, 46, 47, 49).map { dotsPerModule(it) })
    }

    @Test
    fun `a contact whose code fits keeps its whole long name in the QR`() {
        val fitted = assertNotNull(fitQr(contactQrPayloads(contact("Wandering Packet Goblin")), LabelQrEncoder))

        assertEquals("Wandering Packet Goblin", longNameIn(fitted.payload))
        assertEquals(45 to 2, fitted.modules.size to fitted.dotsPerModule)
    }

    @Test
    fun `a long name is cut in the QR payload until the code fits and still printed whole`() {
        // 38 bytes of name make the link 158 bytes, 4 over what a version 7 code holds; 32 bytes of it fit.
        val name = "Wandering Packet Goblin Extraordinaire"
        val fitted = assertNotNull(fitQr(contactQrPayloads(contact(name)), LabelQrEncoder))

        assertEquals("Wandering Packet Goblin Extraord", longNameIn(fitted.payload))
        assertEquals(2, fitted.dotsPerModule)

        val layout = assertNotNull(nameTag(name).layout(labelLengthDots(40), FixedWidthMeasurer))
        val printed = layout.ops.filterIsInstance<LabelOp.Text>().joinToString(" ") { it.text }
        assertTrue("Wandering Packet Goblin Extraordinaire" in printed, "label prints '$printed'")
    }

    @Test
    fun `a long name is cut by UTF-8 bytes on a character boundary`() {
        assertEquals("🐛", "🐛🐛🐛".utf8Prefix(6))
        assertEquals("añ", "añá".utf8Prefix(4))
    }

    @Test
    fun `a channel set too dense for 2 dots per module is refused and a small one is laid out`() {
        fun channels(count: Int) = ChannelSet.Builder()
            .also {
                it.settings =
                    List(count) { index ->
                        ChannelSettings.Builder()
                            .also { settings ->
                                settings.name = "Channel$index"
                                settings.psk = key
                            }
                            .build()
                    }
            }
            .build()
        fun spec(count: Int) = LabelSpec.Channels(
            ChannelLabelContent("Channel0", "US", "Replaces channels"),
            channels(count).getChannelUrl().toString(),
            "JOIN MY MESH",
        )

        assertNull(spec(8).layout(labelLengthDots(40), FixedWidthMeasurer))
        val one = assertNotNull(spec(1).layout(labelLengthDots(40), FixedWidthMeasurer))
        assertTrue(one.ops.filterIsInstance<LabelOp.Qr>().single().dotsPerModule >= 2)
    }

    // design#185 items 8 and 9: the connect label is the name tag around the connect link, and can drop the PIN.
    @Test
    fun `a connect label encodes its link and is the name tag's layout under its own band`() {
        val content = NameTagContent("WPG", "Wandering Packet Goblin", "!deadbeef")
        val link = "meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF&pin=123456"
        val connect = LabelSpec.Connect(content, link, link.substringBefore("&pin"), "SCAN TO CONNECT")
        val encoded = mutableListOf<String>()
        val recording = QrEncoder { text ->
            encoded += text
            LabelQrEncoder.encode(text)
        }

        val layout = assertNotNull(connect.layout(labelLengthDots(40), FixedWidthMeasurer, recording))
        val code = layout.ops.filterIsInstance<LabelOp.Qr>().single()

        assertEquals(listOf(link), encoded)
        assertEquals(
            nameTagLayout(
                content,
                FittedQr(link, code.modules, code.dotsPerModule),
                316,
                "SCAN TO CONNECT",
                FixedWidthMeasurer,
            )
                .ops
                .filterNot { it is LabelOp.Qr },
            layout.ops.filterNot { it is LabelOp.Qr },
        )
        assertEquals("SCAN TO CONNECT", layout.ops.filterIsInstance<LabelOp.Band>().single().text)
    }

    @Test
    fun `a connect label printed without the PIN encodes the link that has none`() {
        val content = NameTagContent("WPG", "Wandering Packet Goblin", "!deadbeef")
        val plain = "meshtastic://meshtastic/connections?address=xAA:BB:CC:DD:EE:FF"
        val connect = LabelSpec.Connect(content, "$plain&pin=123456", plain, "SCAN TO CONNECT")

        assertEquals(connect, connect.withPin(include = true))
        assertEquals(LabelSpec.Connect(content, plain, null, "SCAN TO CONNECT"), connect.withPin(include = false))
    }
}
