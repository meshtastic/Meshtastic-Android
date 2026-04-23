# ============================================================================
# Meshtastic — Shared ProGuard / R8 rules
# ============================================================================
# Cross-platform keep rules applied to BOTH the Android app release (R8) and
# the Desktop distribution (ProGuard 7.7 invoked by compose-jb).
#
# IMPORTANT: compose-jb's standalone ProGuard task does NOT auto-include
# `META-INF/proguard/*.pro` consumer rules from dependency jars (only R8 on
# Android does — https://github.com/Guardsquare/proguard/issues/423).
# So this file inlines all the consumer rules we depend on for desktop. On
# Android these are duplicates of what R8 already auto-discovers, which is
# harmless. Per JetBrains compose-multiplatform docs: keep it as a single
# static .pro file and add rules as shrinking surfaces problems.
# ============================================================================

# ---- Attributes -------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,AnnotationDefault

# ---- Compose Multiplatform 1.11 optimizer defense (#5146) -------------------
# CMP 1.11 ships consumer rules with `-assumenosideeffects` on
# Composer.<clinit>() / ComposerImpl.<clinit>() and `-assumevalues` on
# ComposeRuntimeFlags / ComposeStackTraceMode. The primary defence is
# `-dontoptimize` (set per-host in app/desktop proguard-rules.pro), which
# disables rewriting of these directives. Broad package-wide keeps below have
# been removed per R8_Configuration_Analysis.md as they are redundant — rely
# instead on CMP's own consumer rules + @DoNotInline annotations. If animations
# freeze in a future CMP/KGP release, replace with class-level keeps on the
# specific failure points (Composer, ComposerImpl, ComposeRuntimeFlags,
# ComposeStackTraceMode) rather than package-wide wildcards.

# ---- Compose Multiplatform resources ----------------------------------------
-keep class org.meshtastic.core.resources.Res { *; }
-keepclassmembers class org.meshtastic.core.resources.Res$* { *; }

# ---- Koin Annotations (KSP-generated DI graph) ------------------------------
-keep @org.koin.core.annotation.Module class * { *; }
-keep @org.koin.core.annotation.ComponentScan class * { *; }
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }
-keep @org.koin.core.annotation.KoinViewModel class * { *; }

# ---- kotlinx.coroutines (inlined from coroutines.pro consumer rules) --------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-dontwarn java.lang.ClassValue
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ---- kotlinx.serialization (inlined from kotlinx-serialization-common.pro) --
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers class * {
    static <1> *;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# ---- kotlinx.datetime (inlined from datetime.pro consumer rules) ------------
-dontwarn kotlinx.serialization.KSerializer
-dontwarn kotlinx.serialization.Serializable

# ---- Ktor (inlined from ktor.pro + ServiceLoader gap) -----------------------
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
# ktor consumer rules preserve the ServiceLoader META-INF/services file but not
# the impl classes; ContentNegotiation discovers KotlinxSerializationJsonExtensionProvider
# reflectively and crashes with ServiceConfigurationError without these.
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }
-keep class io.ktor.serialization.kotlinx.json.** { *; }

# ---- androidx.annotation.Keep (inlined from androidx-annotations.pro) -------
-keep,allowobfuscation @interface androidx.annotation.Keep
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <init>(...); }
-keepclassmembers,allowobfuscation class * {
  @androidx.annotation.DoNotInline <methods>;
}

# ---- androidx.datastore (inlined from datastore-preferences-core.pro) -------
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# ---- Wire Protobuf (no consumer rules shipped) ------------------------------
-keepclassmembers class * extends com.squareup.wire.Message {
    public static *** ADAPTER;
}
-keepclassmembers class * extends com.squareup.wire.ProtoAdapter { *; }

# ---- androidx.sqlite bundled driver (JNI native bridge) ---------------------
# androidx.sqlite-bundled's consumer rule keeps native methods only — but the
# bundled JNI library calls back into JVM methods on the driver class
# (e.g. `nativeThreadSafeMode`). Keep the whole driver package.
-keep class androidx.sqlite.driver.bundled.** { *; }
-keepclassmembers class androidx.sqlite.driver.bundled.** { native <methods>; *; }

# ---- Room KMP (room3) -------------------------------------------------------
-keep class * extends androidx.room3.RoomDatabase { <init>(); }
-keep class * implements androidx.room3.RoomDatabaseConstructor { *; }
-keep class org.meshtastic.core.database.MeshtasticDatabaseConstructor { *; }
-keep class org.meshtastic.core.database.MeshtasticDatabase { *; }
