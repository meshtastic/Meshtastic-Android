---
name: proto-bump
description: Bump the org.meshtastic:protobufs dependency to a target version for Meshtastic-Android — edit the version catalog, conditionally drop any develop-SNAPSHOT force/repo, verify with kmpSmokeCompile via the gradle-runner subagent, audit the new field surface, and open a PR. Use when a new protobufs release is available and the app should consume it, or to re-pin a draft off develop-SNAPSHOT onto a tagged release.
disable-model-invocation: true
---

# proto-bump

Bumps the upstream Meshtastic protobufs Maven artifact. Protobuf models are **not** generated in this repo — they come from `org.meshtastic:protobufs` (Square Wire–built KMP models), pinned in `gradle/libs.versions.toml`. A bump is a catalog edit + verification, never a hand-edit of generated proto.

## The one rule that gates everything: tagged vs SNAPSHOT

- A **mergeable** PR must consume a **tagged release** (`vX.Y.Z` on Maven Central). Only tagged releases are stable.
- `develop-SNAPSHOT` is the upstream `develop` branch — moving, unstable. If the change you need (e.g. a new field) only exists on `develop`, you may pin `develop-SNAPSHOT` **but keep the PR a draft** and do not merge until a tagged release carries it. This is the recurring blocker in prior work (LoRa region→preset map, the lockdown re-port).
- "Re-pin before merge" = the inverse: when a draft pinned to SNAPSHOT becomes unblockable because a tag shipped, swap the version to the tag and remove any SNAPSHOT scaffolding (see step 4).

## Steps

1. **Read current state.**
   - Current pin: `meshtastic-protobufs = "<X.Y.Z>"` in `gradle/libs.versions.toml`.
   - Target version: from the skill argument, else find the latest tag at https://github.com/meshtastic/protobufs/releases (or Maven Central `org.meshtastic:protobufs`). Confirm it is a real tagged release, not a SNAPSHOT, unless you are deliberately on the SNAPSHOT path above.

2. **Edit the catalog.** Update `meshtastic-protobufs` in `gradle/libs.versions.toml` to the target version. That is the only required edit in the common case.

3. **Conditionally drop the SNAPSHOT force + repo.** These may or may not be present — check, don't assume (on a clean `main` pin there is nothing to remove):
   - Search the root `build.gradle.kts` and module build files for a `resolutionStrategy`/`force`/dependency-substitution pinning protobufs to `develop-SNAPSHOT`. If present and you're moving to a tagged release, remove it.
   - Check `settings.gradle.kts` (and any `repositories {}`) for a snapshot Maven repo (e.g. a `…/snapshots` URL) added only for protobufs. Remove it **only if** no other dependency still needs it.

4. **Verify the build.** Dispatch the **gradle-runner** subagent (keep the heavy log out of context). At minimum:
   - `:core:proto:...` compile + `kmpSmokeCompile` (the KMP-touch smoke task this repo documents) to confirm the new models resolve and compile across source sets.
   - A broad compile of the modules that consume proto (`core:*`, `feature:*` that import generated types).
   - **Breaking changes show up as compile errors** — Wire-generated API changes (renamed/removed fields, changed oneof shapes) will fail here. Triage each: adapt the call sites in this repo, or, if the break is unexpected, stop and report rather than papering over it.

5. **Audit the new additive surface (recommended).** New proto versions usually add fields/messages the app doesn't consume yet. Diff the new surface against current usage and list what became implementable (e.g. a new telemetry field, a new admin message). **Caveat from prior audits: verify each reference directly** — automated gap-lists for this repo have been wrong as often as right. Treat the list as candidates, not facts. Don't implement them in this PR unless asked; surface them as follow-ups.

6. **Open the PR** per `.github/copilot-pull-request-instructions.md`:
   - WHY-first body; category 🛠️ (or 🌟 if it unlocks a user-facing feature).
   - Note the version delta and link the upstream release notes (real URL only).
   - "Testing Performed" = the gradle-runner verification from step 4.
   - If you're on the SNAPSHOT path, open it as a **draft** and state the un-block condition ("merge once protobufs `vX.Y.Z` is tagged; re-pin off SNAPSHOT first").

## Guardrails
- Never hand-edit generated proto or vendor proto sources — this repo consumes the Maven artifact only.
- Keep the change minimal: a bump PR is a catalog edit + necessary call-site adaptations, not a feature.
- Branch off `main` (the 2.8.0 line) unless told otherwise.
- After a successful real bump, consider updating the relevant memory pointer (e.g. the protobufs-alignment note) so the next session knows the new baseline.
