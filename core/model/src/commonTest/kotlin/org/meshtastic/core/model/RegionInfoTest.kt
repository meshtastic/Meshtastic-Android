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
package org.meshtastic.core.model

import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertNotNull

class RegionInfoTest {

    /**
     * Every region the firmware's region table defines must be selectable in the app. The proto enum is a superset
     * (e.g. EU_874, EU_917, UA_868 have no firmware entry), so parity is asserted against this explicit list.
     */
    @Test
    fun ensure_firmware_regions_are_mapped_in_RegionInfo() {
        val firmwareRegions =
            listOf(
                RegionCode.US,
                RegionCode.EU_433,
                RegionCode.EU_868,
                RegionCode.EU_866,
                RegionCode.EU_N_868,
                RegionCode.CN,
                RegionCode.JP,
                RegionCode.ANZ,
                RegionCode.ANZ_433,
                RegionCode.RU,
                RegionCode.KR,
                RegionCode.TW,
                RegionCode.IN,
                RegionCode.NZ_865,
                RegionCode.TH,
                RegionCode.UA_433,
                RegionCode.MY_433,
                RegionCode.MY_919,
                RegionCode.SG_923,
                RegionCode.PH_433,
                RegionCode.PH_868,
                RegionCode.PH_915,
                RegionCode.KZ_433,
                RegionCode.KZ_863,
                RegionCode.NP_865,
                RegionCode.BR_902,
                RegionCode.ITU1_2M,
                RegionCode.ITU2_2M,
                RegionCode.ITU3_2M,
                RegionCode.ITU2_125CM,
                RegionCode.ITU1_70CM,
                RegionCode.ITU2_70CM,
                RegionCode.ITU3_70CM,
                RegionCode.LORA_24,
                RegionCode.UNSET,
            )
        firmwareRegions.forEach { region ->
            assertNotNull(
                RegionInfo.fromRegionCode(region),
                "Missing RegionInfo entry for firmware region '${region.name}'.",
            )
        }
    }
}
