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
    alias(libs.plugins.spotless)
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
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 30630
        testInstrumentationRunner = "com.geeksville.mesh.TestRunner"
        buildConfigField("String", "MIN_FW_VERSION", "\"${Configs.MIN_FW_VERSION}\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${Configs.ABS_MIN_FW_VERSION}\"")
        // per https://developer.android.com/studio/write/vector-asset-studio
        vectorDrawables.useSupportLibrary = true
        // We have to list all translated languages here,
        // because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        @Suppress("UnstableApiUsage")
        androidResources.localeFilters.addAll(
            listOf(
                "bg",
                "ca",
                "cs",
                "de",
                "el",
                "en",
                "es",
                "et",
                "fi",
                "fr",
                "fr-rHT",
                "ga",
                "gl",
                "hr",
                "hu",
                "is",
                "it",
                "iw",
                "ja",
                "ko",
                "lt",
                "nl",
                "nb",
                "pl",
                "pt",
                "pt-rBR",
                "ro",
                "ru",
                "sk",
                "sl",
                "sq",
                "sr",
                "sv",
                "tr",
                "zh-rCN",
                "zh-rTW",
                "uk",
            ),
        )
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    flavorDimensions.add("default")
    productFlavors {
        val versionCode = defaultConfig.versionCode
        create("fdroid") {
            dimension = "default"
            dependenciesInfo { includeInApk = false }
            versionName = "${Configs.VERSION_NAME_BASE} ($versionCode) fdroid"
        }
        create("google") {
            dimension = "default"
            // Enable Firebase Crashlytics for Google Play builds
            apply(plugin = libs.plugins.google.services.get().pluginId)
            apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
            versionName = "${Configs.VERSION_NAME_BASE} ($versionCode) google"
        }
    }
    buildTypes {
        named("release") {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        named("debug") { isPseudoLocalesEnabled = true }
    }
    bundle { language { enableSplit = false } }
    buildFeatures {
        viewBinding = true
        compose = true
        aidl = true
        buildConfig = true
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

kotlin {
    compilerOptions {
        jvmToolchain(21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xcontext-receivers",
            "-Xannotation-default-target=param-property",
        )
    }
}

// per protobuf-gradle-plugin docs, this is recommended for android
protobuf {
    protoc { artifact = libs.protobuf.protoc.get().toString() }
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
            tasks.named("ksp${capName}Kotlin") { dependsOn("generate${capName}Proto") }
        }
    }
}

dependencies {
    implementation(project(":network"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Bundles
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.ui)
    implementation(libs.bundles.markdown)
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
    implementation(libs.accompanist.permissions)

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

repositories { maven { url = uri("https://jitpack.io") } }

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline.xml")
}

val googleServiceKeywords = listOf("crashlytics", "google")

tasks.configureEach {
    if (
        googleServiceKeywords.any { name.contains(it, ignoreCase = true) } && name.contains("fdroid", ignoreCase = true)
    ) {
        project.logger.lifecycle("Disabling task for F-Droid: $name")
        enabled = false
    }
}

spotless {
    ratchetFrom("origin/main")
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint("1.7.1").setEditorConfigPath("../config/spotless/.editorconfig")
        licenseHeaderFile(rootProject.file("config/spotless/copyright.txt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint("1.7.1").setEditorConfigPath("../config/spotless/.editorconfig")
    }
}
