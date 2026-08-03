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
 * Develocity in one place: scan publishing, obfuscation, build cache. Applied from BOTH
 * settings files — included builds don't inherit the root's config, and without its own,
 * build-logic silently drops to local-cache-only. Versions live in the catalog.
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
            // Fingerprints power cache-miss comparison (CI debugging); skip the payload locally.
            capture { fileFingerprints.set(isCI) }
            // Public instance: no machine identity. Constants on purpose — scans already
            // record OS/CPU and CCUD adds CI metadata. Keep the `if` OUTSIDE the lambdas:
            // capture-free lambdas are what the config cache can serialize.
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
            // Off on CI: runners are ephemeral and every hit comes from the remote anyway.
            local { isEnabled = !isCI }
            remote(develocity.buildCache) {
                isEnabled = true
                val accessKey = System.getenv("DEVELOCITY_ACCESS_KEY")?.trim()
                // Write only from trusted events. A same-repository pull request DOES
                // receive repository secrets, so gating on the access key alone let PR
                // builds write into the shared cache; excluding pull_request here keeps
                // unmerged code out of it. Fork PRs have no key and are excluded twice
                // over, and local builds are excluded by isCI.
                val event = System.getenv("GITHUB_EVENT_NAME")
                val trustedForPush = event == "push" || event == "merge_group"
                isPush = isCI && trustedForPush && !accessKey.isNullOrEmpty()
            }
        }
    }
}
