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
package org.meshtastic.core.takserver

/**
 * Expect/actual wrapper for the TAKPacket-SDK compression pipeline.
 *
 * On JVM/Android the SDK's [org.meshtastic.tak.CotXmlParser] and [org.meshtastic.tak.TakCompressor] are available. On
 * iOS they are not, so the actual throws [UnsupportedOperationException].
 */
internal expect object TakSdkCompressor {

    /**
     * Parse CoT XML via the SDK and compress with remarks-fallback.
     *
     * @return compressed wire payload, or `null` if the packet exceeds [maxBytes] even without remarks.
     * @throws Exception on parse or compression failure.
     */
    fun compressCoT(xml: String, maxBytes: Int): ByteArray?
}
