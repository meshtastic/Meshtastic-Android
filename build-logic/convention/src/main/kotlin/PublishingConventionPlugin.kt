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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.meshtastic.buildlogic.configProperties

class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")

            group = "org.meshtastic"

            if (version == "unspecified") {
                version =
                    System.getenv("VERSION")
                        ?: System.getenv("VERSION_NAME")
                        ?: configProperties.getProperty("VERSION_NAME_BASE")
                        ?: "0.0.0-SNAPSHOT"
            }

            val githubActor = System.getenv("GITHUB_ACTOR")
            val githubToken = System.getenv("GITHUB_TOKEN")

            if (!githubActor.isNullOrEmpty() && !githubToken.isNullOrEmpty()) {
                extensions.configure<PublishingExtension> {
                    repositories {
                        maven {
                            name = "GitHubPackages"
                            url = uri("https://maven.pkg.github.com/meshtastic/Meshtastic-Android")
                            credentials {
                                username = githubActor
                                password = githubToken
                            }
                        }
                    }
                }
            }
        }
    }
}
