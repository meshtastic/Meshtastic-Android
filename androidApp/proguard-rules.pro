# ============================================================================
# Meshtastic Android — ProGuard / R8 rules for release minification
# ============================================================================
# Open-source project: obfuscation and optimization are disabled. We rely on
# tree-shaking (unused code removal) for APK size reduction.
#
# Cross-platform library rules (Koin, kotlinx-serialization, Wire, Room,
# Ktor, Coil, Kable, Kermit, Okio, DataStore, Paging, Lifecycle, Navigation 3,
# AboutLibraries, Markdown, QRCode, CMP resources, core model) live in
# config/proguard/shared-rules.pro and are wired in by the
# AndroidApplicationConventionPlugin. This file holds only Android-specific
# rules and R8-only directives.
# ============================================================================

# ---- General ----------------------------------------------------------------

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

# ---- Networking (transitive references from Ktor on Android) ----------------

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- BLE (Kable) -------------------------------------------------------------
# Kable annotates ScanResultAndroidAdvertisement with @Parcelize but doesn't
# ship the parcelize runtime in its POM; the app doesn't apply the parcelize
# plugin (no @Parcelize usage of our own), so the annotation class is absent.
# Annotation classes aren't needed at runtime — Kable's generated CREATOR
# works without it.
-dontwarn kotlinx.parcelize.Parcelize

# ---- AppSearch / AppFunctions generated document factories ------------------
# AppSearch's DocumentClassFactoryRegistry loads the generated
# `$$__AppSearch__*` factory classes by name and instantiates them via their
# no-arg constructor (reflection). AppSearch's own consumer rule keeps the
# factory *class* (`-keep ... class ** implements DocumentClassFactory {}`) but
# not its members. Under R8 full mode (AGP 8+ default, and we're on AGP 9) a
# bare `-keep class X {}` no longer implicitly retains the default constructor,
# so the constructor is tree-shaken and runtime construction fails with
# NoSuchMethodException — e.g.
# androidx.appfunctions.metadata.$$__AppSearch__AppFunctionRuntimeMetadata.<init>[]
# crashing AppFunctionManager.getInstance(). Upstream tracking: b/440484133.
# Keep the no-arg constructor explicitly until AppSearch's rules cover it.
-keepclassmembers class * implements androidx.appsearch.app.DocumentClassFactory {
    <init>();
}

# Compose runtime/ui/animation/foundation/material3 keep rules now live in
# config/proguard/shared-rules.pro so both Android (R8) and desktop (ProGuard)
# get the same defence-in-depth coverage against CMP 1.11 optimizer folding.
