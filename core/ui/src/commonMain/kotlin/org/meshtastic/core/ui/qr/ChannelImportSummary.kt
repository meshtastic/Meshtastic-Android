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
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.loraBandwidthMegahertz
import org.meshtastic.core.model.schemaLabelRes
import org.meshtastic.core.model.util.withOnWirePrecision
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disabled
import org.meshtastic.core.resources.enabled
import org.meshtastic.core.resources.lora_fem_lna_auto
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
import org.meshtastic.core.resources.schema_lora_override_duty_cycle
import org.meshtastic.core.resources.schema_lora_override_frequency
import org.meshtastic.core.resources.schema_lora_pa_fan_disabled
import org.meshtastic.core.resources.schema_lora_region
import org.meshtastic.core.resources.schema_lora_spread_factor
import org.meshtastic.core.resources.schema_lora_sx126x_rx_boosted_gain
import org.meshtastic.core.resources.schema_lora_tx_enabled
import org.meshtastic.core.resources.schema_lora_tx_power
import org.meshtastic.core.resources.schema_lora_use_preset
import org.meshtastic.core.ui.component.PRECISE_POSITION_BITS
import org.meshtastic.core.ui.util.formatFrequencyMhz
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

/**
 * A LoRa setting as the person reading the summary should understand it, not as the wire stores it. A zero that means
 * "the radio decides" is [Auto] or [Default], never a number, and a field the firmware ignores in the current
 * configuration is [NotUsed], so two configs that behave the same never produce a row.
 */
internal sealed interface LoraValue {
    data class Flag(val on: Boolean) : LoraValue

    /** An enum value shown through its own string resource. */
    data class Label(val res: StringResource) : LoraValue

    /** An enum value the schema has no label for, shown by name. */
    data class Text(val text: String) : LoraValue

    /** A plain count: hops, a frequency slot, a spread factor. */
    data class Count(val value: Int) : LoraValue

    /** [text] is the number as `NumberFormatter.formatInvariant` prints it, shown with the MHz unit. */
    data class Megahertz(val text: String) : LoraValue

    /** [text] is the number with no trailing zeros, shown with the kHz unit. */
    data class Kilohertz(val text: String) : LoraValue

    data class Dbm(val value: Int) : LoraValue

    /** The denominator `n` of a 4/n coding rate. */
    data class CodingRate(val denominator: Int) : LoraValue

    /** Node ids in the form the rest of the app shows, empty when the list is empty. */
    data class Nodes(val ids: List<String>) : LoraValue

    /** Zero that means the radio picks: the slot from the channel name, or no frequency override. */
    data object Auto : LoraValue

    /** Zero that means the preset's or the region's value. */
    data object Default : LoraValue

    /** A field the firmware ignores while another setting is in effect. */
    data object NotUsed : LoraValue
}

/** One LoRa setting the import will change; [from] and [to] are null only for the row about unrecognized fields. */
internal data class LoraFieldChange(
    val label: StringResource,
    val from: LoraValue?,
    val to: LoraValue?,
    /** True when the change affects whether, how much, or from whom the radio sends and receives. */
    val affectsTransmission: Boolean,
)

private class LoraFieldSpec(
    val label: StringResource,
    val read: (LoRaConfig) -> LoraValue,
    val affectsTransmission: (to: LoraValue) -> Boolean = { false },
)

private const val OFFSET_DECIMALS = 6
private const val BANDWIDTH_DECIMALS = 3
private const val KILOHERTZ_PER_MEGAHERTZ = 1000f
private val CODING_RATE_RANGE = 4..8

private fun LoRaConfig.hasFrequencyOverride() = override_frequency != 0f

private fun trimmedNumber(value: Float): String =
    NumberFormatter.formatInvariant(value, BANDWIDTH_DECIMALS).trimEnd('0').trimEnd('.')

private fun LoRaConfig.FEM_LNA_Mode.label(): StringResource = when (this) {
    LoRaConfig.FEM_LNA_Mode.DISABLED -> Res.string.disabled
    LoRaConfig.FEM_LNA_Mode.ENABLED -> Res.string.enabled
    LoRaConfig.FEM_LNA_Mode.NOT_PRESENT -> Res.string.lora_fem_lna_not_present
    LoRaConfig.FEM_LNA_Mode.AUTO -> Res.string.lora_fem_lna_auto
}

private fun Enum<*>.labelValue(): LoraValue? = schemaLabelRes()?.let { LoraValue.Label(it) }

/**
 * Every proto field of [LoRaConfig], so the summary never relies on a hand-picked subset. The meaning of each value
 * comes from firmware (`RadioInterface.cpp`, `MeshRadio.h`) and the `LoRaConfig` proto comments.
 */
private val loraFieldSpecs: List<LoraFieldSpec> =
    listOf(
        LoraFieldSpec(Res.string.schema_lora_use_preset, { LoraValue.Flag(it.use_preset) }),
        // The preset only applies while use_preset is on; otherwise bandwidth, spread factor and coding rate rule.
        LoraFieldSpec(
            Res.string.lora_field_modem_preset,
            {
                if (it.use_preset) {
                    it.modem_preset.labelValue() ?: LoraValue.Text(it.modem_preset.name)
                } else {
                    LoraValue.NotUsed
                }
            },
        ),
        LoraFieldSpec(
            Res.string.schema_lora_bandwidth,
            {
                if (it.use_preset || it.bandwidth == 0) {
                    LoraValue.Default
                } else {
                    LoraValue.Kilohertz(trimmedNumber(loraBandwidthMegahertz(it.bandwidth) * KILOHERTZ_PER_MEGAHERTZ))
                }
            },
        ),
        LoraFieldSpec(
            Res.string.schema_lora_spread_factor,
            { if (it.use_preset || it.spread_factor == 0) LoraValue.Default else LoraValue.Count(it.spread_factor) },
        ),
        // 0, or a value outside 4 to 8, leaves the preset's or the default coding rate in place.
        LoraFieldSpec(
            Res.string.schema_lora_coding_rate,
            { if (it.coding_rate in CODING_RATE_RANGE) LoraValue.CodingRate(it.coding_rate) else LoraValue.Default },
        ),
        LoraFieldSpec(
            Res.string.lora_field_frequency_offset,
            {
                if (it.frequency_offset == 0f) {
                    LoraValue.Default
                } else {
                    LoraValue.Megahertz(NumberFormatter.formatInvariant(it.frequency_offset, OFFSET_DECIMALS))
                }
            },
        ),
        LoraFieldSpec(
            Res.string.schema_lora_region,
            { it.region.labelValue() ?: LoraValue.Text(it.region.name) },
        ),
        // 0 is a real hop limit: the node's own packets are not repeated (Router.cpp, Default.cpp).
        LoraFieldSpec(Res.string.schema_lora_hop_limit, { LoraValue.Count(it.hop_limit) }),
        LoraFieldSpec(
            Res.string.schema_lora_tx_enabled,
            { LoraValue.Flag(it.tx_enabled) },
            affectsTransmission = { to -> to == LoraValue.Flag(false) },
        ),
        // 0 uses the highest power the region allows (RadioInterface.cpp).
        LoraFieldSpec(
            Res.string.schema_lora_tx_power,
            { if (it.tx_power == 0) LoraValue.Default else LoraValue.Dbm(it.tx_power) },
        ),
        // 0 lets the region pick the slot; a frequency override replaces the slot entirely.
        LoraFieldSpec(
            Res.string.schema_lora_channel_num,
            {
                when {
                    it.hasFrequencyOverride() -> LoraValue.NotUsed
                    it.channel_num == 0 -> LoraValue.Auto
                    else -> LoraValue.Count(it.channel_num)
                }
            },
        ),
        LoraFieldSpec(
            Res.string.schema_lora_override_duty_cycle,
            { LoraValue.Flag(it.override_duty_cycle) },
            affectsTransmission = { to -> to == LoraValue.Flag(true) },
        ),
        LoraFieldSpec(Res.string.schema_lora_sx126x_rx_boosted_gain, { LoraValue.Flag(it.sx126x_rx_boosted_gain) }),
        // 0 means no override: the slot calculation decides (RadioInterface.cpp).
        LoraFieldSpec(
            Res.string.schema_lora_override_frequency,
            {
                if (it.hasFrequencyOverride()) {
                    LoraValue.Megahertz(formatFrequencyMhz(it.override_frequency))
                } else {
                    LoraValue.Auto
                }
            },
        ),
        LoraFieldSpec(Res.string.schema_lora_pa_fan_disabled, { LoraValue.Flag(it.pa_fan_disabled) }),
        LoraFieldSpec(
            Res.string.schema_lora_ignore_incoming,
            { config -> LoraValue.Nodes(config.ignore_incoming.map { NodeAddress.numToDefaultId(it) }) },
            affectsTransmission = { to -> (to as? LoraValue.Nodes)?.ids?.isNotEmpty() == true },
        ),
        LoraFieldSpec(Res.string.schema_lora_ignore_mqtt, { LoraValue.Flag(it.ignore_mqtt) }),
        LoraFieldSpec(Res.string.schema_lora_config_ok_to_mqtt, { LoraValue.Flag(it.config_ok_to_mqtt) }),
        LoraFieldSpec(Res.string.lora_field_fem_lna_mode, { LoraValue.Label(it.fem_lna_mode.label()) }),
        LoraFieldSpec(Res.string.lora_field_serial_hal_only, { LoraValue.Flag(it.serial_hal_only) }),
        LoraFieldSpec(Res.string.lora_field_transparent_mode, { LoraValue.Flag(it.transparent_mode) }),
    )

/**
 * Every setting whose effect differs between [current] and [applied], transmission-affecting changes first.
 *
 * [applied] must be the config that is written (see [loraConfigForReplace]). A difference the field list does not
 * explain, such as a field added to the proto later, is reported as one unrecognized-settings row rather than dropped.
 */
internal fun loraConfigChanges(current: LoRaConfig?, applied: LoRaConfig): List<LoraFieldChange> {
    val before = current ?: LoRaConfig.Builder().build()
    val named = loraFieldSpecs.mapNotNull { spec ->
        val from = spec.read(before)
        val to = spec.read(applied)
        if (from == to) null else LoraFieldChange(spec.label, from, to, spec.affectsTransmission(to))
    }
    val unrecognized =
        if (before.differsOnlyInKnownFields(applied)) {
            emptyList()
        } else {
            listOf(LoraFieldChange(Res.string.lora_field_unrecognized, null, null, affectsTransmission = false))
        }
    return named.sortedByDescending { it.affectsTransmission } + unrecognized
}

/** True when copying every field the summary knows about from [applied] onto this config makes them equal. */
private fun LoRaConfig.differsOnlyInKnownFields(applied: LoRaConfig): Boolean = newBuilder()
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
    .build() == applied

/** How much of the sender's position a channel shares; `position_precision` 32 is full precision, 0 shares none. */
internal enum class PositionSharing {
    None,
    Approximate,
    Precise,
}

/** What an imported channel shares or forwards, as written to the radio. */
internal data class ChannelSharing(val uplink: Boolean, val downlink: Boolean, val position: PositionSharing) {
    val isNotable: Boolean
        get() = uplink || downlink || position != PositionSharing.None
}

/** The sharing each imported channel is written with, after the position precision cap the import applies. */
internal fun importedChannelSharing(settings: List<ChannelSettings>): List<ChannelSharing> =
    settings.withOnWirePrecision().map(::channelSharing)

/**
 * The sharing each row is written with given which rows are [selected]. The precision cap depends on the first channel
 * written, so deselecting the primary changes the rows after it. An unselected row shows what selecting it would write.
 */
internal fun importedChannelSharing(settings: List<ChannelSettings>, selected: List<Boolean>): List<ChannelSharing> =
    settings.indices.map { row ->
        val written = settings.filterIndexed { index, _ -> index == row || selected.getOrNull(index) == true }
        val position = (0 until row).count { selected.getOrNull(it) == true }
        channelSharing(written.withOnWirePrecision()[position])
    }

private fun channelSharing(onWire: ChannelSettings): ChannelSharing {
    val bits = onWire.module_settings?.position_precision ?: 0
    return ChannelSharing(
        uplink = onWire.uplink_enabled,
        downlink = onWire.downlink_enabled,
        position =
        when {
            bits >= PRECISE_POSITION_BITS -> PositionSharing.Precise
            bits > 0 -> PositionSharing.Approximate
            else -> PositionSharing.None
        },
    )
}
