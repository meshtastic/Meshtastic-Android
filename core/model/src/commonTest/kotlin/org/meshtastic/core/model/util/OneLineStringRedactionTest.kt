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
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MyNodeInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneLineStringRedactionTest {

    // Not valid UTF-8, so okio renders it as hex rather than text.
    private val secret = ByteArray(16) { 0xFF.toByte() }.toByteString()

    @Test
    fun `channel psk is redacted`() {
        val channel =
            Channel.Builder()
                .also { wb ->
                    wb.settings =
                        ChannelSettings.Builder()
                            .also { cs ->
                                cs.psk = secret
                                cs.name = "Primary"
                            }
                            .build()
                }
                .build()

        val line = channel.toOneLineString()

        assertTrue(line.contains("psk=[REDACTED]"), line)
        assertFalse(line.contains(secret.hex()), line)
        assertTrue(line.contains("Primary"), line)
    }

    @Test
    fun `security keys are redacted from config`() {
        val config =
            Config.Builder()
                .also { wb ->
                    wb.security = Config.SecurityConfig.Builder().also { sc -> sc.private_key = secret }.build()
                }
                .build()

        val line = config.toOneLineString()

        assertTrue(line.contains("private_key=[REDACTED]"), line)
        assertFalse(line.contains(secret.hex()), line)
    }

    @Test
    fun `mqtt credentials are redacted from module config`() {
        val config =
            ModuleConfig.Builder()
                .also { wb ->
                    wb.mqtt =
                        ModuleConfig.MQTTConfig.Builder()
                            .also { mq ->
                                mq.username = "meshuser"
                                mq.password = "hunter2"
                            }
                            .build()
                }
                .build()

        val line = config.toOneLineString()

        assertFalse(line.contains("meshuser"), line)
        assertFalse(line.contains("hunter2"), line)
    }

    @Test
    fun `device id is redacted from my node info`() {
        val line = MyNodeInfo.Builder().also { wb -> wb.device_id = secret }.build().toOneLineString()

        assertTrue(line.contains("device_id=[REDACTED]"), line)
        assertFalse(line.contains(secret.hex()), line)
        assertFalse(line.contains('\n'), line)
    }
}
