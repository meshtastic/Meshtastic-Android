# ============================================================================
# Meshtastic Android — ProGuard / R8 rules for release minification
# ============================================================================
# Release builds are shrunk and optimized. The google flavor is also
# obfuscated; its mapping goes to Crashlytics and Datadog and is attached to
# each GitHub release. The fdroid flavor adds proguard-rules-fdroid.pro, which
# keeps it unobfuscated.
#
# Cross-platform library rules (Koin, kotlinx-serialization, Wire, Room,
# Ktor, Coil, Kable, Kermit, Okio, DataStore, Paging, Lifecycle, Navigation 3,
# AboutLibraries, Markdown, QRCode, CMP resources, core model) live in
# config/proguard/shared-rules.pro and are wired in by the
# AndroidApplicationConventionPlugin. This file holds only Android-specific
# rules and R8-only directives.
# ============================================================================

# ---- General ----------------------------------------------------------------

# R8 optimization is ENABLED: tree-shaking plus the full optimization pass
# (method inlining, class merging, Composer/ComposerImpl devirtualization,
# unused-argument removal) all run.
#
# History: optimization used to be disabled here via -dontoptimize (#5146).
# androidx.compose's consumer rules ship -assumenosideeffects on
# Composer/ComposerImpl/ComposerKt.sourceInformation* and -assumevalues on
# ComposeRuntimeFlags/ComposeStackTraceMode; -dontoptimize is the only directive
# that stops R8 from acting on those, and on an older R8×CMP combination acting
# on them froze the Compose recomposer/frame-clock on the first release frame.
# Re-verified on AGP 9.3 / CMP 1.11.1 (2026-07) by driving a minified release
# APK on-device: first-frame render, Navigation-3 transitions, animated tab
# switching and recomposition all work, so -dontoptimize was removed. The R8
# config analyzer confirms OPTIMIZATION coverage jumps from 0% to ~96%.
# If Compose animations/recomposition ever freeze in a release build after a
# CMP/AGP/R8 bump, re-add `-dontoptimize` and re-open #5146.

# Dump the full merged R8 configuration (app rules + all library consumer rules)
# for auditing. Inspect this file after a release build to see what libraries inject.
-printconfiguration build/outputs/mapping/r8-merged-config.txt

# ---- Names read at runtime --------------------------------------------------
# Each name below is looked up by string, so obfuscation must leave it alone.

# KableGattCacheRefresh reads these private Kable fields by reflection.
-keepclassmembernames class com.juul.kable.BluetoothDeviceAndroidPeripheral {
    kotlinx.coroutines.flow.MutableStateFlow connection;
}
-keepclassmembernames class com.juul.kable.Connection {
    android.bluetooth.BluetoothGatt gatt;
}

# rumViewName() reports a route's class name as its Datadog view name.
-keepnames class * implements androidx.navigation3.runtime.NavKey

# isDeprecatedEnumEntry() finds each constant's field by name to read @Deprecated.
-keepclassmembernames enum org.meshtastic.** {
    <fields>;
}

# GooglePlatformAnalytics drops logging frames from Crashlytics stacks by class-name prefix.
-keepnames class org.meshtastic.app.analytics.GooglePlatformAnalytics*
-keepnames class co.touchlab.kermit.**

# ---- Networking (transitive references from Ktor on Android) ----------------

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

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
