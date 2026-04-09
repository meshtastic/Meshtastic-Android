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
    id("meshtastic.kover")
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

// Exclude generated Compose resource files from detekt analysis
tasks.withType<Detekt>().configureEach { exclude("**/generated/**") }

compose.desktop {
    application {
        mainClass = "org.meshtastic.desktop.MainKt"
        jvmArgs(
            "-Xmx2G",
            "-Dapple.awt.application.name=Meshtastic",
            "-Dcom.apple.mrj.application.apple.menu.about.name=Meshtastic",
            "-Dcom.apple.bundle.identifier=org.meshtastic.desktop",
        )

        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(false) // Open-source project — obfuscation adds no value
            optimize.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            packageName = "Meshtastic"

            // Ensure critical JVM modules are included in the custom JRE bundled with the app.
            // jdeps might miss some of these if they are loaded via reflection or JNI.
            modules(
                "java.net.http", // Ktor Java client
                "jdk.crypto.ec", // Required for SSL/TLS HTTPS requests
                "jdk.unsupported", // sun.misc.Unsafe used by Coroutines & Okio
                "java.sql", // Sometimes required by SQLite JNI
                "java.naming", // Required by Ktor for DNS resolution
            )

            // Default JVM arguments for the packaged application
            // Increase max heap size to prevent OOM issues on complex maps/data
            jvmArgs(
                "-Xmx2G",
                "-Dapple.awt.application.name=Meshtastic",
                "-Dcom.apple.mrj.application.apple.name=Meshtastic",
                "-Dcom.apple.bundle.identifier=org.meshtastic.desktop",
            )

            // App Icon & OS Specific Configurations
            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
                minimumSystemVersion = "12.0"
                bundleID = "org.meshtastic.desktop"
                infoPlist {
                    extraKeysRawXml =
                        """
                        <key>NSUserNotificationAlertStyle</key>
                        <string>alert</string>
                        """
                            .trimIndent()
                }
                // TODO: To prepare for real distribution on macOS, you'll need to sign and notarize.
                // You can inject these from CI environment variables.
                // sign = true
                // notarize = true
                // appleID = System.getenv("APPLE_ID")
                // appStorePassword = System.getenv("APPLE_APP_SPECIFIC_PASSWORD")
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                menuGroup = "Meshtastic"
                // TODO: Must generate and set a consistent UUID for Windows upgrades.
                // upgradeUuid = "YOUR-UPGRADE-UUID-HERE"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                menuGroup = "Network"
            }

            // Define target formats based on the current host OS to avoid configuration errors
            // (e.g., trying to configure Linux AppImage notarization on macOS).
            val currentOs = System.getProperty("os.name").lowercase()
            when {
                currentOs.contains("mac") -> targetFormats(TargetFormat.Dmg)
                currentOs.contains("win") -> targetFormats(TargetFormat.Msi, TargetFormat.Exe)
                else -> targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            }

            // Read version from project properties (passed by CI) or default to 1.0.0
            // Native installers require strict numeric semantic versions (X.Y.Z) without suffixes
            val rawVersion =
                project.findProperty("android.injected.version.name")?.toString()
                    ?: project.findProperty("appVersionName")?.toString()
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

    // Coil image loading (network + SVG decoding for device hardware images)
    implementation(libs.coil)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.svg)

    // Core KMP modules (JVM variants)
    implementation(projects.core.common)
    implementation(projects.core.di)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
    implementation(projects.core.repository)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.prefs)
    implementation(projects.core.network)
    implementation(projects.core.takserver)
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
    implementation(projects.feature.firmware)
    implementation(projects.feature.wifiProvision)
    implementation(projects.feature.intro)

    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.resources)

    // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation)

    // Navigation 3 (JetBrains fork — multiplatform)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
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

    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.koin.test)
    testImplementation(kotlin("test"))
}

aboutLibraries {
    // Fetch full license text + funding info from GitHub API when on CI with a token
    val isCi =
        providers
            .gradleProperty("ci")
            .map { it.toBoolean() }
            .getOrElse(providers.environmentVariable("CI").map { it.toBoolean() }.getOrElse(false))
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

// Ensure aboutlibraries.json is always up-to-date during the build.
// This is required since AboutLibraries v11+ no longer auto-exports.
tasks.named("processResources") { dependsOn("exportLibraryDefinitions") }
