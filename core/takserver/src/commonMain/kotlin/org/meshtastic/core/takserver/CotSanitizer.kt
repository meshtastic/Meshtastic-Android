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

import org.meshtastic.tak.CotMeshSanitizer

/**
 * Thin wrapper over the TAKPacket-SDK's [org.meshtastic.tak.CotMeshSanitizer].
 *
 * The SDK is multiplatform since 0.7.0, so the golden-tested sanitize rules live in ONE place shared by every consumer
 * and every target — they can't drift between platforms.
 */
internal object CotSanitizer {

    /**
     * Normalize CoT XML for the TAK TCP stream — drop the `<?xml ...?>` prologue and collapse inter-tag whitespace so
     * ATAK's streaming parser sees bare `<event>...</event>` on a single line.
     */
    fun normalizeCotXml(xml: String): String = CotMeshSanitizer.normalizeCotXml(xml)

    /** Strip non-essential CoT detail before mesh compression to save wire bytes. */
    fun stripNonEssentialForMesh(xml: String): String = CotMeshSanitizer.stripNonEssentialForMesh(xml)
}
