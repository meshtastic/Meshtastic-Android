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
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("../offline-repository") }
    }
}

plugins {
    // Version duplicated from the root settings.gradle.kts: a settings `plugins {}`
    // block is evaluated before the version catalog exists, so it cannot use `libs`.
    id("com.gradle.develocity") version "4.5.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("../offline-repository") }
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// build-logic is a plugin-included build with its own settings, so it does NOT inherit the
// root build's Develocity configuration. Without this block the shared build-cache script
// below finds no `develocity` extension and silently falls back to local-cache-only —
// `./gradlew help --info` then reports "Using local directory build cache for build
// ':build-logic'" with no matching remote line, while the root build gets both. Mirror the
// root's server/project so the convention plugins share one remote cache with everything else.
develocity {
    server = "https://community.develocity.cloud"
    projectId = "meshtastic"
    buildScan { publishing.onlyIf { it.isAuthenticated } }
}

// Build Cache configuration (Develocity remote cache + local)
apply(from = "../gradle/build-cache.settings.gradle")

rootProject.name = "build-logic"
include(":convention")
