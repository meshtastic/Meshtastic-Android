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

import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.meshtastic.detekt)
    alias(libs.plugins.meshtastic.spotless)
    alias(libs.plugins.meshtastic.koin)
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Exclude generated Compose resource files from detekt analysis
tasks.withType<Detekt>().configureEach { exclude("**/generated/**") }

compose.desktop {
    application {
        mainClass = "org.meshtastic.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Exe,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
            )
            packageName = "Meshtastic"

            modules("java.net.http")

            // App Icon
            macOS { iconFile.set(project.file("src/main/resources/icon.icns")) }
            windows { iconFile.set(project.file("src/main/resources/icon.ico")) }
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }

            // Read version from project properties (passed by CI) or default to 0.1.0
            // Native installers require strict numeric semantic versions (X.Y.Z) without suffixes
            val rawVersion = project.findProperty("android.injected.version.name")?.toString()
                ?: System.getenv("VERSION_NAME")
                ?: "1.0.0"
            val sanitizedVersion = Regex("^\\d+\\.\\d+\\.\\d+").find(rawVersion)?.value ?: "1.0.0"
            packageVersion = sanitizedVersion

            description = "Meshtastic Desktop Application"
            vendor = "Meshtastic LLC"
        }
    }
}

dependencies {
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    // Core KMP modules (JVM variants)
    implementation(projects.core.common)
    implementation(projects.core.di)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.repository)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.prefs)
    implementation(projects.core.network)
    implementation(projects.core.resources)
    implementation(projects.core.service)
    implementation(projects.core.ui)
    implementation(projects.core.proto)
    implementation(projects.core.ble)

    // Feature modules (JVM variants for real composable wiring)
    implementation(projects.feature.settings)
    implementation(projects.feature.node)
    implementation(projects.feature.messaging)
    implementation(projects.feature.connections)
    implementation(projects.feature.map)

    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.materialIconsExtended)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.resources)

    // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation)

    // Navigation 3 (JetBrains fork — multiplatform)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
    implementation(libs.jetbrains.lifecycle.runtime.compose)

    // Koin DI
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kermit)
    implementation(libs.okio)

    // Ktor HttpClient (Java engine for JVM/Desktop)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.koin.annotations)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(kotlin("test"))
}

aboutLibraries {
    // Fetch full license text + funding info from GitHub API when on CI with a token
    val isCi = providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false)
    val ghToken = providers.environmentVariable("GITHUB_TOKEN")
    collect {
        fetchRemoteLicense = isCi && ghToken.isPresent
        fetchRemoteFunding = isCi && ghToken.isPresent
        if (ghToken.isPresent) {
            gitHubApiToken = ghToken.get()
        }
    }
    export {
        excludeFields = listOf("generated")
        outputFile = file("src/main/resources/aboutlibraries.json")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}
