# Car App Library ProGuard/R8 rules

# CarAppService must not be obfuscated (resolved by android:exported="true" in manifest,
# but keep rule ensures R8 doesn't remove it during aggressive shrinking)
-keep class org.meshtastic.feature.car.service.MeshtasticCarAppService { *; }

# Keep Koin-annotated classes for runtime DI resolution
-keep @org.koin.core.annotation.Single class * { *; }
-keep @org.koin.core.annotation.Factory class * { *; }
