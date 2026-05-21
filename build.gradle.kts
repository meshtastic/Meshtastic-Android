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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.datadog) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.secrets) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.test.retry) apply false
    alias(libs.plugins.meshtastic.root)
    id("meshtastic.docs")
}

dependencies {
    dokkaPlugin(libs.dokka.android.documentation.plugin)
}

val combineFlatpakSources by tasks.registering {
    group = "flatpak"
    description = "Combines, deduplicates, and adds high-availability CDN mirrors to all Flatpak source manifests."

    dependsOn(gradle.includedBuild("build-logic").task(":convention:flatpakGradleGenerator"))
    dependsOn(":core:database:flatpakGradleGenerator")
    dependsOn(":desktopApp:flatpakGradleGenerator")

    val arch = providers.gradleProperty("flatpak.arch")
        .getOrElse(System.getProperty("os.arch").let { if (it == "amd64") "x86_64" else it })

    val conventionSources = file("flatpak-sources-convention.json")
    val databaseSources = file("flatpak-sources-core-database.json")
    val desktopSources = file("flatpak-sources-desktop-$arch.json")
    val outputSources = file("flatpak-sources.json")

    inputs.files(conventionSources, databaseSources, desktopSources)
    outputs.file(outputSources)

    doLast {
        val slurper = JsonSlurper()
        val allEntries = mutableListOf<Map<String, Any>>()

        listOf(conventionSources, databaseSources, desktopSources).forEach { file ->
            if (file.exists()) {
                @Suppress("UNCHECKED_CAST")
                val parsed = slurper.parse(file) as List<Map<String, Any>>
                allEntries.addAll(parsed)
            }
        }

        // Deduplicate entries by dest-filename + target path + architecture spec
        val deduplicated = allEntries.groupBy { entry ->
            val dest = entry["dest"] as? String ?: ""
            val filename = entry["dest-filename"] as? String ?: ""
            val arches = entry["only-arches"]?.toString() ?: ""
            "$dest/$filename/$arches"
        }.map { (_, group) ->
            val entry = group.first().toMutableMap()
            val url = entry["url"] as? String ?: ""

            // Dynamically inject mirror URLs for redundancy in Flatpak builds
            if (url.startsWith("https://repo.maven.apache.org/maven2/")) {
                val mirrorUrl = url.replace(
                    "https://repo.maven.apache.org/maven2/",
                    "https://maven-central.storage-download.googleapis.com/maven2/"
                )
                entry["mirror-urls"] = listOf(mirrorUrl)
            } else if (url.startsWith("https://plugins.gradle.org/m2/")) {
                val mirrorUrl = url.replace(
                    "https://plugins.gradle.org/m2/",
                    "https://maven.aliyun.com/repository/gradle-plugin/"
                )
                entry["mirror-urls"] = listOf(mirrorUrl)
            }
            entry
        }

        outputSources.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(deduplicated)))
        logger.lifecycle("Successfully combined and deduplicated ${deduplicated.size} Flatpak offline resources.")
    }
}



