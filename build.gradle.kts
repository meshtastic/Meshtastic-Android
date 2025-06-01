plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.protobuf)
}


android {
    namespace = "com.meshtastic.android.meshserviceexample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshtastic.android.meshserviceexample"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11" // match Java 11
    }
    buildFeatures {
        aidl = true
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
                create("java")
                create("kotlin")
            }
        }
    }
}


dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    //Protobuf
    implementation(libs.bundles.protobuf)

    //Serialization
    implementation(libs.kotlinx.serialization.json)

    //OSM
    implementation(libs.bundles.osm)
    implementation(libs.osmdroid.geopackage) {
        exclude(group = "com.j256.ormlite")
    }
}
