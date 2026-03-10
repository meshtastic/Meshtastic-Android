# Agent Playbooks

These playbooks are execution-focused guidance for common changes in this repository.

Use `AGENTS.md` as the source of truth for architecture boundaries and required conventions. If guidance conflicts, follow `AGENTS.md` and current code patterns.

## Version baseline for external docs

When checking upstream docs/examples, match these repository-pinned versions from `gradle/libs.versions.toml`:

- Kotlin: `2.3.10`
- Koin: `4.2.0-RC1` (`koin-annotations` `2.1.0`, compiler plugin `0.3.0`)
- AndroidX Navigation 3: `1.0.1`
- Kotlin Coroutines: `1.10.2`
- Compose Multiplatform: `1.11.0-alpha03`

Prefer versioned docs pages that match those versions (for example, Koin `4.2` docs rather than older `4.0/4.1` pages).

Quick references:

- Koin annotations (4.2 docs): `https://insert-koin.io/docs/reference/koin-annotations/start`
- Koin KMP docs: `https://insert-koin.io/docs/reference/koin-annotations/kmp`
- AndroidX Navigation 3 release notes: `https://developer.android.com/jetpack/androidx/releases/navigation3`
- Kotlin release notes: `https://kotlinlang.org/docs/releases.html`

## Playbooks

- `docs/agent-playbooks/common-practices.md` - architecture and coding patterns to mirror.
- `docs/agent-playbooks/di-navigation3-anti-patterns-playbook.md` - DI and Navigation 3 mistakes to avoid.
- `docs/agent-playbooks/kmp-source-set-bridging-playbook.md` - when to use `expect`/`actual` vs interfaces + app wiring.
- `docs/agent-playbooks/task-playbooks.md` - step-by-step recipes for common implementation tasks.
- `docs/agent-playbooks/testing-and-ci-playbook.md` - which Gradle tasks to run based on change type, plus CI parity.





