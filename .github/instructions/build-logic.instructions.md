---
applyTo: "build-logic/**/*.kt"
---

# Build-Logic Convention Plugin Rules

- Prefer lazy Gradle configuration (`configureEach`, `withPlugin`, provider APIs).
- Avoid `afterEvaluate` unless there is no viable lazy alternative.
- Check `gradle/libs.versions.toml` for version catalog aliases before adding new ones.
- 24 convention plugin ids are registered in `build-logic/convention/build.gradle.kts` — read that block rather than guessing. The ones module builds apply most often: `meshtastic.kmp.feature`, `meshtastic.kmp.library`, `meshtastic.kmp.library.compose`, `meshtastic.kmp.jvm.android`, `meshtastic.koin`.
