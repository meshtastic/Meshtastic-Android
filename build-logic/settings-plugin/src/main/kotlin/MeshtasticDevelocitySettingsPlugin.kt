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

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Single source of truth for Develocity: scan publishing, obfuscation, and the build cache.
 *
 * Applied from BOTH settings files (root and `build-logic`) — an included build does not
 * inherit the root's Develocity configuration, and without its own, build-logic silently
 * falls back to local-cache-only. Before this plugin the two settings files hand-mirrored
 * the config and a CI drift guard policed them; now the version lives once in the catalog
 * and the config once here.
 */
class MeshtasticDevelocitySettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings): Unit = with(settings) {
        pluginManager.apply("com.gradle.develocity")
        pluginManager.apply("com.gradle.common-custom-user-data-gradle-plugin")

        val isCI = System.getenv("CI") != null
        val develocity = extensions.getByType(DevelocityConfiguration::class.java)

        develocity.server.set("https://community.develocity.cloud")
        develocity.projectId.set("meshtastic")
        develocity.buildScan {
            uploadInBackground.set(!isCI)
            publishing.onlyIf { it.isAuthenticated }
            // File fingerprints power Develocity's cache-miss comparison — a CI-debugging
            // tool; the extra scan payload is not worth paying on every local build.
            capture { fileFingerprints.set(isCI) }
            // community.develocity.cloud is a public OSS instance, so no machine identity is
            // published: without this, every local build would publish the contributor's OS
            // username, hostname, and busiest process names. Constants, not descriptive
            // values — the scan already records OS and CPU count, and CCUD adds CI metadata.
            // The `if` stays OUTSIDE the lambdas so each stays a capture-free constant the
            // configuration cache can serialize.
            obfuscation {
                ipAddresses { addresses -> addresses.map { _ -> "0.0.0.0" } }
                externalProcessName { "external-process" }
                if (isCI) {
                    username { "ci" }
                    hostname { "ci-runner" }
                } else {
                    username { "local-dev" }
                    hostname { "local-machine" }
                }
            }
        }

        buildCache {
            // Gradle's guidance: disable the local cache where a remote is available. On CI
            // ours was write-then-discard (ephemeral runners; build-cache-1 excluded from the
            // Actions cache), so every hit already comes from the remote.
            local { isEnabled = !isCI }
            remote(develocity.buildCache) {
                isEnabled = true
                val accessKey = System.getenv("DEVELOCITY_ACCESS_KEY")?.trim()
                isPush = isCI && !accessKey.isNullOrEmpty()
            }
        }
    }
}
