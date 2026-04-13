# ============================================================================
# Meshtastic Android — ProGuard / R8 rules for release minification
# ============================================================================
# Open-source project: obfuscation is disabled. We rely on tree-shaking and
# code optimization for APK size reduction.
# ============================================================================

# ---- General ----------------------------------------------------------------

# Preserve line numbers for meaningful crash stack traces
-keepattributes SourceFile,LineNumberTable

# Open-source — no need to obfuscate
-dontobfuscate

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

# ---- Compose Multiplatform --------------------------------------------------

# Keep resource library internals and generated Res accessor classes so R8 does
# not tree-shake the resource loading infrastructure. Without these rules the
# fdroid flavor crashes at startup with a misleading URLDecodeException due to
# R8 exception-class merging.
-keep class org.jetbrains.compose.resources.** { *; }
-keep class org.meshtastic.core.resources.** { *; }

# Compose Animation: prevent R8 from merging animation spec classes (easing
# curves, transition specs, Animatable internals) which can cause animations to
# silently snap in release builds.
#
# We use a full -keep here without allowshrinking/allowobfuscation. While it
# might keep some unused transition APIs, R8's aggressive shrinking is known
# to incorrectly remove internal states or merging empty transitions (like None)
# causing AnimatedVisibility and others to snap.
-keep class androidx.compose.animation.** { *; }
