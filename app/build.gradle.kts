/*
 * Copyright (c) 2025 Meshtastic LLC
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

import com.geeksville.mesh.buildlogic.Configs
import com.geeksville.mesh.buildlogic.GitVersionValueSource
import java.io.FileInputStream
import java.util.Properties

val gitVersionProvider = providers.of(GitVersionValueSource::class.java) {}

plugins {
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.flavors)
    alias(libs.plugins.meshtastic.android.application.compose)
    alias(libs.plugins.meshtastic.android.application.firebase)
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.meshtastic.android.room)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.datadog)
    alias(libs.plugins.secrets)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kover)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

android {
    namespace = "com.geeksville.mesh"
    // Assuming Configs object is available (e.g., from buildSrc)
    compileSdk = Configs.COMPILE_SDK

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    defaultConfig {
        applicationId = Configs.APPLICATION_ID
        minSdk = Configs.MIN_SDK
        targetSdk = Configs.TARGET_SDK

        // Prioritize injected props, then ENV, then fallback to git commit count
        versionCode =
            (
                project.findProperty("android.injected.version.code")?.toString()?.toInt()
                    ?: System.getenv("VERSION_CODE")?.toInt()
                    ?: gitVersionProvider.get().toInt() // Restored GitVersionValueSource fallback
                )
        versionName =
            (
                project.findProperty("android.injected.version.name")?.toString()
                    ?: System.getenv("VERSION_NAME")
                    ?: Configs.VERSION_NAME_BASE // Restored Configs.VERSION_NAME_BASE fallback
                )
        buildConfigField("String", "MIN_FW_VERSION", "\"${Configs.MIN_FW_VERSION}\"") // Used Configs
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${Configs.ABS_MIN_FW_VERSION}\"") // Used Configs
        // We have to list all translated languages here,
        // because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        @Suppress("UnstableApiUsage")
        androidResources.localeFilters.addAll(
            listOf(
                "en",
                "ar-rSA",
                "b+sr+Latn",
                "bg-rBG",
                "ca-rES",
                "cs-rCZ",
                "de-rDE",
                "el-rGR",
                "es-rES",
                "et-rEE",
                "fi-rFI",
                "fr-rFR",
                "ga-rIE",
                "gl-rES",
                "hr-rHR",
                "ht-rHT",
                "hu-rHU",
                "is-rIS",
                "it-rIT",
                "iw-rIL",
                "ja-rJP",
                "ko-rKR",
                "lt-rLT",
                "nl-rNL",
                "no-rNO",
                "pl-rPL",
                "pt-rBR",
                "pt-rPT",
                "ro-rRO",
                "ru-rRU",
                "sk-rSK",
                "sl-rSI",
                "sq-rAL",
                "srp",
                "sv-rSE",
                "tr-rTR",
                "uk-rUA",
                "zh-rCN",
                "zh-rTW",
            ),
        )
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
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
        }
    }
    bundle { language { enableSplit = false } }
    buildFeatures { aidl = true }
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}

datadog {
    //    if (!gradle.startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }) {
    //        composeInstrumentation = InstrumentationMode.AUTO
    //    }
}

// workaround for https://github.com/google/ksp/issues/1590
androidComponents {
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

project.afterEvaluate { logger.lifecycle("Version code is set to: ${android.defaultConfig.versionCode}") }

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.prefs)
    implementation(projects.core.proto)
    implementation(projects.core.strings)
    implementation(projects.feature.map)

    // Bundles
    implementation(libs.bundles.markdown)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.datastore)
    implementation(libs.bundles.coil)

    // ZXing
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)

    // Individual dependencies (flavor-specific ones removed)
    implementation(libs.core.splashscreen)
    implementation(libs.emoji2.emojipicker)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.streamsupport.minifuture)
    implementation(libs.usb.serial.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.core.location.altitude)
    implementation(libs.accompanist.permissions)
    implementation(libs.timber)

    dokkaPlugin(libs.dokka.android.documentation.plugin)
}

dokka {
    moduleName.set("Meshtastic App")
    dokkaSourceSets.main {
        sourceLink {
            enableJdkDocumentationLink.set(true)
            enableKotlinStdLibDocumentationLink.set(true)
            enableJdkDocumentationLink.set(true)
            reportUndocumented.set(true)
            localDirectory.set(file("src/main/java"))
            remoteUrl("https://github.com/geeksville/Meshtastic-Android/app/src/main/java")
            remoteLineSuffix.set("#L")
        }
    }
    dokkaPublications.html { suppressInheritedMembers.set(true) }
    dokkaGeneratorIsolation = ProcessIsolation {
        // Configures heap size
        maxHeapSize = "6g"
    }
}
