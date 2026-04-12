---
applyTo: "build-logic/**/*.kt"
---

# Build-Logic Convention Plugin Rules

- Prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs).
- Avoid `afterEvaluate` unless there is no viable lazy alternative.
- Check `gradle/libs.versions.toml` for version catalog aliases before adding new ones.
- Convention plugins: `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.kmp.jvm.android`, `meshtastic.koin`.
