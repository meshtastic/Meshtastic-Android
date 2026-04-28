# ============================================================================
# Meshtastic Desktop — ProGuard rules for release minification
# ============================================================================
# Open-source: obfuscation is OFF (build.gradle.kts: obfuscate.set(false)).
# Tree-shaking still runs.
#
# Two rule sources are merged into the ProGuard run:
#   1. JetBrains' bundled `default-compose-desktop-rules.pro` (auto-injected
#      by the compose.desktop Gradle plugin).
#   2. Cross-platform project keeps in config/proguard/shared-rules.pro,
#      which inlines every dependency consumer rule we need on desktop —
#      compose-jb's standalone ProGuard task does NOT auto-discover
#      `META-INF/proguard/*.pro` consumer rules from dependency jars (only
#      R8 on Android does — https://github.com/Guardsquare/proguard/issues/423).
#
# This file only holds desktop/JVM-specific rules that aren't covered above.
# ============================================================================

# ---- ProGuard 7.7 + Kotlin 2.3 metadata workaround --------------------------
# ProGuard 7.7's KotlinShrinker NPEs on metadata produced by CMP 1.11 +
# Kotlin 2.3.x. Because we don't obfuscate, class names stay stable and
# metadata references remain valid without rewriting. Annotations themselves
# are preserved by `-keepattributes *Annotation*` in shared-rules.pro.
# (R8-only directive equivalent does not exist; this is ProGuard-only.)
-dontprocesskotlinmetadata

# ---- Disable optimizer (CMP 1.11 -assumenosideeffects defense) --------------
# See shared-rules.pro for full rationale. Even though build.gradle.kts sets
# `optimize.set(true)` so compose-jb wires the optimization step, this rule
# turns it into a no-op — keeping CMP's `-assumenosideeffects` directives from
# rewriting Composer call sites and freezing the runtime. See #5146.
-dontoptimize

# ---- Entry point ------------------------------------------------------------
# Keep the desktop host shell (thin module — not worth tree-shaking).
-keep class org.meshtastic.desktop.** { *; }

# ---- JVM runtime suppression ------------------------------------------------
-dontwarn java.lang.reflect.**
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.invoke.**

# ---- JNA (Java Native Access) — used by LinuxNotificationSender for libnotify ---
# JNA uses reflection to bind native methods; keep its core and callback classes.
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.ptr.** { *; }
-dontwarn com.sun.jna.**

# ---- jSerialComm Android stubs (cross-platform serial library) --------------
# jSerialComm bundles Android shims that reference android.* classes; harmless
# on JVM/desktop but ProGuard fails the build on unresolved program classes
# unless suppressed.
-dontwarn com.fazecast.jSerialComm.android.**

# Wire ships AndroidMessage in its common runtime; on desktop classpath there is
# no android.os.Parcelable. We never use AndroidMessage on desktop.
-dontwarn com.squareup.wire.AndroidMessage
-dontwarn com.squareup.wire.AndroidMessage$*
-dontwarn android.os.Parcelable
-dontwarn android.os.Parcelable$*

# Vico's ColorScale* classes call into skia-shader bridges that aren't on the
# desktop ProGuard classpath. Vico ships no consumer rules.
-dontwarn com.patrykandpatrick.vico.compose.cartesian.ColorScaleShader
-dontwarn com.patrykandpatrick.vico.compose.cartesian.layer.ColorScaleAreaFill
-dontwarn com.patrykandpatrick.vico.compose.cartesian.layer.ColorScaleLineFill

# ---- Kotlin 2.3+ stdlib intrinsics not present on JDK 17 --------------------
-dontwarn kotlin.concurrent.atomics.**
-dontwarn kotlin.uuid.UuidV7Generator
