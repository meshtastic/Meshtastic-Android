# ============================================================================
# Meshtastic — Shared ProGuard / R8 rules
# ============================================================================
# Cross-platform keep and dontwarn rules applied to BOTH the Android app
# release build (R8) and the Desktop distribution (ProGuard). Host-specific
# rules live in the per-module proguard-rules.pro file.
#
# Rule of thumb: anything describing a library shared between Android and
# Desktop (Koin, kotlinx-serialization, Wire, Room KMP, Ktor, Coil 3, Kable,
# Kermit, Okio, DataStore, Paging, Lifecycle / Navigation 3, AboutLibraries,
# Markdown renderer, QRCode, Compose Multiplatform resources, core modules)
# belongs here. Anything platform-specific (AWT/Skiko/JNA, AIDL, Android
# framework, JDK-version quirks, flavor specifics) stays in the host file.
# ============================================================================

# ---- Attributes -------------------------------------------------------------

# Preserve line numbers for meaningful stack traces, plus metadata needed for
# reflective serializer/DI/Room lookups.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations

# ---- Kotlin / Coroutines ----------------------------------------------------

-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.coroutines.Continuation { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- Koin DI (reflection-based injection) -----------------------------------

# Prevent R8 from merging exception classes (observed as io.ktor.http.URLDecodeException
# replacing Koin's InstanceCreationException in stack traces, making crashes
# undiagnosable). Broadened to all of koin core to cover the KSP-generated graph.
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep Koin-annotated modules/components so Koin Annotations (KSP) output
# survives tree-shaking.
-keep @org.koin.core.annotation.Module class * { *; }
-keep @org.koin.core.annotation.ComponentScan class * { *; }
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }

# Generated Koin module extensions (Koin Annotations plugin output)
-keep class org.meshtastic.**.di.** { *; }

# ---- kotlinx-serialization --------------------------------------------------

-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep @Serializable classes and their generated $serializer companions
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **.$serializer { *; }
-keepclassmembers class **.$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Wire Protobuf ----------------------------------------------------------

# Wire generates ADAPTER companion objects accessed via reflection
-keep class com.squareup.wire.** { *; }
-dontwarn com.squareup.wire.**

# Generated proto message classes (both meshtastic protos and internal package)
-keep class org.meshtastic.proto.** { *; }
-keep class meshtastic.** { *; }

# Suppress warnings about missing Android Parcelable (Wire cross-platform stubs
# when compiling for non-Android JVM targets; harmless on Android).
-dontwarn android.os.Parcel**
-dontwarn android.os.Parcelable**

# ---- Room KMP (room3) -------------------------------------------------------

# Preserve generated database constructors (Room uses reflection to instantiate)
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keep class * implements androidx.room3.RoomDatabaseConstructor { *; }

# Keep the expect/actual MeshtasticDatabaseConstructor + database surface
-keep class org.meshtastic.core.database.MeshtasticDatabaseConstructor { *; }
-keep class org.meshtastic.core.database.MeshtasticDatabase { *; }

# Room DAOs — Room generates implementations at compile time; keep interfaces
-keep class org.meshtastic.core.database.dao.** { *; }

# Room Entities — accessed via reflection for column mapping
-keep class org.meshtastic.core.database.entity.** { *; }

# Room TypeConverters — invoked reflectively
-keep class org.meshtastic.core.database.Converters { *; }

# Room generated _Impl classes
-keep class **_Impl { *; }

# ---- SQLite bundled --------------------------------------------------------

-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ---- Ktor (ServiceLoader + plugin discovery) --------------------------------

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep ServiceLoader metadata files
-keepclassmembers class * implements io.ktor.client.HttpClientEngineFactory { *; }

# ---- Coil 3 (image loading) -------------------------------------------------

-keep class coil3.** { *; }
-dontwarn coil3.**

# ---- Kable BLE --------------------------------------------------------------

-keep class com.juul.kable.** { *; }
-dontwarn com.juul.kable.**

# ---- Compose Multiplatform resources ----------------------------------------

# Generated resource accessor classes (Res.string.*, Res.drawable.*, etc.).
# Without these the fdroid flavor has crashed at startup with a misleading
# URLDecodeException due to R8 exception-class merging.
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.meshtastic.core.resources.** { *; }

# ---- AboutLibraries ---------------------------------------------------------

-keep class com.mikepenz.aboutlibraries.** { *; }
-dontwarn com.mikepenz.aboutlibraries.**

# ---- Multiplatform Markdown Renderer ----------------------------------------

-keep class com.mikepenz.markdown.** { *; }
-dontwarn com.mikepenz.markdown.**

# ---- QR Code Kotlin ---------------------------------------------------------

-keep class io.github.g0dkar.qrcode.** { *; }
-dontwarn io.github.g0dkar.qrcode.**
-keep class qrcode.** { *; }
-dontwarn qrcode.**

# ---- Kermit logging ---------------------------------------------------------

-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ---- Okio -------------------------------------------------------------------

-keep class okio.** { *; }
-dontwarn okio.**

# ---- DataStore --------------------------------------------------------------

-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---- Paging -----------------------------------------------------------------

-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# ---- Lifecycle / Navigation 3 / ViewModel (JetBrains forks) -----------------

-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation3.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation3.**

# ---- Meshtastic shared model ------------------------------------------------

# Core model classes (used in serialization, Room, and Koin injection)
-keep class org.meshtastic.core.model.** { *; }

# ---- Compose Runtime & Animation --------------------------------------------

# Defence-in-depth: prevent tree-shaking of Compose infrastructure classes that
# are referenced indirectly through compiler-generated state machines. Applies
# to BOTH R8 (Android app) and ProGuard (desktop distribution).
#
# Why shared: CMP 1.11 ships consumer rules with -assumenosideeffects on
# Composer.<clinit>() / ComposerImpl.<clinit>() and -assumevalues on
# ComposeRuntimeFlags / ComposeStackTraceMode. If the optimizer runs (R8 full
# mode on Android, ProGuard with optimize.set(true) on desktop) these call
# sites can be rewritten even when the target classes are kept, causing the
# recomposer / frame-clock / animation state machines to silently freeze on
# the first frame. -dontoptimize (set per-host) is the primary defence; these
# keep rules are a safety net against future toolchain changes. See #5146.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.animation.core.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
