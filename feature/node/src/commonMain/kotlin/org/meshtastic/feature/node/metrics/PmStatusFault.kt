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
package org.meshtastic.feature.node.metrics

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.pm_status_co2_error
import org.meshtastic.core.resources.pm_status_fan_error
import org.meshtastic.core.resources.pm_status_fan_speed_warning
import org.meshtastic.core.resources.pm_status_gas_error
import org.meshtastic.core.resources.pm_status_hcho_error
import org.meshtastic.core.resources.pm_status_pm_error
import org.meshtastic.core.resources.pm_status_rht_error

/**
 * One bit of `AirQualityMetrics.pm_status_flags`, the PM sensor's raw status register. The bit layout is the SEN6X
 * family's, as the proto documents it; the register is passed through unchanged, so other sensors may set other bits.
 */
@Suppress("MagicNumber")
internal enum class PmStatusFault(val bit: Int, val labelRes: StringResource) {
    FAN_ERROR(4, Res.string.pm_status_fan_error),
    RHT_ERROR(6, Res.string.pm_status_rht_error),
    GAS_ERROR(7, Res.string.pm_status_gas_error),
    CO2_ERROR_SEN66(9, Res.string.pm_status_co2_error),
    HCHO_ERROR(10, Res.string.pm_status_hcho_error),
    PM_ERROR(11, Res.string.pm_status_pm_error),
    CO2_ERROR_SEN63C(12, Res.string.pm_status_co2_error),
    FAN_SPEED_WARNING(21, Res.string.pm_status_fan_speed_warning),
    ;

    val mask: Int
        get() = 1 shl bit

    /** True for the bits the datasheet calls a warning rather than a fault; the sensor keeps reporting through them. */
    val isWarning: Boolean
        get() = this == FAN_SPEED_WARNING

    companion object {
        private const val HEX_RADIX = 16

        /** The faults set in [flags], in bit order. Two bits sharing a label report that label once. */
        fun decode(flags: Int): List<PmStatusFault> =
            entries.filter { flags and it.mask != 0 }.distinctBy { it.labelRes }

        /** The bits of [flags] no [PmStatusFault] names, as a bitmask; 0 when every set bit is known. */
        fun unknownBits(flags: Int): Int = entries.fold(flags) { rest, fault -> rest and fault.mask.inv() }

        /**
         * [unknownBits] as a hex bitmask for display, or null when every set bit is a named fault. The register is
         * uint32 on the wire, so bit 31 renders as `80000000`, not a negative number.
         */
        fun unknownBitsHex(flags: Int): String? = unknownBits(flags).takeIf { it != 0 }?.toUInt()?.toString(HEX_RADIX)
    }
}
