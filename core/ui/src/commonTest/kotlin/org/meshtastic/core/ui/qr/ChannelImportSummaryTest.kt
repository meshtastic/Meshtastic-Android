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
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disabled
import org.meshtastic.core.resources.lora_fem_lna_not_present
import org.meshtastic.core.resources.lora_field_fem_lna_mode
import org.meshtastic.core.resources.lora_field_frequency_offset
import org.meshtastic.core.resources.lora_field_modem_preset
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
import org.meshtastic.core.resources.schema_lora_modempreset_long_fast
import org.meshtastic.core.resources.schema_lora_modempreset_short_fast
import org.meshtastic.core.resources.schema_lora_override_duty_cycle
import org.meshtastic.core.resources.schema_lora_override_frequency
import org.meshtastic.core.resources.schema_lora_pa_fan_disabled
import org.meshtastic.core.resources.schema_lora_region
import org.meshtastic.core.resources.schema_lora_regioncode_eu_868
import org.meshtastic.core.resources.schema_lora_regioncode_unset
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
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelImportSummaryTest {
    private fun lora(block: LoRaConfig.Builder.() -> Unit = {}) = LoRaConfig.Builder().also(block).build()

    private fun flag(on: Boolean) = LoraValue.Flag(on)

    private data class FieldCase(
        val label: StringResource,
        val from: LoraValue,
        val to: LoraValue,
        val change: LoRaConfig.Builder.() -> Unit,
        val base: LoRaConfig.Builder.() -> Unit = {},
        /** Other rows that change with this field: the preset needs use_preset and the slot yields to an override. */
        val companions: Int = 0,
    )

    // One case per field of LoRaConfig in the pinned protobufs. Expected values follow the proto comments and the
    // firmware (`RadioInterface.cpp`, `MeshRadio.h`), not a run of the code.
    private val fieldCases =
        listOf(
            FieldCase(
                Res.string.schema_lora_use_preset,
                flag(false),
                flag(true),
                { use_preset = true },
                companions = 1,
            ),
            FieldCase(
                Res.string.lora_field_modem_preset,
                LoraValue.Label(Res.string.schema_lora_modempreset_long_fast),
                LoraValue.Label(Res.string.schema_lora_modempreset_short_fast),
                { modem_preset = LoRaConfig.ModemPreset.SHORT_FAST },
                base = { use_preset = true },
            ),
            FieldCase(
                Res.string.schema_lora_bandwidth,
                LoraValue.Default,
                LoraValue.Kilohertz("250"),
                { bandwidth = 250 },
            ),
            FieldCase(
                Res.string.schema_lora_spread_factor,
                LoraValue.Default,
                LoraValue.Count(9),
                { spread_factor = 9 },
            ),
            FieldCase(
                Res.string.schema_lora_coding_rate,
                LoraValue.Default,
                LoraValue.CodingRate(5),
                { coding_rate = 5 },
            ),
            FieldCase(
                Res.string.lora_field_frequency_offset,
                LoraValue.Default,
                LoraValue.Megahertz("0.250000"),
                { frequency_offset = 0.25f },
            ),
            FieldCase(
                Res.string.schema_lora_region,
                LoraValue.Label(Res.string.schema_lora_regioncode_unset),
                LoraValue.Label(Res.string.schema_lora_regioncode_eu_868),
                { region = LoRaConfig.RegionCode.EU_868 },
            ),
            FieldCase(Res.string.schema_lora_hop_limit, LoraValue.Count(0), LoraValue.Count(7), { hop_limit = 7 }),
            FieldCase(Res.string.schema_lora_tx_enabled, flag(false), flag(true), { tx_enabled = true }),
            FieldCase(Res.string.schema_lora_tx_power, LoraValue.Default, LoraValue.Dbm(20), { tx_power = 20 }),
            FieldCase(Res.string.schema_lora_channel_num, LoraValue.Auto, LoraValue.Count(12), { channel_num = 12 }),
            FieldCase(
                Res.string.schema_lora_override_duty_cycle,
                flag(false),
                flag(true),
                { override_duty_cycle = true },
            ),
            FieldCase(
                Res.string.schema_lora_sx126x_rx_boosted_gain,
                flag(false),
                flag(true),
                { sx126x_rx_boosted_gain = true },
            ),
            FieldCase(
                Res.string.schema_lora_override_frequency,
                LoraValue.Auto,
                LoraValue.Megahertz("906.875"),
                { override_frequency = 906.875f },
                companions = 1,
            ),
            FieldCase(Res.string.schema_lora_pa_fan_disabled, flag(false), flag(true), { pa_fan_disabled = true }),
            FieldCase(
                Res.string.schema_lora_ignore_incoming,
                LoraValue.Nodes(emptyList()),
                LoraValue.Nodes(listOf("!00000007")),
                { ignore_incoming = listOf(7) },
            ),
            FieldCase(Res.string.schema_lora_ignore_mqtt, flag(false), flag(true), { ignore_mqtt = true }),
            FieldCase(Res.string.schema_lora_config_ok_to_mqtt, flag(false), flag(true), { config_ok_to_mqtt = true }),
            FieldCase(
                Res.string.lora_field_fem_lna_mode,
                LoraValue.Label(Res.string.disabled),
                LoraValue.Label(Res.string.lora_fem_lna_not_present),
                { fem_lna_mode = LoRaConfig.FEM_LNA_Mode.NOT_PRESENT },
            ),
            FieldCase(Res.string.lora_field_serial_hal_only, flag(false), flag(true), { serial_hal_only = true }),
            FieldCase(Res.string.lora_field_transparent_mode, flag(false), flag(true), { transparent_mode = true }),
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

            assertEquals(1 + case.companions, changes.size, "${case.to}: unexpected rows $changes")
            val change = changes.first { it.label == case.label }
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
    fun a_slot_that_goes_back_to_zero_is_reported_as_automatic() {
        val changes = loraConfigChanges(lora { channel_num = 12 }, lora { channel_num = 0 })

        assertEquals(LoraValue.Count(12), changes.single().from)
        assertEquals(LoraValue.Auto, changes.single().to)
    }

    @Test
    fun a_frequency_override_makes_the_slot_unused_and_is_shown_with_its_unit_value() {
        val changes =
            loraConfigChanges(
                lora { channel_num = 12 },
                lora {
                    channel_num = 12
                    override_frequency = 915.5f
                },
            )

        assertEquals(
            listOf(Res.string.schema_lora_channel_num, Res.string.schema_lora_override_frequency),
            changes.map { it.label },
        )
        assertEquals(LoraValue.NotUsed, changes[0].to)
        assertEquals(LoraValue.Megahertz("915.500"), changes[1].to)
    }

    @Test
    fun manual_modem_fields_are_not_reported_while_the_preset_is_in_use() {
        val changes =
            loraConfigChanges(
                lora { use_preset = true },
                lora {
                    use_preset = true
                    bandwidth = 250
                    spread_factor = 9
                },
            )

        assertTrue(changes.isEmpty(), "firmware ignores bandwidth and spread factor with use_preset: $changes")
    }

    @Test
    fun the_preset_is_not_used_once_manual_modem_fields_take_over() {
        val changes = loraConfigChanges(lora { use_preset = true }, lora { use_preset = false })

        assertEquals(
            listOf(Res.string.schema_lora_use_preset, Res.string.lora_field_modem_preset),
            changes.map { it.label },
        )
        assertEquals(LoraValue.NotUsed, changes[1].to)
    }

    @Test
    fun bandwidth_codes_show_the_kilohertz_the_slot_math_uses() {
        fun shown(code: Int) = loraConfigChanges(lora { use_preset = false }, lora { bandwidth = code }).single().to

        assertEquals(LoraValue.Kilohertz("31.25"), shown(31))
        assertEquals(LoraValue.Kilohertz("203.125"), shown(200))
        assertEquals(LoraValue.Kilohertz("812.5"), shown(800))
        assertEquals(LoraValue.Kilohertz("125"), shown(125))
    }

    @Test
    fun a_coding_rate_outside_four_to_eight_leaves_the_default_in_place() {
        assertTrue(loraConfigChanges(lora(), lora { coding_rate = 9 }).isEmpty())
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
        assertTrue(changes.take(3).all { it.affectsTransmission })
        assertTrue(changes.drop(3).none { it.affectsTransmission })
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
        assertFalse(changes.any { it.affectsTransmission })
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

    private fun sharingSettings(precisionBits: Int, uplink: Boolean = false, downlink: Boolean = false, psk: Byte = 7) =
        ChannelSettings.Builder()
            .also { wb ->
                wb.name = "Field"
                wb.psk = byteArrayOf(psk).toByteString()
                wb.uplink_enabled = uplink
                wb.downlink_enabled = downlink
                wb.module_settings = ModuleSettings.Builder().also { it.position_precision = precisionBits }.build()
            }
            .build()

    @Test
    fun position_sharing_is_none_approximate_or_precise_by_precision_bits() {
        // A 32-byte-class key is not publicly decryptable, so the precision is written as received.
        val full = ByteArray(32) { 3 }.toByteString()
        fun settings(bits: Int) = sharingSettings(bits).newBuilder().also { it.psk = full }.build()

        assertEquals(
            listOf(PositionSharing.None, PositionSharing.Approximate, PositionSharing.Precise),
            importedChannelSharing(listOf(settings(0), settings(13), settings(32))).map { it.position },
        )
    }

    @Test
    fun a_channel_on_a_public_key_is_not_described_as_sharing_precise_position() {
        // The import caps position precision on a publicly decryptable key, so the summary must describe the capped
        // value that is written, not the 32 bits the link asked for.
        val publicKey = sharingSettings(precisionBits = 32, psk = 1)

        assertEquals(PositionSharing.Approximate, importedChannelSharing(listOf(publicKey)).single().position)
    }

    @Test
    fun deselecting_the_primary_describes_the_rows_after_it_by_the_key_they_then_inherit() {
        // A channel with no key of its own takes the first written channel's key, and precision is capped only on a
        // publicly decryptable key (Channel.usesPublicKey).
        val publicPrimary = sharingSettings(precisionBits = 32, psk = 1)
        val privateChannel =
            sharingSettings(precisionBits = 32)
                .newBuilder()
                .also { it.psk = ByteArray(32) { 3 }.toByteString() }
                .build()
        val inheriting =
            sharingSettings(precisionBits = 32).newBuilder().also { it.psk = ByteArray(0).toByteString() }.build()
        val settings = listOf(publicPrimary, privateChannel, inheriting)

        assertEquals(
            PositionSharing.Approximate,
            importedChannelSharing(settings, listOf(true, true, true))[2].position,
        )
        assertEquals(
            PositionSharing.Precise,
            importedChannelSharing(settings, listOf(false, true, true))[2].position,
        )
    }

    @Test
    fun a_row_after_a_deselected_one_is_described_by_its_own_settings() {
        val key = ByteArray(32) { 3 }.toByteString()
        fun private(uplink: Boolean) = sharingSettings(0, uplink = uplink).newBuilder().also { it.psk = key }.build()
        val settings = listOf(private(uplink = false), private(uplink = true), private(uplink = false))

        val sharing = importedChannelSharing(settings, listOf(false, true, true))

        assertEquals(listOf(false, true, false), sharing.map { it.uplink })
    }

    @Test
    fun an_unselected_row_shows_what_selecting_it_would_write() {
        val privateChannel =
            sharingSettings(precisionBits = 32)
                .newBuilder()
                .also { it.psk = ByteArray(32) { 3 }.toByteString() }
                .build()
        val settings = listOf(privateChannel, sharingSettings(0, uplink = true))

        val sharing = importedChannelSharing(settings, listOf(true, false))

        assertEquals(PositionSharing.Precise, sharing[0].position)
        assertTrue(sharing[1].uplink)
    }

    @Test
    fun uplink_and_downlink_are_reported_per_channel() {
        val sharing =
            importedChannelSharing(listOf(sharingSettings(0, uplink = true), sharingSettings(0, downlink = true)))

        assertEquals(listOf(true, false), sharing.map { it.uplink })
        assertEquals(listOf(false, true), sharing.map { it.downlink })
        assertTrue(sharing.all { it.isNotable })
    }
}
