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
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig

/**
 * The LoRa config a Replace import writes: the incoming config, keeping the device's own `config_ok_to_mqtt` and
 * `tx_power` so a shared link cannot raise a hardware-specific power limit. Every other field is overwritten.
 */
internal fun loraConfigForReplace(incoming: LoRaConfig, current: LoRaConfig?): LoRaConfig = incoming
    .newBuilder()
    .also { wb ->
        wb.config_ok_to_mqtt = current?.config_ok_to_mqtt ?: false
        wb.tx_power = current?.tx_power ?: 0
    }
    .build()

/** The channel set a Replace import hands to `importChannelSet`; its LoRa config is [loraConfigForReplace]. */
internal fun ChannelSet.forReplace(current: LoRaConfig?): ChannelSet =
    newBuilder().also { wb -> wb.lora_config = lora_config?.let { loraConfigForReplace(it, current) } }.build()

/** One LoRa setting the import will change, with the value on the radio now and the value after the import. */
internal data class LoraFieldChange(
    val label: StringResource,
    val from: Any?,
    val to: Any?,
    /** True when the change stops or alters transmission and is shown ahead of the rest in the error color. */
    val alertsTransmission: Boolean,
)

private class LoraFieldSpec(
    val label: StringResource,
    val read: (LoRaConfig) -> Any?,
    val alertsTransmission: (from: Any?, to: Any?) -> Boolean = { _, _ -> false },
)

/** Every proto field of [LoRaConfig], so the summary never relies on a hand-picked subset. */
private val loraFieldSpecs: List<LoraFieldSpec> =
    listOf(
        LoraFieldSpec(Res.string.schema_lora_use_preset, { it.use_preset }),
        LoraFieldSpec(Res.string.schema_lora_modem_preset, { it.modem_preset }),
        LoraFieldSpec(Res.string.schema_lora_bandwidth, { it.bandwidth }),
        LoraFieldSpec(Res.string.schema_lora_spread_factor, { it.spread_factor }),
        LoraFieldSpec(Res.string.schema_lora_coding_rate, { it.coding_rate }),
        LoraFieldSpec(Res.string.lora_field_frequency_offset, { it.frequency_offset }),
        LoraFieldSpec(Res.string.schema_lora_region, { it.region }),
        LoraFieldSpec(Res.string.schema_lora_hop_limit, { it.hop_limit }),
        LoraFieldSpec(
            Res.string.schema_lora_tx_enabled,
            { it.tx_enabled },
            alertsTransmission = { _, to -> to == false },
        ),
        LoraFieldSpec(Res.string.schema_lora_tx_power, { it.tx_power }),
        LoraFieldSpec(Res.string.schema_lora_channel_num, { it.channel_num }),
        LoraFieldSpec(
            Res.string.schema_lora_override_duty_cycle,
            { it.override_duty_cycle },
            alertsTransmission = { _, to -> to == true },
        ),
        LoraFieldSpec(Res.string.schema_lora_sx126x_rx_boosted_gain, { it.sx126x_rx_boosted_gain }),
        LoraFieldSpec(Res.string.schema_lora_override_frequency, { it.override_frequency }),
        LoraFieldSpec(Res.string.schema_lora_pa_fan_disabled, { it.pa_fan_disabled }),
        LoraFieldSpec(
            Res.string.schema_lora_ignore_incoming,
            { it.ignore_incoming },
            alertsTransmission = { _, to -> (to as? List<*>)?.isNotEmpty() == true },
        ),
        LoraFieldSpec(Res.string.schema_lora_ignore_mqtt, { it.ignore_mqtt }),
        LoraFieldSpec(Res.string.schema_lora_config_ok_to_mqtt, { it.config_ok_to_mqtt }),
        LoraFieldSpec(Res.string.lora_field_fem_lna_mode, { it.fem_lna_mode }),
        LoraFieldSpec(Res.string.lora_field_serial_hal_only, { it.serial_hal_only }),
        LoraFieldSpec(Res.string.lora_field_transparent_mode, { it.transparent_mode }),
    )

/**
 * Every field that differs between [current] and [applied], transmission-affecting changes first.
 *
 * [applied] must be the config that is written (see [loraConfigForReplace]). Any difference the field list above does
 * not explain, such as a field added to the proto later, is reported as one unrecognized-settings row rather than
 * dropped.
 */
internal fun loraConfigChanges(current: LoRaConfig?, applied: LoRaConfig): List<LoraFieldChange> {
    val before = current ?: LoRaConfig.Builder().build()
    val named = loraFieldSpecs.mapNotNull { spec ->
        val from = spec.read(before)
        val to = spec.read(applied)
        if (from == to) null else LoraFieldChange(spec.label, from, to, spec.alertsTransmission(from, to))
    }
    val explained =
        before
            .newBuilder()
            .also { wb ->
                wb.use_preset = applied.use_preset
                wb.modem_preset = applied.modem_preset
                wb.bandwidth = applied.bandwidth
                wb.spread_factor = applied.spread_factor
                wb.coding_rate = applied.coding_rate
                wb.frequency_offset = applied.frequency_offset
                wb.region = applied.region
                wb.hop_limit = applied.hop_limit
                wb.tx_enabled = applied.tx_enabled
                wb.tx_power = applied.tx_power
                wb.channel_num = applied.channel_num
                wb.override_duty_cycle = applied.override_duty_cycle
                wb.sx126x_rx_boosted_gain = applied.sx126x_rx_boosted_gain
                wb.override_frequency = applied.override_frequency
                wb.pa_fan_disabled = applied.pa_fan_disabled
                wb.ignore_incoming = applied.ignore_incoming
                wb.ignore_mqtt = applied.ignore_mqtt
                wb.config_ok_to_mqtt = applied.config_ok_to_mqtt
                wb.fem_lna_mode = applied.fem_lna_mode
                wb.serial_hal_only = applied.serial_hal_only
                wb.transparent_mode = applied.transparent_mode
            }
            .build()
    val unrecognized =
        if (explained == applied) {
            emptyList()
        } else {
            listOf(LoraFieldChange(Res.string.lora_field_unrecognized, null, null, alertsTransmission = false))
        }
    return named.sortedByDescending { it.alertsTransmission } + unrecognized
}

/** What an imported channel shares or forwards, as written to the radio. */
internal data class ChannelSharing(val uplink: Boolean, val downlink: Boolean, val positionBits: Int) {
    val isNotable: Boolean
        get() = uplink || downlink || positionBits > 0
}

/** The sharing flags an imported channel is written with; [onWire] is the settings after position precision capping. */
internal fun channelSharing(onWire: ChannelSettings): ChannelSharing = ChannelSharing(
    uplink = onWire.uplink_enabled,
    downlink = onWire.downlink_enabled,
    positionBits = onWire.module_settings?.position_precision ?: 0,
)
