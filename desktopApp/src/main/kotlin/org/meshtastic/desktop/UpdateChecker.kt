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
package org.meshtastic.desktop

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks GitHub Releases for a newer published desktop build.
 *
 * The `releases/latest` endpoint only ever returns a published, non-draft, non-prerelease release, so users are only
 * pointed at builds that completed the full promotion pipeline (see `.github/workflows/promote.yml`).
 */
class UpdateChecker(private val httpClient: HttpClient) {

    /** A published release newer than the running build. */
    data class UpdateInfo(val versionName: String, val releaseUrl: String)

    /** Returns the latest published release when it is newer than [currentVersionName], null otherwise. */
    suspend fun check(currentVersionName: String): UpdateInfo? = runCatching {
        val release = Json.parseToJsonElement(httpClient.get(LATEST_RELEASE_URL).bodyAsText()).jsonObject
        val tag = release["tag_name"]?.jsonPrimitive?.content
        if (tag != null && isNewer(latest = tag, current = currentVersionName)) {
            UpdateInfo(
                versionName = tag.removePrefix("v"),
                releaseUrl = release["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE_URL,
            )
        } else {
            null
        }
    }
        .getOrElse { e ->
            Logger.d(e) { "Update check skipped: offline, rate-limited, or malformed response" }
            null
        }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/meshtastic/Meshtastic-Android/releases/latest"
        private const val RELEASES_PAGE_URL = "https://github.com/meshtastic/Meshtastic-Android/releases"

        private val VERSION_REGEX = Regex("""(\d+)\.(\d+)\.(\d+)""")

        /**
         * True when [latest] carries a strictly newer numeric X.Y.Z than [current]. Suffixes (`v`, `-internal.N`) are
         * ignored; versions that don't parse never trigger an update.
         */
        internal fun isNewer(latest: String, current: String): Boolean {
            val latestVersion = parse(latest)
            val currentVersion = parse(current)
            return latestVersion != null &&
                currentVersion != null &&
                compareValuesBy(latestVersion, currentVersion, { it.first }, { it.second }, { it.third }) > 0
        }

        private fun parse(raw: String): Triple<Int, Int, Int>? =
            VERSION_REGEX.find(raw)?.destructured?.let { (major, minor, patch) ->
                Triple(major.toInt(), minor.toInt(), patch.toInt())
            }
    }
}
