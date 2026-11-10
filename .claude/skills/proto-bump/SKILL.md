---
name: proto-bump
description: Change the org.meshtastic:protobufs pin for Meshtastic-Android — to a tagged release or to a commit snapshot (X.Y.Z.N-g<sha>-SNAPSHOT) from protobufs master. Verifies with test/allTests (not compile alone) via the gradle-runner subagent, audits the new field surface, and opens a PR. Use to consume new proto changes, or to finish a Renovate protobufs bump that needs call-site work.
disable-model-invocation: true
---

# proto-bump

Changes the upstream Meshtastic protobufs Maven pin. Protobuf models are **not** generated in this repo — they come from `org.meshtastic:protobufs` (Square Wire–built KMP models), pinned as `meshtastic-protobufs` in `gradle/libs.versions.toml`. A bump is a catalog edit + verification, never a hand-edit of generated proto.

> **Renovate already does routine bumps.** It watches for both tagged releases and commit snapshots (`.github/renovate.json` accepts only `X.Y.Z` and `X.Y.Z.N-g<sha>-SNAPSHOT`) and automerges them. Use this skill when a bump needs call-site adaptation, or to pin a specific upstream commit before Renovate picks it up.

## The two version forms

| | Tagged release | Commit snapshot |
|---|---|---|
| Version | `X.Y.Z` | `X.Y.Z.N-g<sha>-SNAPSHOT` (`N` commits after tag `vX.Y.Z`, at `<sha>`) |
| Source | Maven Central | Central's snapshot repo, already declared in `settings.gradle.kts` (`snapshotsOnly()`) |
| Published | on each protobufs release | on every protobufs `master` push that touches a `.proto` or `packages/kmp/` |
| Mergeable | yes | yes; `main` routinely pins one |

A commit snapshot sorts above its base tag and below the next release, so it outranks the tagged `protobufs` that `takpacket-sdk` and other dependencies carry transitively and Gradle resolves the pin without a forced version. Never pin a bare `-SNAPSHOT` or a legacy hyphen-SHA snapshot (`2.7.26-678281c-SNAPSHOT`).

## Steps

1. Read the current pin. Pick the target: a tag from https://github.com/meshtastic/protobufs/releases, or the snapshot for the protobufs commit you need (`git describe --tags --long` on that commit gives `vX.Y.Z-N-g<sha>`, written `X.Y.Z.N-g<sha>-SNAPSHOT`).
2. Set `meshtastic-protobufs = "<version>"`.
3. **Verify** (below), including `test`/`allTests`.
4. **Audit** the new additive surface (below).
5. Open the PR. Leave `values/schema_strings.xml` alone: the `Protobufs Bump` workflow comments on the PR with the upstream PRs, the `.proto` delta and the settings strings that change, and the `scheduled-updates` run the merge triggers on `main` regenerates the file. Run `./gradlew :schema-strings:sync` only when a call site in this PR needs a new `schema_` key.

## Verification (don't skip test/allTests)

Dispatch the **gradle-runner** subagent (keep the heavy log out of context):

- `kmpSmokeCompile` + a broad compile of proto-consuming modules (`core:*`, `feature:*`) — catches *compile-time* breaking changes (renamed/removed fields, changed oneof shapes).
- **`test` and `allTests`**: a dependency that resolves a different `protobufs` on the runtime classpath than the one compiled against fails only here, as `NoSuchFieldError`/`NoSuchMethodError` on the proto classes. `assembleDebug` and `detekt` do not catch it.

Triage each compile/test failure: adapt this repo's call sites, or, if a break is unexpected, stop and report rather than papering over it.

## Audit the new additive surface (recommended)

New proto versions usually add fields/messages the app doesn't consume yet. Diff the new surface against current usage and list what became implementable. Verify each reference directly: automated gap-lists for this repo are often wrong. Treat the list as candidates, not facts; don't implement them in this PR unless asked.

## PR + guardrails

- Write the PR per `CONTRIBUTING.md` → Pull Requests: WHY-first; 🧹 Chores for a routine catalog bump, since that is where `CONTRIBUTING.md` puts dependencies — 🛠️ only when the bump carries call-site adaptations of its own, 🌟 if it unlocks a user-facing feature; link the upstream compare or release notes (real URL only); "Testing Performed" = the gradle-runner run including `allTests`.
- Never hand-edit or vendor generated proto — this repo consumes the Maven artifact only.
- Keep the change minimal: a bump PR is a catalog edit + necessary call-site adaptations, not a feature.
- Branch off `main` unless told otherwise.
