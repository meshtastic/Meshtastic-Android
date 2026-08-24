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

import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.HardwareModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoRaBandwidthPolicyTest {

    @Test
    fun subGhzRegion_remainsUnconstrained() {
        val selection = loRaBandwidthSelection(123, RegionCode.US, hwModel = null, pioEnv = null)

        assertNull(selection.options)
        assertNull(selection.invalidPersistedValue)
        assertTrue(selection.isValid)
    }

    @Test
    fun unsetRegion_preservesProtobufDefault() {
        val selection = loRaBandwidthSelection(0, RegionCode.UNSET, hwModel = null, pioEnv = null)

        assertNull(selection.options)
        assertNull(selection.invalidPersistedValue)
        assertTrue(selection.isValid)
    }

    @Test
    fun mixedTloraT3S3_usesConservativeHighBandOptions() {
        val selection =
            loRaBandwidthSelection(
                400,
                RegionCode.LORA_24,
                hwModel = HardwareModel.TLORA_T3_S3,
                pioEnv = "tlora-t3s3-v1",
            )

        assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
        assertTrue(selection.isValid)
    }

    @Test
    fun mixedHardwareModel_cannotBeUnlockedByConflictingTarget() {
        val selection =
            loRaBandwidthSelection(
                1600,
                RegionCode.LORA_24,
                hwModel = HardwareModel.TLORA_T3_S3,
                pioEnv = "tlora-v2-1-1_8",
            )

        assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
        assertEquals(1600, selection.invalidPersistedValue)
        assertFalse(selection.isValid)
    }

    @Test
    fun lr1121Target_excludes1600AndRetainsInvalidPersistedValue() {
        val selection =
            loRaBandwidthSelection(1600, RegionCode.LORA_24, hwModel = HardwareModel.MUZI_BASE, pioEnv = "muzi-base")

        assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
        assertEquals(1600, selection.invalidPersistedValue)
        assertFalse(selection.isValid)
    }

    @Test
    fun lr1121HardwareModel_cannotBeUnlockedByConflictingTarget() {
        val selection =
            loRaBandwidthSelection(
                1600,
                RegionCode.LORA_24,
                hwModel = HardwareModel.MUZI_BASE,
                pioEnv = "tlora-v2-1-1_8",
            )

        assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
        assertEquals(1600, selection.invalidPersistedValue)
        assertFalse(selection.isValid)
    }

    @Test
    fun unknownLora24Targets_useConservativeHighBandOptions() {
        listOf(null, "future-radio").forEach { target ->
            val selection = loRaBandwidthSelection(800, RegionCode.LORA_24, hwModel = null, pioEnv = target)

            assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
            assertTrue(selection.isValid)
        }
    }

    @Test
    fun invalidLora24SubGhzValue_mustBeReplaced() {
        val selection =
            loRaBandwidthSelection(
                125,
                RegionCode.LORA_24,
                hwModel = HardwareModel.TLORA_V2_1_1P8,
                pioEnv = "tlora-v2-1-1_8",
            )

        assertEquals(125, selection.invalidPersistedValue)
        assertFalse(selection.isValid)
        assertFalse(selection.allowsSave(usePreset = false))
        assertTrue(selection.allowsSave(usePreset = true))
    }

    @Test
    fun lora24ProtobufDefault_usesFirmwareDefaultBandwidth() {
        val selection = loRaBandwidthSelection(0, RegionCode.LORA_24, hwModel = null, pioEnv = null)

        assertEquals(listOf(0, 200, 400, 800), selection.options?.map { it.wireValue })
        assertNull(selection.invalidPersistedValue)
        assertTrue(selection.allowsSave(usePreset = false))
        assertTrue(selection.allowsSave(usePreset = true))
    }

    @Test
    fun provenSx128xOnlyTargets_include1600() {
        val targets =
            listOf(
                "betafpv_2400_tx_micro",
                "makerpython_nrf52840_sx1280_eink",
                "makerpython_nrf52840_sx1280_oled",
                "my-esp32s3-diy-eink",
                "my-esp32s3-diy-oled",
                "tlora-v2-1-1_8",
            )

        targets.forEach { target ->
            val selection = loRaBandwidthSelection(1600, RegionCode.LORA_24, hwModel = null, pioEnv = target)

            assertEquals(listOf(0, 200, 400, 800, 1600), selection.options?.map { it.wireValue })
            assertTrue(selection.isValid)
        }
    }

    @Test
    fun canonicalCodes_haveFirmwareDisplayBandwidths() {
        val selection = loRaBandwidthSelection(200, RegionCode.LORA_24, hwModel = null, pioEnv = null)

        assertEquals(listOf("812.5", "203.125", "406.25", "812.5"), selection.options?.map { it.displayKilohertz })
    }
}
