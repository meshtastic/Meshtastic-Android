# ============================================================================
# Meshtastic Desktop — ProGuard rules for release minification
# ============================================================================
# Open-source project: we rely on tree-shaking (unused code removal) for size
# reduction. Obfuscation is disabled in build.gradle.kts (obfuscate.set(false)).
#
# Cross-platform library rules (Koin, kotlinx-serialization, Wire, Room,
# Ktor, Coil, Kable, Kermit, Okio, DataStore, Paging, Lifecycle, Navigation 3,
# AboutLibraries, Markdown, QRCode, CMP resources, core model) live in
# config/proguard/shared-rules.pro and are wired in by this module's
# build.gradle.kts. This file holds only desktop/JVM-specific rules.
# ============================================================================

# ---- General ----------------------------------------------------------------

# Suppress notes about duplicate resource files (common in fat JARs)
-dontnote **

# Disable ProGuard optimization passes. Tree-shaking (unused code removal) still
# runs — only method-body rewrites and call-site transformations are suppressed.
#
# Why: CMP 1.11 ships consumer rules with -assumenosideeffects on
# Composer.<clinit>() and ComposerImpl.<clinit>(), plus -assumevalues on
# ComposeRuntimeFlags and ComposeStackTraceMode. These optimization directives
# let the optimizer rewrite *call sites* (class-init triggers, flag reads) even
# when the target classes are preserved by -keep rules. The result is that the
# Compose recomposer/frame-clock/animation state machines silently freeze on
# their first frame in release builds. -dontoptimize is the only directive that
# disables processing of -assumenosideeffects/-assumevalues. The desktop compose
# build sets optimize.set(true), so this applies here as well as to R8. See #5146.
-dontoptimize

# Do not parse/rewrite Kotlin metadata during shrinking/optimization.
# ProGuard's KotlinShrinker cannot handle the metadata produced by Compose
# Multiplatform 1.11.x + Kotlin 2.3.x, causing a NullPointerException.
# Since we disable obfuscation (class names remain stable), metadata references
# stay valid and do not need rewriting. The annotations themselves are preserved
# by -keepattributes *Annotation*.
#
# NOTE: -dontprocesskotlinmetadata is a ProGuard-only directive; R8 does not
# recognize it, which is why it lives in the desktop-only file.
-dontprocesskotlinmetadata

# ---- Entry point ------------------------------------------------------------

-keep class org.meshtastic.desktop.MainKt { *; }

# ---- Ktor Java engine (desktop-only; Android uses OkHttp) -------------------
# io.ktor.client.engine.java ships consumer rules; the shared
# HttpClientEngineFactory ServiceLoader keep in shared-rules.pro covers the
# reflective discovery path.

# ---- Meshtastic desktop host shell ------------------------------------------

# Keep all desktop module classes (thin host shell — not worth tree-shaking)
-keep class org.meshtastic.desktop.** { *; }

# ---- JVM runtime suppression ------------------------------------------------

-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# ---- jSerialComm (cross-platform serial library with Android stubs) ---------

-dontwarn com.fazecast.jSerialComm.android.**

# ---- Kotlin stdlib atomics (Kotlin 2.3+ intrinsics, not on JDK 17) ----------

-dontwarn kotlin.concurrent.atomics.**
-dontwarn kotlin.uuid.UuidV7Generator
