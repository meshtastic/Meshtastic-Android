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
package org.meshtastic.app.firmware

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.SoftDeviceVariant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the SoftDevice map in `device_bootloader_ota_quirks.json` against the hardware catalog.
 *
 * This test is the reason the map can safely be fail-closed. An unmapped or mistyped row does not break anything at
 * runtime — it silently makes factory erase unavailable for that device — so nothing else would ever notice. CI has to
 * be what notices.
 *
 * It deliberately does **not** assert which variant each board uses, only that every board has one and that the values
 * are recognised. The variant itself is verified at runtime against what the device reports on its UF2 drive, and a
 * disagreement refuses rather than guessing (see `resolveNrfEraseImage`).
 */
class SoftDeviceQuirkCoverageTest {

    /**
     * Models with no firmware variant on `meshtastic/firmware` master, so no ldscript exists to derive a SoftDevice
     * from. Present in the asset with no `softDevice` value, which makes the refusal deliberate and greppable rather
     * than an oversight. Remove an entry here once its variant lands upstream.
     */
    private val knownUnmapped = setOf(130) // THINKNODE_M8

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `every nrf52840 model in the hardware catalog has a softdevice mapping`() {
        val nrfModels = nrfHardware().map { it.hwModel }.toSet()
        val mapped = quirks().softDeviceVariants.filter { it.softDevice != null }.map { it.hwModel }.toSet()

        val missing = nrfModels - mapped - knownUnmapped
        assertTrue(
            missing.isEmpty(),
            "nRF52840 models with no SoftDevice mapping: $missing. Factory erase is silently unavailable for these. " +
                "Derive each from meshtastic/firmware boards/<board>.json build.arduino.ldscript " +
                "(nrf52840_s140_v6.ld = 6.1.1, _v7.ld = 7.3.0), or add the hwModel to knownUnmapped with a reason.",
        )
    }

    @Test
    fun `known-unmapped models are present but deliberately carry no softdevice`() {
        val entries = quirks().softDeviceVariants.associateBy { it.hwModel }
        knownUnmapped.forEach { hwModel ->
            val entry = entries[hwModel]
            assertNotNull(
                entry,
                "hwModel $hwModel is listed as known-unmapped but has no row at all — add the row so " +
                    "the refusal is explicit, or drop it from knownUnmapped",
            )
            assertEquals(
                null,
                entry.softDevice,
                "hwModel $hwModel is listed as known-unmapped but now carries a SoftDevice — remove it from " +
                    "knownUnmapped instead",
            )
        }
    }

    @Test
    fun `every softdevice value is one the app ships an erase image for`() {
        quirks().softDeviceVariants.forEach { entry ->
            val wire = entry.softDevice ?: return@forEach
            assertNotNull(
                SoftDeviceVariant.fromWire(wire),
                "hwModel ${entry.hwModel} (${entry.hwModelSlug}) declares SoftDevice '$wire', which maps to no " +
                    "erase image. A typo here disables erase for that device silently.",
            )
        }
    }

    @Test
    fun `every mapped target exists in the hardware catalog`() {
        // Resolution requires the device-reported target to appear in its row, so a stale target name is a silent
        // refusal for that build.
        val catalogTargets = nrfHardware().map { it.platformioTarget }.toSet()
        quirks().softDeviceVariants.forEach { entry ->
            entry.platformioTargets.forEach { target ->
                assertTrue(
                    target in catalogTargets,
                    "hwModel ${entry.hwModel} lists platformioTarget '$target', which is not in the hardware " +
                        "catalog — erase would be refused for any device reporting it",
                )
            }
        }
    }

    @Test
    fun `every nrf52840 catalog target is covered by its model's row`() {
        val entries = quirks().softDeviceVariants.associateBy { it.hwModel }
        nrfHardware().forEach { hw ->
            val entry = entries[hw.hwModel] ?: return@forEach
            assertTrue(
                hw.platformioTargets().any { it in entry.platformioTargets },
                "hwModel ${hw.hwModel} (${hw.hwModelSlug}) builds target '${hw.platformioTarget}' but its row lists " +
                    "${entry.platformioTargets} — a device reporting that target would be refused",
            )
        }
    }

    @Test
    fun `mesh tracker x1 is mapped, since the web flasher omits it`() {
        // hwModel 128 is nrf52840_s140_v7.ld (7.3.0) but is absent from the web flasher's sd7.3 allowlist, so that tool
        // serves it the 6.1.1 image. Pinned here so the app does not inherit the same gap.
        val entry = quirks().softDeviceVariants.firstOrNull { it.hwModel == MESH_TRACKER_X1_HW_MODEL }
        assertNotNull(entry, "MESH_TRACKER_X1 (128) must be mapped")
        assertEquals(SoftDeviceVariant.S140_7_3_0, SoftDeviceVariant.fromWire(entry.softDevice))
    }

    private fun NetworkDeviceHardware.platformioTargets(): List<String> = listOf(platformioTarget)

    private fun nrfHardware(): List<NetworkDeviceHardware> =
        json.decodeFromString<List<NetworkDeviceHardware>>(asset("device_hardware.json").readText()).filter {
            it.architecture == NRF52840_ARCHITECTURE
        }

    private fun quirks(): BootloaderOtaQuirksResponse =
        json.decodeFromString(asset("device_bootloader_ota_quirks.json").readText())

    private fun asset(name: String): File =
        listOf("src/main/assets/$name", "androidApp/src/main/assets/$name").map(::File).firstOrNull(File::exists)
            ?: fail("Could not locate $name from working directory ${File(".").absolutePath}")

    private companion object {
        const val NRF52840_ARCHITECTURE = "nrf52840"
        const val MESH_TRACKER_X1_HW_MODEL = 128
    }
}
