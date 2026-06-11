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
 * iOS stub for CotSanitizer.
 *
 * Passthrough — the JVM-only TAKPacket-SDK is unavailable here, and stale/whitespace normalization is tolerated by the
 * iOS receive path (uncompressed TAK_TRACKER mode).
 *
 * TODO: Replace with Swift SDK integration via interop so the sanitize rules can't drift from the golden-tested SDK.
 */
internal actual object CotSanitizer {

    actual fun normalizeCotXml(xml: String): String = xml

    actual fun stripNonEssentialForMesh(xml: String): String = xml
}
