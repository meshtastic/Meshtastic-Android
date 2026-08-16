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

import org.meshtastic.tak.CotXmlParser
import org.meshtastic.tak.TakCompressor
import org.meshtastic.tak.TakPacketSdk
import co.touchlab.kermit.Logger as KermitLogger
import org.meshtastic.tak.Logger as SdkLogger

/**
 * Thin wrapper over the TAKPacket-SDK compression pipeline
 * ([org.meshtastic.tak.CotXmlParser] + [org.meshtastic.tak.TakCompressor]).
 *
 * The SDK is multiplatform since 0.7.0 — zstd dictionary compression rides on its transitive pure-Kotlin kzstd codec —
 * so this works on every target.
 */
internal object TakSdkCompressor {

    init {
        // The SDK never logs by default (see org.meshtastic.tak.Logger's kdoc) — wire its
        // internal diagnostics (dictionary selection, skip-compress path, etc.) into the
        // same Kermit sink this module already uses instead of leaving them discarded.
        TakPacketSdk.logger = SdkLogger { message -> KermitLogger.d(tag = "TakPacketSdk") { message } }
    }

    /**
     * Parse CoT XML via the SDK and compress with remarks-fallback.
     *
     * @return the wire payload plus whether `<remarks>` had to be stripped to fit [maxBytes]; `wirePayload` is `null`
     *   if the packet exceeds [maxBytes] even without remarks.
     * @throws Exception on parse or compression failure.
     */
    fun compressCoT(xml: String, maxBytes: Int): TakCompressor.RemarksFallbackResult {
        val sdkData = CotXmlParser().parse(xml)
        return TakCompressor().compressWithRemarksFallbackDetailed(sdkData, maxBytes)
    }
}
