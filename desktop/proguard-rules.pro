# ============================================================================
# Meshtastic Desktop — ProGuard rules for release minification
# ============================================================================
# Open-source project: we rely on tree-shaking (unused code removal) for size
# reduction. Obfuscation is disabled in build.gradle.kts (obfuscate.set(false)).
#
# Key libraries requiring keep-rules (reflection, JNI, code generation):
#   Koin (DI via reflection), kotlinx-serialization (generated serializers),
#   Wire protobuf (ADAPTER reflection), Room KMP (generated DB + converters),
#   Ktor (Java engine + ServiceLoader), Kable BLE, Coil, Compose Multiplatform
#   resources, SQLite bundled (JNI), AboutLibraries.
# ============================================================================

# ---- General ----------------------------------------------------------------

# Preserve line numbers for meaningful stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Suppress notes about duplicate resource files (common in fat JARs)
-dontnote **

# Do not parse/rewrite Kotlin metadata during shrinking/optimization.
# ProGuard's KotlinShrinker cannot handle the metadata produced by Compose
# Multiplatform 1.11.x + Kotlin 2.3.x, causing a NullPointerException.
# Since we disable obfuscation (class names remain stable), metadata references
# stay valid and do not need rewriting. The annotations themselves are preserved
# by -keepattributes *Annotation*.
-dontprocesskotlinmetadata

# ---- Entry point ------------------------------------------------------------

-keep class org.meshtastic.desktop.MainKt { *; }

# ---- Kotlin / Coroutines ---------------------------------------------------

# Keep Kotlin metadata for reflection-dependent libraries
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Coroutines internals
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.Continuation { *; }

# ---- Koin DI (reflection-based injection) -----------------------------------

# Koin core — uses reflection to instantiate definitions
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep all Koin-annotated @Module / @ComponentScan classes and their generated
# counterparts so Koin K2 plugin output survives tree-shaking.
-keep @org.koin.core.annotation.Module class * { *; }
-keep @org.koin.core.annotation.ComponentScan class * { *; }
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }

# Generated Koin module extensions (K2 plugin output)
-keep class org.meshtastic.**.di.** { *; }

# ---- kotlinx-serialization --------------------------------------------------

# The serialization plugin generates companion $serializer classes and
# serializer() factory methods that are invoked reflectively.
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Keep @Serializable classes and their generated serializers
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    # Companion object that holds the serializer() factory
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **.$serializer { *; }
-keep class **.$serializer { *; }
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Wire protobuf ----------------------------------------------------------

# Wire generates ADAPTER companion objects accessed via reflection
-keep class com.squareup.wire.** { *; }
-dontwarn com.squareup.wire.**

# All generated proto message classes
-keep class org.meshtastic.proto.** { *; }
-keep class meshtastic.** { *; }

# Suppress warnings about missing Android Parcelable (Wire cross-platform stubs)
-dontwarn android.os.Parcel**
-dontwarn android.os.Parcelable**

# ---- Room KMP ---------------------------------------------------------------

# Preserve generated database constructors (required for Room's reflective init)
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keep class * implements androidx.room3.RoomDatabaseConstructor { *; }

# Keep the expect/actual MeshtasticDatabaseConstructor
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

# ---- SQLite bundled (JNI) ---------------------------------------------------

-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ---- Ktor (Java engine + ServiceLoader + content negotiation) ---------------

# Ktor uses ServiceLoader and reflection for engine/plugin discovery
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep ServiceLoader metadata files
-keepclassmembers class * implements io.ktor.client.HttpClientEngineFactory { *; }

# Java HTTP client engine
-keep class io.ktor.client.engine.java.** { *; }

# ---- Coil (image loading) ---------------------------------------------------

-keep class coil3.** { *; }
-dontwarn coil3.**

# ---- Kable BLE --------------------------------------------------------------

-keep class com.juul.kable.** { *; }
-dontwarn com.juul.kable.**

# ---- Compose Multiplatform resources ----------------------------------------

# Generated resource accessor classes (Res.string.*, Res.drawable.*, etc.)
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

# ---- Kermit logging ----------------------------------------------------------

-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ---- Okio -------------------------------------------------------------------

-dontwarn okio.**
-keep class okio.** { *; }

# ---- DataStore --------------------------------------------------------------

-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---- Paging -----------------------------------------------------------------

-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# ---- Lifecycle / Navigation / ViewModel (JetBrains forks) -------------------

-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation3.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.navigation3.**

# ---- Meshtastic application code --------------------------------------------

# Keep all desktop module classes (thin host shell — not worth tree-shaking)
-keep class org.meshtastic.desktop.** { *; }

# Core model classes (used in serialization, Room, and Koin injection)
-keep class org.meshtastic.core.model.** { *; }

# ---- JVM runtime suppression ------------------------------------------------

-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# ---- jSerialComm (cross-platform serial library with Android stubs) ---------

-dontwarn com.fazecast.jSerialComm.android.**

# ---- Kotlin stdlib atomics (Kotlin 2.3+ intrinsics, not on JDK 17) ----------

-dontwarn kotlin.concurrent.atomics.**
-dontwarn kotlin.uuid.UuidV7Generator
