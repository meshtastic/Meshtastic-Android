/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.wire)
    `maven-publish`
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

kotlin {
    // Keep jvm() for desktop/server consumers
    jvm()

    // Override minSdk for ATAK compatibility (standard is 26)
    androidLibrary { minSdk = 21 }

    sourceSets { commonMain.dependencies { api(libs.wire.runtime) } }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
        srcDir("src/main/wire-includes")
    }
    kotlin {
        // Flattens 'oneof' fields into nullable properties on the parent class.
        // This removes the intermediate sealed classes, simplifying usage and reducing method count/binary size.
        // Codebase is already written to use the nullable properties (e.g. packet.decoded vs
        // packet.payload_variant.decoded).
        boxOneOfsMinSize = 5000
    }
    root("meshtastic.*")
}

// Modern KMP publication uses the project name as the artifactId by default.
// We rename the publications to include the 'core-' prefix for consistency.
publishing {
    publications.withType<MavenPublication>().configureEach {
        val baseId = artifactId
        if (baseId == "proto") {
            artifactId = "core-proto"
        } else if (baseId.startsWith("proto-")) {
            artifactId = baseId.replace("proto-", "core-proto-")
        }
    }
}
