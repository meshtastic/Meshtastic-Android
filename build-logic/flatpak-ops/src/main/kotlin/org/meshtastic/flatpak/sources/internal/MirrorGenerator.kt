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
package org.meshtastic.flatpak.sources.internal

import java.net.URI

/**
 * Generates fallback mirror URLs for Maven Central artifacts.
 *
 * Only Maven Central has well-known public mirrors; for everything else (Google, JitPack, Gradle plugin portal,
 * snapshot repos), we trust the primary URL since these hosts don't have stable mirrors anyway. The URL itself is
 * authoritative — we just rewrite the host while preserving the path.
 */
internal object MirrorGenerator {

    private val MAVEN_CENTRAL_HOSTS = listOf("repo.maven.apache.org", "repo1.maven.org")

    /** Returns mirror URLs for the given primary URL, or an empty list if no mirrors apply. */
    fun mirrorsFor(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host
        val path = uri?.rawPath
        return if (host != null && path != null && host in MAVEN_CENTRAL_HOSTS) {
            MAVEN_CENTRAL_HOSTS.filter { it != host }.map { "https://$it$path" } +
                "https://maven-central.storage-download.googleapis.com$path"
        } else {
            emptyList()
        }
    }
}
