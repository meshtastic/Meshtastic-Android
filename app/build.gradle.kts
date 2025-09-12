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

import com.google.protobuf.gradle.proto
import java.io.FileInputStream
import java.util.Properties

// FIXME: fdroid branching logic + anything else that might have been munged

plugins {
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.flavors)
    alias(libs.plugins.meshtastic.android.application.compose)
    alias(libs.plugins.meshtastic.android.application.firebase)
    alias(libs.plugins.meshtastic.android.lint)
    alias(libs.plugins.meshtastic.hilt)
    alias(libs.plugins.meshtastic.android.room)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.spotless)
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

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    defaultConfig {
        // Prioritize injected props, then ENV, then fallback to git commit count
        versionCode =
            (
                project.findProperty("android.injected.version.code")?.toString()?.toInt()
                    ?: System.getenv("VERSION_CODE")?.toInt()
                    ?: 1
                )
        versionName =
            (
                project.findProperty("android.injected.version.name")?.toString()
                    ?: System.getenv("VERSION_NAME")
                    ?: "0.0.1"
                )
        testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
        buildConfigField("String", "MIN_FW_VERSION", "\"1.0.0\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"1.0.0\"")
        // per https://developer.android.com/studio/write/vector-asset-studio
        vectorDrawables.useSupportLibrary = true
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
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "xBG", "xBG_64") }
    }
    buildTypes {
        release {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            }
        }
    }
    bundle { language { enableSplit = false } }
    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }
    sourceSets {
        named("main") {
            proto { srcDir("src/main/proto") }
        }
        // Adds exported schema location as test app assets.
        named("androidTest") { assets.srcDirs(files("$projectDir/schemas")) }
    }
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

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc { artifact = libs.protoc.get().toString() }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {}
                create("kotlin") {}
            }
        }
    }
}

// workaround for https://github.com/google/ksp/issues/1590
androidComponents {
    onVariants(selector().all()) { variant ->
        project.afterEvaluate {
            val variantNameCapped = variant.name.replaceFirstChar { it.uppercase() }
            tasks.named("ksp${variantNameCapped}Kotlin") { dependsOn("generate${variantNameCapped}Proto") }
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

project.afterEvaluate { logger.lifecycle("Version code is set to: ${android.defaultConfig.versionCode}") }

dependencies {
    implementation(project(":network"))
    // Bundles
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.markdown)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.datastore)
    implementation(libs.bundles.protobuf)
    implementation(libs.bundles.coil)

    // OSM
    "fdroidImplementation"(libs.bundles.osm)
    "fdroidImplementation"(libs.osmdroid.geopackage) { exclude(group = "com.j256.ormlite") }

    "googleImplementation"(libs.bundles.maps.compose)

    // ZXing
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)

    // Individual dependencies
    "googleImplementation"(libs.awesome.app.rating)
    "googleImplementation"(libs.bundles.datadog)
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


    // Testing
    testImplementation(libs.bundles.testing)
    debugImplementation(libs.bundles.testing.android.manifest)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.bundles.testing.navigation)

    dokkaPlugin(libs.dokka.android.documentation.plugin)
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}

val googleServiceKeywords = listOf("crashlytics", "google", "datadog")

tasks.configureEach {
    if (
        googleServiceKeywords.any { name.contains(it, ignoreCase = true) } && name.contains("fdroid", ignoreCase = true)
    ) {
        project.logger.lifecycle("Disabling task for F-Droid: $name")
        enabled = false
    }
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