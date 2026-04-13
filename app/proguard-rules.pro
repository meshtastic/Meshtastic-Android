# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Room KMP: preserve generated database constructor (required for R8/ProGuard)
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Needed for protobufs
-keep class com.google.protobuf.** { *; }
-keep class org.meshtastic.proto.** { *; }

# Networking
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ?
-dontwarn java.lang.reflect.**
-dontwarn com.google.errorprone.annotations.**

# Our app is opensource no need to obsfucate
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Koin DI: prevent R8 from merging exception classes (observed as io.ktor.http.URLDecodeException
# replacing Koin's InstanceCreationException in stack traces, making crashes undiagnosable).
-keep class org.koin.core.error.** { *; }

# R8 optimization for Kotlin null checks (AGP 9.0+)
-processkotlinnullchecks remove

# Compose Multiplatform resources: keep the resource library internals and generated Res
# accessor classes so R8 does not tree-shake the resource loading infrastructure.
# Without these rules the fdroid flavor (which has fewer transitive Compose dependencies
# than google) crashes at startup with a misleading URLDecodeException due to R8
# exception-class merging (see Koin keep rule above).
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.meshtastic.core.resources.** { *; }

# Compose Animation: R8 can tree-shake or merge animation spec classes (easing curves,
# transition specs, Animatable internals) since they appear as small single-use types.
# This causes animations to silently snap or skip in release builds.
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.animation.core.** { *; }

# Nordic BLE
-dontwarn no.nordicsemi.kotlin.ble.environment.android.mock.**
-keep class no.nordicsemi.kotlin.ble.environment.android.mock.** { *; }
-keep class no.nordicsemi.kotlin.ble.environment.android.compose.** { *; }
