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

import com.android.build.api.dsl.ApplicationExtension
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.meshtastic.buildlogic.GitVersionValueSource
import org.meshtastic.buildlogic.configProperties
import java.io.FileInputStream
import java.util.Properties

val gitVersionProvider = providers.of(GitVersionValueSource::class.java) {}

plugins {
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.flavors)
    alias(libs.plugins.meshtastic.android.application.compose)
    id("meshtastic.koin")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.secrets)
    alias(libs.plugins.aboutlibraries)
    id("dev.mokkery")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

configure<ApplicationExtension> {
    namespace = "org.meshtastic.app"

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    defaultConfig {
        applicationId = configProperties.getProperty("APPLICATION_ID")

        val vcOffset = configProperties.getProperty("VERSION_CODE_OFFSET")?.toInt() ?: 0
        println("Version code offset: $vcOffset")
        versionCode =
            (
                project.findProperty("android.injected.version.code")?.toString()?.toInt()
                    ?: System.getenv("VERSION_CODE")?.toInt()
                    ?: (gitVersionProvider.get().toInt() + vcOffset)
                )
        versionName =
            (
                project.findProperty("android.injected.version.name")?.toString()
                    ?: System.getenv("VERSION_NAME")
                    ?: configProperties.getProperty("VERSION_NAME_BASE")
                )
        buildConfigField("String", "MIN_FW_VERSION", "\"${configProperties.getProperty("MIN_FW_VERSION")}\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${configProperties.getProperty("ABS_MIN_FW_VERSION")}\"")
        // We have to list all translated languages here,
        // because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        @Suppress("UnstableApiUsage")
        val ci = project.findProperty("ci")?.toString()?.toBoolean() ?: false
        if (ci) {
            println("CI build detected - limiting locale filters for faster packaging")
            androidResources.localeFilters.addAll(listOf("en"))
        } else {
            androidResources.localeFilters.addAll(
                listOf(
                    "en",
                    "ar",
                    "bg",
                    "ca",
                    "cs",
                    "de",
                    "el",
                    "es",
                    "et",
                    "fi",
                    "fr",
                    "ga",
                    "gl",
                    "hr",
                    "ht",
                    "hu",
                    "is",
                    "it",
                    "iw",
                    "ja",
                    "ko",
                    "lt",
                    "nl",
                    "no",
                    "pl",
                    "pt",
                    "pt-rBR",
                    "ro",
                    "ru",
                    "sk",
                    "sl",
                    "sq",
                    "sr",
                    "srp",
                    "sv",
                    "tr",
                    "uk",
                    "zh-rCN",
                    "zh-rTW",
                ),
            )
        }
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }

        val disableSplits =
            project.gradle.startParameter.taskNames.any {
                it.contains("bundle", ignoreCase = true) || it.contains("google", ignoreCase = true)
            }

        // Enable ABI splits to generate smaller APKs per architecture for F-Droid/IzzyOnDroid
        splits {
            abi {
                isEnable = !disableSplits
                reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                isUniversalApk = true
            }
        }

        dependenciesInfo {
            // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
            includeInApk = false
            // Disables dependency metadata when building Android App Bundles (for Google Play)
            includeInBundle = false
        }

        testInstrumentationRunner = "org.meshtastic.app.TestRunner"
    }

    // Configure existing product flavors (defined by convention plugin)
    // with their dynamic version names.
    productFlavors {
        configureEach {
            versionName = "${defaultConfig.versionName} (${defaultConfig.versionCode}) $name"
            if (name == "google") {
                manifestPlaceholders["MAPS_API_KEY"] = "dummy"
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
        }
    }
    bundle { language { enableSplit = false } }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.flavorName?.let { flavor -> variant.applicationId = "com.geeksville.mesh.$flavor.debug" }
    }

    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.flavorName == "google") {
            val variantNameCapped = variant.name.replaceFirstChar { it.uppercase() }
            val minifyTaskName = "minify${variantNameCapped}WithR8"
            val uploadTaskName = "uploadMapping$variantNameCapped"
            if (project.tasks.findByName(uploadTaskName) != null && project.tasks.findByName(minifyTaskName) != null) {
                tasks.named(minifyTaskName).configure { finalizedBy(uploadTaskName) }
            }
        }
    }
}

project.afterEvaluate {
    logger.lifecycle(
        "Version code is set to: ${extensions.getByType<ApplicationExtension>().defaultConfig.versionCode}",
    )
}

dependencies {
    implementation(projects.core.ble)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.nfc)
    implementation(projects.core.prefs)
    implementation(projects.core.proto)
    implementation(projects.core.service)
    implementation(projects.core.resources)
    implementation(projects.core.ui)
    implementation(projects.core.barcode)
    implementation(projects.feature.intro)
    implementation(projects.feature.messaging)
    implementation(projects.feature.connections)
    implementation(projects.feature.map)
    implementation(projects.feature.node)
    implementation(projects.feature.settings)
    implementation(projects.feature.firmware)
    implementation(projects.feature.widget)

    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.navigation3.runtime)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.androidx.paging.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.usb.serial.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    implementation(libs.accompanist.permissions)
    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.glance.preview)

    googleImplementation(libs.location.services)
    googleImplementation(libs.play.services.maps)
    googleImplementation(libs.maps.compose)
    googleImplementation(libs.maps.compose.utils)
    googleImplementation(libs.maps.compose.widgets)
    googleImplementation(libs.dd.sdk.android.okhttp)
    googleImplementation(libs.dd.sdk.android.compose)
    googleImplementation(libs.dd.sdk.android.logs)
    googleImplementation(libs.dd.sdk.android.rum)
    googleImplementation(libs.dd.sdk.android.timber)
    googleImplementation(libs.dd.sdk.android.trace)
    googleImplementation(libs.dd.sdk.android.trace.otel)
    googleImplementation(platform(libs.firebase.bom))
    googleImplementation(libs.firebase.analytics)
    googleImplementation(libs.firebase.crashlytics)

    fdroidImplementation(libs.osmdroid.android)
    fdroidImplementation(libs.osmdroid.geopackage) { exclude(group = "com.j256.ormlite") }
    fdroidImplementation(libs.osmbonuspack)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.koin.test)

    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.koin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.glance.appwidget)
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
tasks
    .matching { it.name.startsWith("process") && it.name.endsWith("Resources") }
    .configureEach { dependsOn("exportLibraryDefinitions") }
