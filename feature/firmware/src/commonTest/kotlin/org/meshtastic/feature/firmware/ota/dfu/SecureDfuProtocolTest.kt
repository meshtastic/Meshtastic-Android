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
package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { ignoreUnknownKeys = true }

class SecureDfuProtocolTest {

    // ── CRC-32 ────────────────────────────────────────────────────────────────

    @Test
    fun `CRC-32 of empty data is zero`() {
        assertEquals(0, DfuCrc32.calculate(ByteArray(0)))
    }

    @Test
    fun `CRC-32 standard check vector - 123456789`() {
        // Standard CRC-32/ISO-HDLC check value for "123456789" is 0xCBF43926
        val data = "123456789".encodeToByteArray()
        assertEquals(0xCBF43926.toInt(), DfuCrc32.calculate(data))
    }

    @Test
    fun `CRC-32 with seed accumulates across segments`() {
        val data = "Hello, World!".encodeToByteArray()
        val full = DfuCrc32.calculate(data)

        val firstHalf = DfuCrc32.calculate(data, length = 7)
        val accumulated = DfuCrc32.calculate(data, offset = 7, seed = firstHalf)

        assertEquals(full, accumulated, "Seeded CRC must equal whole-buffer CRC")
    }

    @Test
    fun `CRC-32 offset and length slice correctly`() {
        val wrapper = byteArrayOf(0xFF.toByte(), 0x01, 0x02, 0x03, 0xFF.toByte())
        val sliced = DfuCrc32.calculate(wrapper, offset = 1, length = 3)
        val direct = DfuCrc32.calculate(byteArrayOf(0x01, 0x02, 0x03))
        assertEquals(direct, sliced)
    }

    @Test
    fun `CRC-32 single byte is deterministic`() {
        val a = DfuCrc32.calculate(byteArrayOf(0x42))
        val b = DfuCrc32.calculate(byteArrayOf(0x42))
        assertEquals(a, b)
    }

    @Test
    fun `CRC-32 different data produces different CRC`() {
        val a = DfuCrc32.calculate(byteArrayOf(0x01))
        val b = DfuCrc32.calculate(byteArrayOf(0x02))
        assertTrue(a != b)
    }

    // ── intToLeBytes / readIntLe ───────────────────────────────────────────────

    @Test
    fun `intToLeBytes produces correct little-endian byte order`() {
        val bytes = intToLeBytes(0x01020304)
        assertEquals(0x04.toByte(), bytes[0])
        assertEquals(0x03.toByte(), bytes[1])
        assertEquals(0x02.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
    }

    @Test
    fun `intToLeBytes and readIntLe round-trip for zero`() {
        roundTripInt(0)
    }

    @Test
    fun `intToLeBytes and readIntLe round-trip for positive value`() {
        roundTripInt(0x12345678)
    }

    @Test
    fun `intToLeBytes and readIntLe round-trip for Int MAX_VALUE`() {
        roundTripInt(Int.MAX_VALUE)
    }

    @Test
    fun `intToLeBytes and readIntLe round-trip for negative value`() {
        roundTripInt(-1)
    }

    @Test
    fun `readIntLe reads from non-zero offset`() {
        val buf = byteArrayOf(0x00, 0x04, 0x03, 0x02, 0x01)
        assertEquals(0x01020304, buf.readIntLe(1))
    }

    private fun roundTripInt(value: Int) {
        assertEquals(value, intToLeBytes(value).readIntLe(0))
    }

    // ── DfuResponse.parse ────────────────────────────────────────────────────

    @Test
    fun `parse returns Unknown when data is too short`() {
        assertIs<DfuResponse.Unknown>(DfuResponse.parse(byteArrayOf(0x60.toByte(), 0x01)))
    }

    @Test
    fun `parse returns Unknown when first byte is not RESPONSE_CODE`() {
        assertIs<DfuResponse.Unknown>(DfuResponse.parse(byteArrayOf(0x01, 0x01, 0x01)))
    }

    @Test
    fun `parse returns Failure when result is not SUCCESS`() {
        val data = byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.CREATE, DfuResultCode.INVALID_OBJECT)
        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuOpcode.CREATE, result.opcode)
        assertEquals(DfuResultCode.INVALID_OBJECT, result.resultCode)
    }

    @Test
    fun `parse returns Success for CREATE opcode`() {
        val result = parseSuccessFor(DfuOpcode.CREATE)
        assertIs<DfuResponse.Success>(result)
        assertEquals(DfuOpcode.CREATE, result.opcode)
    }

    @Test
    fun `parse returns Success for EXECUTE opcode`() {
        val result = parseSuccessFor(DfuOpcode.EXECUTE)
        assertIs<DfuResponse.Success>(result)
        assertEquals(DfuOpcode.EXECUTE, result.opcode)
    }

    @Test
    fun `parse returns Success for SET_PRN opcode`() {
        val result = parseSuccessFor(DfuOpcode.SET_PRN)
        assertIs<DfuResponse.Success>(result)
    }

    @Test
    fun `parse returns Success for ABORT opcode`() {
        val result = parseSuccessFor(DfuOpcode.ABORT)
        assertIs<DfuResponse.Success>(result)
    }

    @Test
    fun `parse returns SelectResult for SELECT success`() {
        val maxSize = intToLeBytes(4096)
        val offset = intToLeBytes(512)
        val crc = intToLeBytes(0xDEADBEEF.toInt())
        val data =
            byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.SELECT, DfuResultCode.SUCCESS) + maxSize + offset + crc

        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.SelectResult>(result)
        assertEquals(4096, result.maxSize)
        assertEquals(512, result.offset)
        assertEquals(0xDEADBEEF.toInt(), result.crc32)
    }

    @Test
    fun `parse returns Failure for SELECT when payload too short`() {
        val short = byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.SELECT, DfuResultCode.SUCCESS, 0x01, 0x02)
        val result = DfuResponse.parse(short)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuResultCode.INVALID_PARAMETER, result.resultCode)
    }

    @Test
    fun `parse returns ChecksumResult for CALCULATE_CHECKSUM success`() {
        val offset = intToLeBytes(1024)
        val crc = intToLeBytes(0x12345678)
        val data =
            byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.CALCULATE_CHECKSUM, DfuResultCode.SUCCESS) + offset + crc

        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.ChecksumResult>(result)
        assertEquals(1024, result.offset)
        assertEquals(0x12345678, result.crc32)
    }

    @Test
    fun `parse returns Failure for CALCULATE_CHECKSUM when payload too short`() {
        val short = byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.CALCULATE_CHECKSUM, DfuResultCode.SUCCESS, 0x01)
        val result = DfuResponse.parse(short)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuResultCode.INVALID_PARAMETER, result.resultCode)
    }

    @Test
    fun `Unknown DfuResponse preserves raw bytes`() {
        val raw = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val result = DfuResponse.parse(raw)
        assertIs<DfuResponse.Unknown>(result)
        assertTrue(raw.contentEquals(result.raw))
    }

    private fun parseSuccessFor(opcode: Byte): DfuResponse =
        DfuResponse.parse(byteArrayOf(DfuOpcode.RESPONSE_CODE, opcode, DfuResultCode.SUCCESS))

    // ── DfuManifest deserialization ───────────────────────────────────────────

    @Test
    fun `DfuManifest deserializes application entry`() {
        val manifest =
            json.decodeFromString<DfuManifest>(
                """{"manifest":{"application":{"bin_file":"app.bin","dat_file":"app.dat"}}}""",
            )
        assertEquals("app.bin", manifest.manifest.application?.binFile)
        assertEquals("app.dat", manifest.manifest.application?.datFile)
    }

    @Test
    fun `DfuManifest deserializes softdevice_bootloader entry`() {
        val manifest =
            json.decodeFromString<DfuManifest>(
                """{"manifest":{"softdevice_bootloader":{"bin_file":"sd.bin","dat_file":"sd.dat"}}}""",
            )
        assertEquals("sd.bin", manifest.manifest.softdeviceBootloader?.binFile)
    }

    @Test
    fun `DfuManifest ignores unknown keys`() {
        val manifest =
            json.decodeFromString<DfuManifest>(
                """{"manifest":{"application":{"bin_file":"a.bin","dat_file":"a.dat"},"unknown_field":"ignored"}}""",
            )
        assertEquals("a.bin", manifest.manifest.primaryEntry?.binFile)
    }

    // ── DfuManifestContent.primaryEntry priority ──────────────────────────────

    @Test
    fun `primaryEntry prefers application over all others`() {
        val content =
            DfuManifestContent(
                application = DfuManifestEntry("app.bin", "app.dat"),
                softdeviceBootloader = DfuManifestEntry("sd_bl.bin", "sd_bl.dat"),
                bootloader = DfuManifestEntry("boot.bin", "boot.dat"),
                softdevice = DfuManifestEntry("sd.bin", "sd.dat"),
            )
        assertEquals("app.bin", content.primaryEntry?.binFile)
    }

    @Test
    fun `primaryEntry falls back to softdevice_bootloader`() {
        val content =
            DfuManifestContent(
                softdeviceBootloader = DfuManifestEntry("sd_bl.bin", "sd_bl.dat"),
                bootloader = DfuManifestEntry("boot.bin", "boot.dat"),
            )
        assertEquals("sd_bl.bin", content.primaryEntry?.binFile)
    }

    @Test
    fun `primaryEntry falls back to bootloader`() {
        val content =
            DfuManifestContent(
                bootloader = DfuManifestEntry("boot.bin", "boot.dat"),
                softdevice = DfuManifestEntry("sd.bin", "sd.dat"),
            )
        assertEquals("boot.bin", content.primaryEntry?.binFile)
    }

    @Test
    fun `primaryEntry falls back to softdevice`() {
        val content = DfuManifestContent(softdevice = DfuManifestEntry("sd.bin", "sd.dat"))
        assertEquals("sd.bin", content.primaryEntry?.binFile)
    }

    @Test
    fun `primaryEntry is null when all entries are null`() {
        assertNull(DfuManifestContent().primaryEntry)
    }

    // ── DfuException messages ─────────────────────────────────────────────────

    @Test
    fun `DfuException ProtocolError includes opcode and result code in message`() {
        val e = DfuException.ProtocolError(opcode = 0x01, resultCode = 0x05)
        assertTrue(e.message!!.contains("0x01"), "Message should contain opcode")
        assertTrue(e.message!!.contains("0x05"), "Message should contain result code")
    }

    @Test
    fun `DfuException ChecksumMismatch formats hex values in message`() {
        val e = DfuException.ChecksumMismatch(expected = 0xDEADBEEF.toInt(), actual = 0x12345678)
        assertTrue(e.message!!.contains("deadbeef"), "Message should contain expected CRC")
        assertTrue(e.message!!.contains("12345678"), "Message should contain actual CRC")
    }

    @Test
    fun `DfuZipPackage equality is content-based`() {
        val a = DfuZipPackage(byteArrayOf(0x01), byteArrayOf(0x02))
        val b = DfuZipPackage(byteArrayOf(0x01), byteArrayOf(0x02))
        assertEquals(a, b)
    }

    @Test
    fun `DfuZipPackage inequality when content differs`() {
        val a = DfuZipPackage(byteArrayOf(0x01), byteArrayOf(0x02))
        val b = DfuZipPackage(byteArrayOf(0x01), byteArrayOf(0x03))
        assertTrue(a != b)
    }

    // ── Extended error codes ─────────────────────────────────────────────────

    @Test
    fun `parse returns Failure with extended error when result is EXT_ERROR`() {
        // [RESPONSE_CODE, CREATE, EXT_ERROR, SD_VERSION_FAILURE]
        val data =
            byteArrayOf(
                DfuOpcode.RESPONSE_CODE,
                DfuOpcode.CREATE,
                DfuResultCode.EXT_ERROR,
                DfuExtendedError.SD_VERSION_FAILURE,
            )
        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuOpcode.CREATE, result.opcode)
        assertEquals(DfuResultCode.EXT_ERROR, result.resultCode)
        assertEquals(DfuExtendedError.SD_VERSION_FAILURE, result.extendedError)
    }

    @Test
    fun `parse returns Failure without extended error when EXT_ERROR but no extra byte`() {
        // Only 3 bytes — no room for extended error byte
        val data = byteArrayOf(DfuOpcode.RESPONSE_CODE, DfuOpcode.CREATE, DfuResultCode.EXT_ERROR)
        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuResultCode.EXT_ERROR, result.resultCode)
        assertNull(result.extendedError)
    }

    @Test
    fun `parse returns Failure without extended error for non-EXT_ERROR codes`() {
        val data =
            byteArrayOf(
                DfuOpcode.RESPONSE_CODE,
                DfuOpcode.CREATE,
                DfuResultCode.INVALID_OBJECT,
                0x07, // extra byte that should be ignored
            )
        val result = DfuResponse.parse(data)
        assertIs<DfuResponse.Failure>(result)
        assertEquals(DfuResultCode.INVALID_OBJECT, result.resultCode)
        assertNull(result.extendedError)
    }

    @Test
    fun `DfuExtendedError describe returns known descriptions`() {
        assertEquals("SD version failure", DfuExtendedError.describe(DfuExtendedError.SD_VERSION_FAILURE))
        assertEquals("Signature missing", DfuExtendedError.describe(DfuExtendedError.SIGNATURE_MISSING))
        assertEquals("Verification failed", DfuExtendedError.describe(DfuExtendedError.VERIFICATION_FAILED))
        assertEquals("Insufficient space", DfuExtendedError.describe(DfuExtendedError.INSUFFICIENT_SPACE))
        assertEquals("Init command invalid", DfuExtendedError.describe(DfuExtendedError.INIT_COMMAND_INVALID))
        assertEquals("FW version failure", DfuExtendedError.describe(DfuExtendedError.FW_VERSION_FAILURE))
        assertEquals("HW version failure", DfuExtendedError.describe(DfuExtendedError.HW_VERSION_FAILURE))
        assertEquals("Wrong hash type", DfuExtendedError.describe(DfuExtendedError.WRONG_HASH_TYPE))
        assertEquals("Hash failed", DfuExtendedError.describe(DfuExtendedError.HASH_FAILED))
        assertEquals("Wrong signature type", DfuExtendedError.describe(DfuExtendedError.WRONG_SIGNATURE_TYPE))
    }

    @Test
    fun `DfuExtendedError describe returns hex for unknown code`() {
        val desc = DfuExtendedError.describe(0x7F)
        assertTrue(desc.contains("0x7f"), "Should contain hex code: $desc")
    }

    @Test
    fun `DfuException ProtocolError includes extended error description in message`() {
        val e =
            DfuException.ProtocolError(
                opcode = DfuOpcode.EXECUTE,
                resultCode = DfuResultCode.EXT_ERROR,
                extendedError = DfuExtendedError.SD_VERSION_FAILURE,
            )
        assertTrue(e.message!!.contains("SD version failure"), "Message should contain extended error: ${e.message}")
        assertTrue(e.message!!.contains("0x0b"), "Message should contain result code 0x0b: ${e.message}")
    }

    @Test
    fun `DfuException ProtocolError without extended error omits ext field`() {
        val e = DfuException.ProtocolError(opcode = 0x01, resultCode = 0x05, extendedError = null)
        assertTrue(!e.message!!.contains("ext="), "Message should not contain ext= when null: ${e.message}")
    }

    // ── DfuResponse Failure equality ─────────────────────────────────────────

    @Test
    fun `Failure with same extended error is equal`() {
        val a = DfuResponse.Failure(0x01, 0x0B, 0x07)
        val b = DfuResponse.Failure(0x01, 0x0B, 0x07)
        assertEquals(a, b)
    }

    @Test
    fun `Failure with null vs non-null extended error is not equal`() {
        val a = DfuResponse.Failure(0x01, 0x0B, null)
        val b = DfuResponse.Failure(0x01, 0x0B, 0x07)
        assertTrue(a != b)
    }
}
