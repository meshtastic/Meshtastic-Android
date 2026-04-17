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
# Kotlin stdlib and kotlinx-coroutines ship their own consumer ProGuard rules
# (kotlin-stdlib and kotlinx-coroutines-core consumer-rules.pro) which keep
# Metadata, Continuation, kotlin.reflect internals, and debug metadata. No
# explicit wildcards needed here.

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
-keep @org.koin.core.annotation.KoinViewModel class * { *; }

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

# Wire generates an ADAPTER static field on every Message subclass accessed
# reflectively during encoding/decoding. Keep those fields and the
# ProtoAdapter subclasses themselves; Wire's bundled consumer rules preserve
# the runtime itself.
-keepclassmembers class * extends com.squareup.wire.Message {
    public static *** ADAPTER;
}
-keepclassmembers class * extends com.squareup.wire.ProtoAdapter { *; }

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

# Room's own consumer rules (from androidx.room3) keep DAOs, entities,
# generated _Impl classes, and TypeConverters referenced from the database.

# ---- SQLite bundled --------------------------------------------------------
# androidx.sqlite ships consumer rules.

# ---- Ktor (ServiceLoader + plugin discovery) --------------------------------

# Keep ServiceLoader metadata files (ktor discovers HttpClientEngineFactory
# implementations reflectively via ServiceLoader).
-keepclassmembers class * implements io.ktor.client.HttpClientEngineFactory { *; }

# ---- Coil 3 (image loading) -------------------------------------------------
# coil3 ships consumer rules.

# ---- Kable BLE --------------------------------------------------------------
# com.juul.kable ships consumer rules; if release builds fail with missing
# Kable classes, restore a narrow keep for the specific reflection-loaded type.

# ---- Compose Multiplatform resources ----------------------------------------

# Generated resource accessor classes (Res.string.*, Res.drawable.*, etc.).
# Without these the fdroid flavor has crashed at startup with a misleading
# URLDecodeException due to R8 exception-class merging.
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.meshtastic.core.resources.Res { *; }
-keepclassmembers class org.meshtastic.core.resources.Res$* { *; }

# ---- AboutLibraries ---------------------------------------------------------
# com.mikepenz.aboutlibraries ships consumer rules.

# ---- Multiplatform Markdown Renderer ----------------------------------------
# com.mikepenz.markdown ships consumer rules.

# ---- QR Code Kotlin ---------------------------------------------------------

-keep class io.github.g0dkar.qrcode.** { *; }
-dontwarn io.github.g0dkar.qrcode.**
-keep class qrcode.** { *; }
-dontwarn qrcode.**

# ---- Kermit logging ---------------------------------------------------------
# co.touchlab.kermit ships consumer rules.

# ---- Okio -------------------------------------------------------------------
# okio ships consumer rules.

# ---- DataStore --------------------------------------------------------------
# androidx.datastore ships consumer rules.

# ---- Paging -----------------------------------------------------------------
# androidx.paging ships consumer rules.

# ---- Lifecycle / Navigation 3 / ViewModel (JetBrains forks) -----------------
# androidx.lifecycle and androidx.navigation3 ship consumer rules.

# ---- Meshtastic shared model ------------------------------------------------
# core.model types are reached via static references from Koin-wired graphs,
# Room entities, and kotlinx-serialization @Serializable companions — all of
# which have their own keep rules above.

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
