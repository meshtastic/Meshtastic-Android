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

pluginManagement {
    includeBuild("settings-plugin")
    repositories {
        // Google-only groups resolve from Google's repository alone, as in the root settings.
        exclusiveContent {
            forRepositories(
                google { content { includeGroupByRegex("com\\.google.*") } },
                maven {
                    name = "OfflineGoogleArtifacts"
                    url = uri("../offline-repository")
                },
            )
            filter {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Ahead of Central, as in the root settings: Central rate-limits CI's shared egress.
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            mavenContent { releasesOnly() }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("../offline-repository") }
    }
}

plugins {
    id("meshtastic.develocity")
}

dependencyResolutionManagement {
    repositories {
        // Google-only groups resolve from Google's repository alone, as in the root settings.
        exclusiveContent {
            forRepositories(
                google { content { includeGroupByRegex("com\\.google.*") } },
                maven {
                    name = "OfflineGoogleArtifacts"
                    url = uri("../offline-repository")
                },
            )
            filter {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Ahead of Central, as in the root settings: Central rate-limits CI's shared egress.
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            mavenContent { releasesOnly() }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("../offline-repository") }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
