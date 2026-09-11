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
package org.meshtastic.core.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import co.touchlab.kermit.Logger

/**
 * Presents the armed share URL as an NFC Forum Type 4 Tag, so a reader - another phone, a Flipper, any NDEF reader -
 * can take a contact or channel by tapping this phone, with no physical tag.
 *
 * Read-only by construction: the capability container advertises no write access and no write command is answered.
 * Serves nothing unless [NfcTagEmulation] is armed, so an idle phone presents no tag at all.
 *
 * Implements only what a Type 4 read needs: SELECT application, SELECT file, READ BINARY.
 */
class MeshtasticHostApduService : HostApduService() {

    private var selectedFile = FILE_NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val ndef = NfcTagEmulation.armedNdefMessage()
        // Unarmed, every command is refused, so a reader sees no tag rather than an empty one.
        return if (commandApdu == null || ndef == null) SW_FILE_NOT_FOUND else dispatch(commandApdu, ndef)
    }

    override fun onDeactivated(reason: Int) {
        // The next reader starts unselected; without this a second tap could read whatever file
        // the previous one left selected.
        selectedFile = FILE_NONE
    }

    private fun dispatch(apdu: ByteArray, ndef: ByteArray): ByteArray = when {
        apdu.contentEquals(SELECT_APPLICATION) -> {
            selectedFile = FILE_NONE
            SW_OK
        }

        isSelectFile(apdu) -> selectFile(apdu)

        isReadBinary(apdu) -> readBinary(apdu, ndef)

        else -> SW_INS_NOT_SUPPORTED
    }

    private fun isSelectFile(apdu: ByteArray) = apdu.size == SELECT_FILE_LENGTH &&
        apdu[OFFSET_CLA] == CLA_ISO &&
        apdu[OFFSET_INS] == INS_SELECT &&
        apdu[OFFSET_P1] == SELECT_BY_FILE_ID &&
        apdu[OFFSET_P2] == SELECT_FIRST_OCCURRENCE

    private fun isReadBinary(apdu: ByteArray) =
        apdu.size >= READ_BINARY_LENGTH && apdu[OFFSET_CLA] == CLA_ISO && apdu[OFFSET_INS] == INS_READ_BINARY

    private fun selectFile(apdu: ByteArray): ByteArray {
        val fileId = twoByteValue(apdu, SELECT_FILE_ID_OFFSET)
        return if (fileId == FILE_CAPABILITY_CONTAINER || fileId == FILE_NDEF) {
            selectedFile = fileId
            SW_OK
        } else {
            Logger.w { "Reader selected unknown file 0x${fileId.toString(HEX_RADIX)}" }
            SW_FILE_NOT_FOUND
        }
    }

    private fun readBinary(apdu: ByteArray, ndef: ByteArray): ByteArray {
        val content =
            when (selectedFile) {
                FILE_CAPABILITY_CONTAINER -> CAPABILITY_CONTAINER
                FILE_NDEF -> ndefFile(ndef)
                else -> null
            }
        val offset = twoByteValue(apdu, OFFSET_P1)
        // Le == 0 means "as much as you can", which for a short APDU is 256 bytes.
        val requested = (apdu[OFFSET_LE].toInt() and BYTE_MASK).let { if (it == 0) MAX_SHORT_LE else it }

        return when {
            content == null -> SW_FILE_NOT_FOUND
            offset > content.size -> SW_WRONG_PARAMETERS
            else -> content.copyOfRange(offset, minOf(offset + requested, content.size)) + SW_OK
        }
    }

    /**
     * The NDEF file as a reader sees it: a two-byte big-endian length, then the message. A message too large for what
     * [CAPABILITY_CONTAINER] advertises is served as empty rather than truncated, since a truncated NDEF message is
     * worse than none.
     */
    private fun ndefFile(ndef: ByteArray): ByteArray {
        val body =
            if (ndef.size > MAX_NDEF_FILE_SIZE - NLEN_SIZE) {
                Logger.w { "Armed NDEF message too large to emulate: ${ndef.size} bytes" }
                ByteArray(0)
            } else {
                ndef
            }
        val nlen = byteArrayOf((body.size shr BITS_PER_BYTE and BYTE_MASK).toByte(), (body.size and BYTE_MASK).toByte())
        return nlen + body
    }

    private fun twoByteValue(apdu: ByteArray, offset: Int): Int =
        (apdu[offset].toInt() and BYTE_MASK shl BITS_PER_BYTE) or (apdu[offset + 1].toInt() and BYTE_MASK)

    private companion object {
        fun hex(spaced: String): ByteArray =
            spaced.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

        val SW_OK = hex("90 00")
        val SW_FILE_NOT_FOUND = hex("6A 82")
        val SW_WRONG_PARAMETERS = hex("6B 00")
        val SW_INS_NOT_SUPPORTED = hex("6D 00")

        /** SELECT (by name) of the NFC Forum NDEF Tag Application, AID D2760000850101. */
        val SELECT_APPLICATION = hex("00 A4 04 00 07 D2 76 00 00 85 01 01 00")

        /**
         * CCLEN 000F, mapping version 2.0, MLe 00FF, MLc 0034, then the NDEF File Control TLV: type 04, length 06, file
         * id E104, max size 0400, read access free, write access none. MLe is the largest response a reader may ask for
         * in one READ BINARY; 255 keeps a share URL to a single exchange.
         */
        val CAPABILITY_CONTAINER = hex("000F 20 00FF 0034 04 06 E104 0400 00 FF")

        const val CLA_ISO: Byte = 0x00
        const val INS_SELECT: Byte = 0xA4.toByte()
        const val INS_READ_BINARY: Byte = 0xB0.toByte()
        const val SELECT_BY_FILE_ID: Byte = 0x00
        const val SELECT_FIRST_OCCURRENCE: Byte = 0x0C

        const val OFFSET_CLA = 0
        const val OFFSET_INS = 1
        const val OFFSET_P1 = 2
        const val OFFSET_P2 = 3
        const val OFFSET_LE = 4
        const val SELECT_FILE_ID_OFFSET = 5

        const val SELECT_FILE_LENGTH = 7
        const val READ_BINARY_LENGTH = 5

        const val FILE_NONE = -1
        const val FILE_CAPABILITY_CONTAINER = 0xE103
        const val FILE_NDEF = 0xE104

        const val MAX_NDEF_FILE_SIZE = 1024
        const val NLEN_SIZE = 2
        const val MAX_SHORT_LE = 256

        const val BYTE_MASK = 0xFF
        const val BITS_PER_BYTE = 8
        const val HEX_RADIX = 16
    }
}
