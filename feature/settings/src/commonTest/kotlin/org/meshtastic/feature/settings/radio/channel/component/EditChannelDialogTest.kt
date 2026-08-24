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
package org.meshtastic.feature.settings.radio.channel.component

import org.meshtastic.core.model.Channel
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EditChannelDialogTest {
    @Test
    fun `new named manual channel generates fresh psk`() {
        val update =
            Channel.default.settings.applyChannelNameEdit(
                nameInput = "custom",
                pskEditState = ChannelPskEditState(canGeneratePskForName = true),
            )

        assertEquals("custom", update.settings.name)
        assertNotEquals(Channel.default.settings.psk, update.settings.psk)
        assertTrue(update.pskEditState.generatedPskForName)
        assertFalse(update.pskEditState.pskExplicitlyEdited)
    }

    @Test
    fun `explicitly entered default marker is preserved when naming channel`() {
        val update =
            Channel.default.settings.applyChannelNameEdit(
                nameInput = "custom",
                pskEditState = ChannelPskEditState(canGeneratePskForName = true, pskExplicitlyEdited = true),
            )

        assertEquals("custom", update.settings.name)
        assertEquals(Channel.default.settings.psk, update.settings.psk)
        assertFalse(update.pskEditState.generatedPskForName)
    }

    @Test
    fun `existing default psk channel is not rotated when renamed`() {
        val update =
            Channel.default.settings.applyChannelNameEdit(
                nameInput = "custom",
                pskEditState = ChannelPskEditState(canGeneratePskForName = false),
            )

        assertEquals("custom", update.settings.name)
        assertEquals(Channel.default.settings.psk, update.settings.psk)
        assertFalse(update.pskEditState.generatedPskForName)
    }

    @Test
    fun `generated name psk is restored to default when name is cleared after reopen`() {
        val generatedSettings = ChannelSettings(name = "custom", psk = Channel.getRandomKey())

        val update =
            generatedSettings.applyChannelNameEdit(
                nameInput = "",
                pskEditState = ChannelPskEditState(canGeneratePskForName = true, generatedPskForName = true),
            )

        assertEquals("", update.settings.name)
        assertEquals(Channel.default.settings.psk, update.settings.psk)
        assertFalse(update.pskEditState.generatedPskForName)
    }
}
