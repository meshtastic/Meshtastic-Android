# Build-Logic Documentation Index

Quick navigation guide for build-logic conventions in this repository.

## Start Here

- New to build-logic? -> `docs/BUILD_LOGIC_CONVENTIONS_GUIDE.md`
- Need test-dependency specifics? -> `docs/BUILD_CONVENTION_TEST_DEPS.md`
- Need implementation code? -> `build-logic/convention/src/main/kotlin/`

## Primary Docs (Current)

| Document | Purpose |
| :--- | :--- |
| `docs/BUILD_LOGIC_CONVENTIONS_GUIDE.md` | Canonical conventions, duplication heuristics, verification commands, common pitfalls |
| `docs/BUILD_CONVENTION_TEST_DEPS.md` | Rationale and behavior for centralized KMP test dependencies |

## Key Conventions to Follow

- Prefer lazy Gradle APIs in convention plugins: `configureEach`, `withPlugin`, provider APIs.
- Avoid `afterEvaluate` in `build-logic/convention` unless there is no viable lazy alternative.
- Keep convention plugins single-purpose and compose them (e.g., `meshtastic.kmp.feature` composes KMP + Compose + Koin conventions).
- Use version-catalog aliases from `gradle/libs.versions.toml` consistently.

## Verification Commands

```bash
./gradlew :build-logic:convention:compileKotlin
./gradlew :build-logic:convention:validatePlugins
./gradlew spotlessCheck
./gradlew detekt
```

## Related Files

- `build-logic/convention/build.gradle.kts`
- `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt`
- `build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/FlavorResolution.kt`
- `AGENTS.md`
- `.github/copilot-instructions.md`
- `GEMINI.md`
