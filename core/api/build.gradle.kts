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
plugins {
    alias(libs.plugins.meshtastic.android.library)
    id("meshtastic.publishing")
}

configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "org.meshtastic.core.api"
    buildFeatures { aidl = true }

    defaultConfig {
        // Lowering minSdk to 21 for better compatibility with ATAK and other plugins
        minSdk = 21
    }

    publishing { singleVariant("release") { withSourcesJar() } }
}

// Suppress dep-ann warnings from AIDL-generated code where Javadoc @deprecated
// doesn't produce @Deprecated annotations on Stub/Proxy override methods.
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-dep-ann") }

// Map the Android component to a Maven publication.
// afterEvaluate is required because AGP registers the "release" component lazily
// after the android.publishing.singleVariant("release") configuration runs.
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifactId = "meshtastic-android-api"
            }
        }
    }
}

dependencies { api(projects.core.model) }
