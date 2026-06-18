# Car App Library ProGuard/R8 rules

# The service class itself is kept by android:exported="true" in the manifest, and
# obfuscation is off project-wide. The Car App Library constructs it reflectively, so
# pin only the no-arg <init> (its other members are framework overrides, kept as such).
-keepclassmembers class org.meshtastic.feature.car.service.MeshtasticCarAppService {
    <init>();
}

# Koin @Single/@Factory keeps are provided app-wide by config/proguard/shared-rules.pro
# (applied to the app that consumes this module). Re-add them here only if feature:car is
# ever published/consumed standalone.
