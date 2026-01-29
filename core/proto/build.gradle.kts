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
import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.protobuf)
    `maven-publish`
}

apply(from = rootProject.file("gradle/publishing.gradle.kts"))

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["googleRelease"])
                artifactId = "core-proto"
            }
        }
    }
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.core.proto"
    
    defaultConfig {
        // Lowering minSdk to 21 for better compatibility with ATAK and other plugins
        minSdk = 21
    }

    publishing { singleVariant("googleRelease") { withSourcesJar() } }
}

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc { artifact = libs.protobuf.protoc.get().toString() }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {}
                create("kotlin") {}
            }
        }
    }
}

dependencies {
    // This needs to be API for consuming modules
    api(libs.protobuf.kotlin)
}
