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

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.detekt)
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
    compileSdk = Configs.COMPILE_SDK
    defaultConfig {
        applicationId = Configs.APPLICATION_ID
        minSdk = Configs.MIN_SDK_VERSION
        targetSdk = Configs.TARGET_SDK
        versionCode =
            Configs.VERSION_CODE // format is Mmmss (where M is 1+the numeric major number)
        versionName = Configs.VERSION_NAME
        testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
        buildConfigField("String", "MIN_FW_VERSION", "\"${Configs.MIN_FW_VERSION}\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${Configs.ABS_MIN_FW_VERSION}\"")
        // per https://developer.android.com/studio/write/vector-asset-studio
        vectorDrawables.useSupportLibrary = true
    }
    flavorDimensions.add("default")
    productFlavors {
        create("fdroid") {
            dimension = "default"
            dependenciesInfo {
                includeInApk = false
            }
        }
        create("google") {
            dimension = "default"
            if (Configs.USE_CRASHLYTICS) {
                // Enable Firebase Crashlytics for Google Play builds
                apply(plugin = libs.plugins.google.services.get().pluginId)
                apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
            }
        }
    }
    buildTypes {
        named("release") {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        named("debug") {
            isPseudoLocalesEnabled = true
        }
    }
    defaultConfig {
        // We have to list all translated languages here, because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        androidResources.localeFilters += listOf(
            "bg", "ca", "cs", "de",
            "el", "en", "es", "et",
            "fi", "fr", "fr-rHT", "ga",
            "gl", "hr", "hu", "is",
            "it", "iw", "ja", "ko",
            "lt", "nl", "nb", "pl",
            "pt", "pt-rBR", "ro",
            "ru", "sk", "sl", "sq",
            "sr", "sv", "tr", "zh-rCN",
            "zh-rTW", "uk"
        )

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        viewBinding = true
        compose = true
        aidl = true
        buildConfig = true
    }
    // Configure the build-logic plugins to target JDK 17
    // This matches the JDK used to build the project, and is not related to what is running on device.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    lint {
        abortOnError = false
        disable.add("MissingTranslation")
    }
    sourceSets {
        // Adds exported schema location as test app assets.
        named("androidTest") { assets.srcDirs(files("$projectDir/schemas")) }
    }
}

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
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
            val capName = variant.name.replaceFirstChar { it.uppercase() }
            tasks.named("ksp${capName}Kotlin") {
                dependsOn("generate${capName}Proto")
            }
        }
    }
}

dependencies {
    implementation(project(":network"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Bundles
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.ui)
    debugImplementation(libs.bundles.ui.tooling)
    implementation(libs.bundles.adaptive)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.navigation)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.datastore)
    implementation(libs.bundles.room)
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.protobuf)
    implementation(libs.bundles.coil)

    // OSM
    implementation(libs.bundles.osm)
    implementation(libs.osmdroid.geopackage) { exclude(group = "com.j256.ormlite") }

    // ZXing
    implementation(libs.zxing.android.embedded) { isTransitive = false }
    implementation(libs.zxing.core)

    // Individual dependencies
    implementation(libs.appintro)
    "googleImplementation"(libs.awesome.app.rating)
    implementation(libs.core.splashscreen)
    implementation(libs.emoji2.emojipicker)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.streamsupport.minifuture)
    implementation(libs.usb.serial.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.core.location.altitude)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    // Firebase BOM
    "googleImplementation"(platform(libs.firebase.bom))
    "googleImplementation"(libs.bundles.firebase)

    // ksp
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)
    kspAndroidTest(libs.hilt.compiler)

    // Testing
    testImplementation(libs.bundles.testing)
    debugImplementation(libs.bundles.testing.android.manifest)
    androidTestImplementation(libs.bundles.testing.android)
    androidTestImplementation(libs.bundles.testing.hilt)
    androidTestImplementation(libs.bundles.testing.navigation)
    androidTestImplementation(libs.bundles.testing.room)

    detektPlugins(libs.detekt.formatting)
}

ksp {
//    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

repositories {
    maven { url = uri("https://jitpack.io") }
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline.xml")
}
