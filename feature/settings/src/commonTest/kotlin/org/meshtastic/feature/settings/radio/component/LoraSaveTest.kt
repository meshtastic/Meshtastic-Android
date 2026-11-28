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
package org.meshtastic.feature.settings.radio.component

import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class LoraSaveTest {

    private fun lora(transparent: Boolean, hopLimit: Int = 3) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.hop_limit = hopLimit
            wb.tx_enabled = true
            wb.transparent_mode = transparent
        }
        .build()

    private fun config(lora: Config.LoRaConfig) = Config.Builder().also { wb -> wb.lora = lora }.build()

    @Test
    fun `turning it off on the local node saves the other edits with the flag still on and hands off the turn-off`() {
        val save = loraSaveFor(isLocal = true, reported = lora(true), saved = lora(false, hopLimit = 5))

        assertEquals(LoraSave(config(lora(true, hopLimit = 5)), turnsOn = false, turnOffAfter = true), save)
    }

    @Test
    fun `turning it on on the local node saves the form as it is and counts as the choice of the user`() {
        val save = loraSaveFor(isLocal = true, reported = lora(false), saved = lora(true, hopLimit = 5))

        assertEquals(LoraSave(config(lora(true, hopLimit = 5)), turnsOn = true, turnOffAfter = false), save)
    }

    @Test
    fun `a save that leaves the flag alone or targets a remote node is sent as the form has it`() {
        val edited = lora(true, hopLimit = 5)
        val remoteOff = lora(false, hopLimit = 5)

        assertEquals(
            LoraSave(config(edited), turnsOn = false, turnOffAfter = false),
            loraSaveFor(isLocal = true, reported = lora(true), saved = edited),
        )
        assertEquals(
            LoraSave(config(remoteOff), turnsOn = false, turnOffAfter = false),
            loraSaveFor(isLocal = false, reported = lora(true), saved = remoteOff),
        )
    }
}
