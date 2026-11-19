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

import org.meshtastic.buildlogic.resolveVersionInfo

plugins {
    alias(libs.plugins.meshtastic.web.application)
    alias(libs.plugins.meshtastic.kmp.library.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.meshtastic.koin)
    alias(libs.plugins.meshtastic.aboutlibraries)
}

// ── Version resolution (shared with androidApp/desktopApp via build-logic) ──
val versionInfo = resolveVersionInfo()

// ── Generate WebBuildConfig — mirrors desktopApp's generateDesktopBuildConfig ──
// Writing one small file is cheaper than a cache lookup, so the task is not cacheable.
abstract class GenerateWebBuildConfigTask : DefaultTask() {
    @get:Input abstract val versionCode: Property<Int>

    @get:Input abstract val versionName: Property<String>

    @get:Input abstract val debug: Property<Boolean>

    @get:Input abstract val minFwVersion: Property<String>

    @get:Input abstract val absMinFwVersion: Property<String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("WebBuildConfig.kt")
            .writeText(
                """
                |package org.meshtastic.web
                |
                |/**
                | * Auto-generated build configuration for Meshtastic Web.
                | * Do not edit — values are derived from config.properties and git at build time.
                | */
                |object WebBuildConfig {
                |    const val VERSION_CODE: Int = ${versionCode.get()}
                |    const val VERSION_NAME: String = "${versionName.get()}"
                |    const val IS_DEBUG: Boolean = ${debug.get()}
                |    const val APPLICATION_ID: String = "org.meshtastic.MeshtasticWeb"
                |    const val MIN_FW_VERSION: String = "${minFwVersion.get()}"
                |    const val ABS_MIN_FW_VERSION: String = "${absMinFwVersion.get()}"
                |}
                |
                """
                    .trimMargin(),
            )
    }
}

val buildConfigOutputDir = layout.buildDirectory.dir("generated/buildconfig")

val generateWebBuildConfig =
    tasks.register<GenerateWebBuildConfigTask>("generateWebBuildConfig") {
        versionCode.set(versionInfo.versionCode)
        versionName.set(versionInfo.versionName)
        // Release unless -Pweb.release=false. The development and production executables link the same compilation,
        // so a debug build cannot be tied to one distribution; asking for it is explicit.
        debug.set(providers.gradleProperty("web.release").map { !it.toBoolean() }.orElse(false))
        minFwVersion.set(versionInfo.minFwVersion)
        absMinFwVersion.set(versionInfo.absMinFwVersion)
        outputDir.set(buildConfigOutputDir.map { it.dir("org/meshtastic/web") })
    }

// The manifest icon is the Android launcher art and the favicon the desktop tray glyph, copied in, not kept twice.
val pwaIconsDir = layout.buildDirectory.dir("generated/pwaIcons")
val launcherArt = layout.projectDirectory.dir("../androidApp/src/main")

val copyPwaIcons =
    tasks.register<Sync>("copyPwaIcons") {
        from(launcherArt.file("ic_launcher2-playstore.png")) { rename { "icon-512.png" } }
        from(layout.projectDirectory.file("../desktopApp/src/main/resources/tray_icon_black.svg")) {
            rename { "favicon.svg" }
        }
        into(pwaIconsDir)
    }

kotlin {
    sourceSets {
        wasmJsMain.get().kotlin.srcDir(generateWebBuildConfig.map { buildConfigOutputDir })
        wasmJsMain.get().resources.srcDir(copyPwaIcons)

        wasmJsMain.dependencies {
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
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.ble)

            // The feature modules with a wasmJs target. maplibre-compose publishes no wasmJs variant, so no map.
            implementation(projects.feature.settings)
            implementation(projects.feature.node)
            implementation(projects.feature.messaging)
            implementation(projects.feature.connections)
            implementation(projects.feature.intro)
            implementation(projects.feature.docs)
            implementation(projects.feature.wifiProvision)
            implementation(projects.feature.firmware)
            implementation(projects.feature.discovery)

            // Compose Multiplatform
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.animation)
            implementation(libs.compose.multiplatform.resources)

            // JetBrains Material 3 Adaptive (multiplatform NavigationSuiteScaffold, used by MeshtasticNavigationSuite)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)

            // Navigation 3 (JetBrains fork — multiplatform)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.navigation3.browser)
            implementation(libs.jetbrains.navigationevent.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kermit)
            implementation(libs.okio)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Coil fetches over CoreNetworkWasmJsModule's Js HttpClient.
            implementation(libs.coil)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)
        }
    }
}
