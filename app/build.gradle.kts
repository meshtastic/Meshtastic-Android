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
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.aboutlibraries)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

configure<ApplicationExtension> {
    namespace = configProperties.getProperty("APPLICATION_ID")

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

        dependenciesInfo {
            // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
            includeInApk = false
            // Disables dependency metadata when building Android App Bundles (for Google Play)
            includeInBundle = false
        }

        testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
    }

    // Configure existing product flavors (defined by convention plugin)
    // with their dynamic version names.
    productFlavors {
        named("google") { versionName = "${defaultConfig.versionName} (${defaultConfig.versionCode}) google" }
        named("fdroid") { versionName = "${defaultConfig.versionName} (${defaultConfig.versionCode}) fdroid" }
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
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (variant.name == "fdroidDebug") {
            variant.applicationId = "com.geeksville.mesh.fdroid.debug"
        }

        if (variant.name == "googleDebug") {
            variant.applicationId = "com.geeksville.mesh.google.debug"
        }
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
    implementation(projects.core.analytics)
    implementation(projects.core.ble)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.nfc)
    implementation(projects.core.prefs)
    implementation(projects.core.proto)
    implementation(projects.core.service)
    implementation(projects.core.strings)
    implementation(projects.core.ui)
    implementation(projects.core.barcode)
    implementation(projects.feature.intro)
    implementation(projects.feature.messaging)
    implementation(projects.feature.map)
    implementation(projects.feature.node)
    implementation(projects.feature.settings)
    implementation(projects.feature.firmware)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.navigationSuite)
    implementation(libs.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.usb.serial.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.accompanist.permissions)
    implementation(libs.kermit)

    implementation(libs.nordic.client.android)
    implementation(libs.nordic.common.core)
    implementation(libs.nordic.common.permissions.ble)
    implementation(libs.nordic.common.permissions.notification)
    implementation(libs.nordic.common.scanner.ble)
    implementation(libs.nordic.common.ui)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    googleImplementation(libs.location.services)
    googleImplementation(libs.play.services.maps)

    fdroidImplementation(libs.osmdroid.android)
    fdroidImplementation(libs.osmdroid.geopackage) { exclude(group = "com.j256.ormlite") }

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.nordic.client.android.mock)
    testImplementation(libs.nordic.client.core.mock)
    testImplementation(libs.nordic.core.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

aboutLibraries {
    export { excludeFields = listOf("generated") }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}
