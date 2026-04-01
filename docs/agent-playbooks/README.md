# Agent Playbooks

These playbooks are execution-focused guidance for common changes in this repository.

Use `AGENTS.md` as the source of truth for architecture boundaries and required conventions. If guidance conflicts, follow `AGENTS.md` and current code patterns.

## Version baseline for external docs

When checking upstream docs/examples, match these repository-pinned versions from `gradle/libs.versions.toml`:

- Kotlin: `2.3.20`
- Koin: `4.2.0` (`koin-annotations` `4.2.0` — uses same version as `koin-core`; compiler plugin `0.4.1`)
- JetBrains Navigation 3: `1.1.0-beta01` (`org.jetbrains.androidx.navigation3`)
- JetBrains Lifecycle (multiplatform): `2.11.0-alpha02` (`org.jetbrains.androidx.lifecycle`)
- AndroidX Lifecycle (Android-only): `2.10.0` (`androidx.lifecycle`)
- Kotlin Coroutines: `1.10.2`
- Compose Multiplatform: `1.11.0-beta01`
- JetBrains Material 3 Adaptive: `1.3.0-alpha06` (`org.jetbrains.compose.material3.adaptive`)

Prefer versioned docs pages that match those versions (for example, Koin `4.2` docs rather than older `4.0/4.1` pages).

## Dependency alias quick-reference

Version catalog aliases split cleanly by fork provenance. **Use the right prefix for the right source set.**

| Alias prefix | Coordinates | Use in |
|---|---|---|
| `jetbrains-lifecycle-*` | `org.jetbrains.androidx.lifecycle:*` | `commonMain`, `androidMain` |
| `jetbrains-navigation3-ui` | `org.jetbrains.androidx.navigation3:navigation3-ui` | `commonMain`, `androidMain` |
| `jetbrains-navigationevent-*` | `org.jetbrains.androidx.navigationevent:*` | `commonMain`, `androidMain` |
| `jetbrains-compose-material3-adaptive-*` | `org.jetbrains.compose.material3.adaptive:*` | `commonMain`, `androidMain` |
| `androidx-lifecycle-process` | `androidx.lifecycle:lifecycle-process` | `androidMain` only — `ProcessLifecycleOwner` |
| `androidx-lifecycle-testing` | `androidx.lifecycle:lifecycle-runtime-testing` | `androidUnitTest` only |

> **Note:** JetBrains does not publish a separate `navigation3-runtime` artifact — `navigation3-ui` is the only artifact. The version catalog only defines `jetbrains-navigation3-ui`. The `lifecycle-runtime-ktx` and `lifecycle-viewmodel-ktx` KTX aliases were removed (extensions merged into base artifacts since Lifecycle 2.8.0).

Quick references:

- Koin annotations (4.2 docs): `https://insert-koin.io/docs/reference/koin-annotations/start`
- Koin KMP docs: `https://insert-koin.io/docs/reference/koin-annotations/kmp`
- AndroidX Navigation 3 release notes: `https://developer.android.com/jetpack/androidx/releases/navigation3`
- Kotlin release notes: `https://kotlinlang.org/docs/releases.html`

## Playbooks

- `docs/agent-playbooks/di-navigation3-anti-patterns-playbook.md` - DI and Navigation 3 mistakes to avoid.
- `docs/agent-playbooks/kmp-source-set-bridging-playbook.md` - when to use `expect`/`actual` vs interfaces + app wiring.
- `docs/agent-playbooks/task-playbooks.md` - step-by-step recipes for common implementation tasks, plus code anchor quick reference.
- `docs/agent-playbooks/testing-and-ci-playbook.md` - which Gradle tasks to run based on change type, plus CI parity.



