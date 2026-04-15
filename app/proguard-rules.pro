# ============================================================================
# Meshtastic Android — ProGuard / R8 rules for release minification
# ============================================================================
# Open-source project: obfuscation and optimization are disabled. We rely on
# tree-shaking (unused code removal) for APK size reduction.
# ============================================================================

# ---- General ----------------------------------------------------------------

# Preserve line numbers for meaningful crash traces
-keepattributes SourceFile,LineNumberTable

# Open-source — no need to obfuscate
-dontobfuscate

# Disable R8 optimization passes. Tree-shaking (unused code removal) still
# runs — only method-body rewrites and call-site transformations are suppressed.
#
# Why: CMP 1.11 ships consumer rules with -assumenosideeffects on
# Composer.<clinit>() and ComposerImpl.<clinit>(), plus -assumevalues on
# ComposeRuntimeFlags and ComposeStackTraceMode. These optimization directives
# let R8 rewrite *call sites* (class-init triggers, flag reads) even when the
# target classes are preserved by -keep rules. The result is that the Compose
# recomposer/frame-clock/animation state machines silently freeze on their
# first frame in release builds. -dontoptimize is the only directive that
# disables processing of -assumenosideeffects/-assumevalues. See #5146.
-dontoptimize

# Dump the full merged R8 configuration (app rules + all library consumer rules)
# for auditing. Inspect this file after a release build to see what libraries inject.
-printconfiguration build/outputs/mapping/r8-merged-config.txt

# ---- Networking (transitive references from Ktor) ---------------------------

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Wire Protobuf ----------------------------------------------------------

# Wire-generated proto message classes (accessed via ADAPTER companion reflection)
-keep class org.meshtastic.proto.** { *; }

# ---- Room KMP (room3) ------------------------------------------------------

# Preserve generated database constructors (Room uses reflection to instantiate)
-keep class * extends androidx.room3.RoomDatabase { <init>(); }

# ---- Koin DI ----------------------------------------------------------------

# Prevent R8 from merging exception classes (observed as io.ktor.http.URLDecodeException
# replacing Koin's InstanceCreationException in stack traces, making crashes undiagnosable).
-keep class org.koin.core.error.** { *; }

# ---- Compose Runtime & Animation --------------------------------------------

# Defence-in-depth: prevent R8 tree-shaking of Compose infrastructure classes
# that are referenced indirectly through compiler-generated state machines.
# With -dontoptimize above these are largely redundant, but they provide a
# safety net against future toolchain changes.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.animation.core.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }

# ---- Compose Multiplatform --------------------------------------------------

# Keep resource library internals and generated Res accessor classes so R8 does
# not tree-shake the resource loading infrastructure. Without these rules the
# fdroid flavor crashes at startup with a misleading URLDecodeException due to
# R8 exception-class merging.
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.meshtastic.core.resources.** { *; }
