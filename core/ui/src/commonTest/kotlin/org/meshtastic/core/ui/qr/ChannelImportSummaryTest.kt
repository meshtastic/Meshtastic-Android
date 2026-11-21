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
package org.meshtastic.core.ui.qr

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.lora_field_fem_lna_mode
import org.meshtastic.core.resources.lora_field_frequency_offset
import org.meshtastic.core.resources.lora_field_serial_hal_only
import org.meshtastic.core.resources.lora_field_transparent_mode
import org.meshtastic.core.resources.lora_field_unrecognized
import org.meshtastic.core.resources.schema_lora_bandwidth
import org.meshtastic.core.resources.schema_lora_channel_num
import org.meshtastic.core.resources.schema_lora_coding_rate
import org.meshtastic.core.resources.schema_lora_config_ok_to_mqtt
import org.meshtastic.core.resources.schema_lora_hop_limit
import org.meshtastic.core.resources.schema_lora_ignore_incoming
import org.meshtastic.core.resources.schema_lora_ignore_mqtt
import org.meshtastic.core.resources.schema_lora_modem_preset
import org.meshtastic.core.resources.schema_lora_override_duty_cycle
import org.meshtastic.core.resources.schema_lora_override_frequency
import org.meshtastic.core.resources.schema_lora_pa_fan_disabled
import org.meshtastic.core.resources.schema_lora_region
import org.meshtastic.core.resources.schema_lora_spread_factor
import org.meshtastic.core.resources.schema_lora_sx126x_rx_boosted_gain
import org.meshtastic.core.resources.schema_lora_tx_enabled
import org.meshtastic.core.resources.schema_lora_tx_power
import org.meshtastic.core.resources.schema_lora_use_preset
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.ui.util.importChannelSet
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.LocalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelImportSummaryTest {
    private fun lora(block: LoRaConfig.Builder.() -> Unit = {}) = LoRaConfig.Builder().also(block).build()

    private data class FieldCase(
        val label: StringResource,
        val from: Any,
        val to: Any,
        val change: LoRaConfig.Builder.() -> Unit,
        val base: LoRaConfig.Builder.() -> Unit = {},
    )

    // One case per field of LoRaConfig in the pinned protobufs; the expected values are the ones set here.
    private val fieldCases =
        listOf(
            FieldCase(Res.string.schema_lora_use_preset, false, true, { use_preset = true }),
            FieldCase(
                Res.string.schema_lora_modem_preset,
                LoRaConfig.ModemPreset.LONG_FAST,
                LoRaConfig.ModemPreset.SHORT_FAST,
                { modem_preset = LoRaConfig.ModemPreset.SHORT_FAST },
            ),
            FieldCase(Res.string.schema_lora_bandwidth, 0, 250, { bandwidth = 250 }),
            FieldCase(Res.string.schema_lora_spread_factor, 0, 9, { spread_factor = 9 }),
            FieldCase(Res.string.schema_lora_coding_rate, 0, 5, { coding_rate = 5 }),
            FieldCase(Res.string.lora_field_frequency_offset, 0f, 1.5f, { frequency_offset = 1.5f }),
            FieldCase(
                Res.string.schema_lora_region,
                LoRaConfig.RegionCode.UNSET,
                LoRaConfig.RegionCode.EU_868,
                { region = LoRaConfig.RegionCode.EU_868 },
            ),
            FieldCase(Res.string.schema_lora_hop_limit, 0, 7, { hop_limit = 7 }),
            FieldCase(Res.string.schema_lora_tx_enabled, false, true, { tx_enabled = true }),
            FieldCase(Res.string.schema_lora_tx_power, 0, 20, { tx_power = 20 }),
            FieldCase(Res.string.schema_lora_channel_num, 0, 12, { channel_num = 12 }),
            FieldCase(Res.string.schema_lora_override_duty_cycle, false, true, { override_duty_cycle = true }),
            FieldCase(Res.string.schema_lora_sx126x_rx_boosted_gain, false, true, { sx126x_rx_boosted_gain = true }),
            FieldCase(Res.string.schema_lora_override_frequency, 0f, 906.875f, { override_frequency = 906.875f }),
            FieldCase(Res.string.schema_lora_pa_fan_disabled, false, true, { pa_fan_disabled = true }),
            FieldCase(
                Res.string.schema_lora_ignore_incoming,
                emptyList<Int>(),
                listOf(7),
                { ignore_incoming = listOf(7) },
            ),
            FieldCase(Res.string.schema_lora_ignore_mqtt, false, true, { ignore_mqtt = true }),
            FieldCase(Res.string.schema_lora_config_ok_to_mqtt, false, true, { config_ok_to_mqtt = true }),
            FieldCase(
                Res.string.lora_field_fem_lna_mode,
                LoRaConfig.FEM_LNA_Mode.DISABLED,
                LoRaConfig.FEM_LNA_Mode.ENABLED,
                { fem_lna_mode = LoRaConfig.FEM_LNA_Mode.ENABLED },
            ),
            FieldCase(Res.string.lora_field_serial_hal_only, false, true, { serial_hal_only = true }),
            FieldCase(Res.string.lora_field_transparent_mode, false, true, { transparent_mode = true }),
        )

    @Test
    fun every_lora_field_is_reported_alone_with_old_and_new_value() {
        for (case in fieldCases) {
            val current = lora { case.base(this) }
            val applied = lora {
                case.base(this)
                case.change(this)
            }

            val changes = loraConfigChanges(current, applied)

            val change = changes.singleOrNull() ?: error("${case.to}: expected exactly one row, got $changes")
            assertEquals(case.label, change.label)
            assertEquals(case.from, change.from)
            assertEquals(case.to, change.to)
        }
    }

    @Test
    fun an_unchanged_config_reports_nothing() {
        val config = lora { hop_limit = 5 }
        assertTrue(loraConfigChanges(config, config).isEmpty())
    }

    @Test
    fun a_set_field_that_goes_unset_reports_the_old_value() {
        val changes = loraConfigChanges(lora { channel_num = 12 }, lora { channel_num = 0 })

        assertEquals(12, changes.single().from)
        assertEquals(0, changes.single().to)
    }

    @Test
    fun transmission_changes_come_first_and_are_flagged() {
        val current = lora { tx_enabled = true }
        val applied = lora {
            hop_limit = 7
            tx_enabled = false
            override_duty_cycle = true
            ignore_incoming = listOf(42)
            channel_num = 3
        }

        val changes = loraConfigChanges(current, applied)

        assertEquals(5, changes.size)
        assertEquals(
            setOf(
                Res.string.schema_lora_tx_enabled,
                Res.string.schema_lora_override_duty_cycle,
                Res.string.schema_lora_ignore_incoming,
            ),
            changes.take(3).map { it.label }.toSet(),
        )
        assertTrue(changes.take(3).all { it.alertsTransmission })
        assertTrue(changes.drop(3).none { it.alertsTransmission })
    }

    @Test
    fun turning_transmit_on_or_duty_cycle_override_off_is_not_flagged() {
        val changes =
            loraConfigChanges(
                lora { override_duty_cycle = true },
                lora {
                    tx_enabled = true
                    override_duty_cycle = false
                },
            )

        assertEquals(2, changes.size)
        assertFalse(changes.any { it.alertsTransmission })
    }

    @Test
    fun a_field_the_list_does_not_know_still_produces_a_row() {
        // Field 99 stands in for a proto field added after this list was written: it only survives as unknownFields.
        val withUnknown =
            Buffer()
                .also { buffer ->
                    buffer.write(LoRaConfig.ADAPTER.encode(lora { hop_limit = 3 }))
                    // Tag (99 shl 3) as a two-byte varint, then the varint value 1.
                    buffer.writeByte(0x98)
                    buffer.writeByte(0x06)
                    buffer.writeByte(1)
                }
                .readByteArray()
                .let { LoRaConfig.ADAPTER.decode(it) }

        val changes = loraConfigChanges(lora { hop_limit = 3 }, withUnknown)

        assertEquals(listOf(Res.string.lora_field_unrecognized.key), changes.map { it.label.key })
    }

    @Test
    fun replace_keeps_the_device_mqtt_consent_and_tx_power_and_overwrites_the_rest() {
        val current = lora {
            tx_power = 17
            config_ok_to_mqtt = true
            hop_limit = 3
        }
        val incoming = lora {
            tx_power = 30
            config_ok_to_mqtt = false
            hop_limit = 7
            override_frequency = 906.875f
            tx_enabled = true
        }

        val applied = loraConfigForReplace(incoming, current)

        assertEquals(17, applied.tx_power)
        assertTrue(applied.config_ok_to_mqtt)
        assertEquals(
            incoming
                .newBuilder()
                .also {
                    it.tx_power = 17
                    it.config_ok_to_mqtt = true
                }
                .build(),
            applied,
        )
    }

    @Test
    fun the_summary_describes_exactly_the_config_the_import_writes() = runTest {
        val current = lora {
            tx_power = 17
            config_ok_to_mqtt = true
            tx_enabled = true
            hop_limit = 3
        }
        val incoming =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(ChannelSettings.Builder().also { it.name = "Shared" }.build())
                    wb.lora_config = lora {
                        tx_power = 30
                        config_ok_to_mqtt = false
                        tx_enabled = false
                        override_frequency = 906.875f
                        channel_num = 12
                        hop_limit = 3
                    }
                }
                .build()
        val radioController = FakeRadioController()
        val radioConfigRepository =
            FakeRadioConfigRepository().apply {
                setLocalConfigDirect(LocalConfig.Builder().also { it.lora = current }.build())
            }

        importChannelSet(incoming.forReplace(current), radioController, radioConfigRepository)

        val written = radioController.localConfigs.single().lora ?: error("LoRa config was not written")
        val summarized = loraConfigChanges(current, loraConfigForReplace(incoming.lora_config!!, current))
        assertEquals(loraConfigChanges(current, written), summarized)
        assertEquals(
            setOf(
                Res.string.schema_lora_tx_enabled,
                Res.string.schema_lora_override_frequency,
                Res.string.schema_lora_channel_num,
            ),
            summarized.map { it.label }.toSet(),
        )
    }
}
