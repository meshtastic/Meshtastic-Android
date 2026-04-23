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
# (org.meshtastic.desktop.MainKt is covered by the package-wide keep below.)

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

# ---- Library consumer rules ------------------------------------------------
# The compose-jb gradle plugin auto-injects `default-compose-desktop-rules.pro`
# (bundled inside org.jetbrains.compose:compose-gradle-plugin) into every
# desktop ProGuard run. That file already covers:
#   - kotlin.**, kotlinx.coroutines.** (incl. SwingDispatcherFactory ServiceLoader)
#   - org.jetbrains.skiko.**, org.jetbrains.skia.**
#   - kotlinx.serialization.** (incl. @Serializable companion keeps)
#   - kotlinx.datetime.**
#   - androidx.compose.runtime SnapshotStateKt + Material3 SliderDefaults
# So we DO NOT re-declare those here. Source of truth:
#   https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/resources/default-compose-desktop-rules.pro
#
# However, the standalone ProGuard 7.7.0 that compose-jb invokes does NOT
# auto-import library `META-INF/proguard/*.pro` consumer rules from arbitrary
# jars (only R8/Android does). So any consumer-rule pattern outside the bundled
# defaults above must be copied here manually (see Ktor SL block below).

# ---- androidx.sqlite bundled driver (JNI native bridge) ---------------------
# BundledSQLiteDriver loads `libsqliteJni` and the native code calls back into
# JVM-land via methods on `BundledSQLiteDriverKt` (e.g. `nativeThreadSafeMode`)
# and member methods on `BundledSQLiteDriver` itself. Because those JVM symbols
# are referenced only from native code, ProGuard removes them as unused; the
# native loader then crashes with `NoSuchMethodError: ... name or signature does
# not match`. Keep the whole driver package — it's small and entirely needed at
# runtime once the bundled SQLite driver is selected.
-keep class androidx.sqlite.driver.bundled.** { *; }
-keepclassmembers class androidx.sqlite.driver.bundled.** { native <methods>; *; }

# ---- Ktor serialization extension providers (ServiceLoader) -----------------
# io.ktor.serialization.kotlinx-json discovers KotlinxSerializationJsonExtensionProvider
# via META-INF/services/io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider.
# Without this keep the desktop HttpClient init throws ServiceConfigurationError
# at first request; on Windows jpackage's launcher swallows the trace and
# surfaces it as "Failed to launch JVM".
-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider { *; }

# ---- Vico 3.2.0-next.1 ColorScale (CMP API drift) ---------------------------
# Vico's new ColorScale* classes (ColorScaleShader, ColorScaleAreaFill,
# ColorScaleLineFill) reference CMP UI graphics members that don't exist in
# compose-multiplatform 1.11.0-beta03 (LinearGradientShader-VjE6UOU$default
# on ShaderKt and Paint.setShader(org.jetbrains.skia.Shader)). We don't use
# the ColorScale APIs in this app, so suppress these warnings to let ProGuard
# proceed; otherwise it aborts with "unresolved program class members".
# Remove once Vico ships a release built against CMP 1.11 stable.
-dontwarn com.patrykandpatrick.vico.compose.cartesian.ColorScaleShader
-dontwarn com.patrykandpatrick.vico.compose.cartesian.layer.ColorScaleAreaFill
-dontwarn com.patrykandpatrick.vico.compose.cartesian.layer.ColorScaleLineFill
