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
# -keep prevents class merging (EnterTransition/ExitTransition into *Impl,
#   VectorizedSpringSpec/TweenSpec elimination, etc.).
# allowshrinking lets R8 remove genuinely unreachable classes (e.g.
#   SharedTransition APIs, RepeatableSpec — unused by this app). Verified via
#   dex analysis: 278 classes survive in release vs 139 without this rule;
#   all actively used classes (AnimatedVisibility, Crossfade, SpringSpec,
#   TweenSpec, EnterTransition, ExitTransition, etc.) are preserved.
# allowobfuscation is moot (-dontobfuscate is set above) but explicit for
#   clarity.
# The ** wildcard is recursive and covers animation.core.* sub-packages.
-keep,allowshrinking,allowobfuscation class androidx.compose.animation.** { *; }
