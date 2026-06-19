---
name: proto-bump
description: Change the org.meshtastic:protobufs pin for Meshtastic-Android — either bump to a tagged release (mergeable) or track develop-SNAPSHOT for a preview (draft), adding/removing the transitive resolution-force hack as appropriate. Verifies with test/allTests (not just compile) via the gradle-runner subagent, audits the new field surface, and opens a PR. Use to consume new proto changes or to re-pin a SNAPSHOT draft onto a tag.
disable-model-invocation: true
---

# proto-bump

Changes the upstream Meshtastic protobufs Maven pin. Protobuf models are **not** generated in this repo — they come from `org.meshtastic:protobufs` (Square Wire–built KMP models), pinned in `gradle/libs.versions.toml`. A bump is a catalog edit + verification, never a hand-edit of generated proto.

> **Renovate already watches the catalog** for new *tagged* `org.meshtastic:protobufs` releases and opens the bump PR for you. Use this skill to **drive/finish** such a bump (verify, audit, adapt call sites), or — the part Renovate can't do — to track an unreleased `develop-SNAPSHOT`.

## Two modes

| | Mode A — tagged release | Mode B — develop-SNAPSHOT |
|---|---|---|
| Version | `X.Y.Z` | `develop-SNAPSHOT` |
| Transitive force block | **absent** (remove if present) | **present** (add it) |
| PR state | mergeable | **draft** (un-block when a tag ships) |
| When | a release carries what you need | the change only exists on protobufs `develop` (precedent: #5790, #5834, lockdown re-port) |

Pick the mode from the argument / context. If the change you need isn't in any tag yet, it's Mode B.

## Mode A — bump to a tagged release (mergeable)

1. Read the current pin: `meshtastic-protobufs = "<…>"` in `gradle/libs.versions.toml`. Confirm the target is a real tag at https://github.com/meshtastic/protobufs/releases (or Maven Central), not a SNAPSHOT.
2. Set `meshtastic-protobufs = "X.Y.Z"`.
3. **Re-pin cleanup:** if the transitive force block (see below) is present in the root `build.gradle.kts`, **remove it** — a tagged protobufs is ordered correctly against transitive pins, so the force is unnecessary and misleading once on a release. Also re-check: is takpacket/mqtt now republished against this protobufs? If still pinning an older one, you may need to keep the force (note it in the PR).
4. **Verify** (step "Verification" below) — including `test`/`allTests`.
5. **Audit** the new additive surface (below).
6. Open a normal (non-draft) PR.

## Mode B — track develop-SNAPSHOT (draft only)

1. Set `meshtastic-protobufs = "develop-SNAPSHOT"`.
2. **Add the transitive force block** to the bottom of the root `build.gradle.kts` (exact text below). No repository change is needed — `settings.gradle.kts` already declares the Sonatype maven-snapshots repo (`snapshotsOnly()`) and JitPack (`https://jitpack.io`), which is where `develop-SNAPSHOT` resolves from.
3. **Verify** — including `test`/`allTests` (this is the mode where skipping them bites; see below).
4. **Audit** the new additive surface.
5. Open the PR **as a draft**, stating the un-block condition: *"merge once protobufs `vX.Y.Z` is tagged; switch to Mode A (set the tag + remove the force block) first."*

## The transitive force block (why it exists, exact code)

`takpacket-sdk` (and the MQTT client) transitively pin a **tagged** `protobufs` (e.g. `2.7.25`). Gradle ranks `2.7.25` **above** `develop-SNAPSHOT` (a numeric component outranks the `develop` string qualifier), so the *test runtime* classpath silently downgrades to the tagged proto while the *common-metadata compile* uses the snapshot. The mismatch throws `NoSuchFieldError`/`NoSuchMethodError` on proto-generated classes **at test runtime** — and crucially `assembleDebug`/`detekt` do **not** catch it; only `test`/`allTests` do. The block forces every `org.meshtastic:protobufs*` variant to the snapshot so compile and runtime agree:

```kotlin
// ─── TEMPORARY: protobufs develop-SNAPSHOT preview (PR #NNNN) ─────────────────────────────────────
// We track the unreleased protobufs develop-SNAPSHOT. takpacket-sdk-jvm transitively pins a tagged
// protobufs, and Gradle ranks the tag > develop-SNAPSHOT (a numeric part outranks the "develop"
// string qualifier). That downgrades the test *runtime* classpath to the tag while the common-metadata
// *compile* uses the snapshot, yielding NoSuchFieldError/NoSuchMethodError on the proto-generated
// classes at test runtime (assembleDebug/detekt don't catch it; test/allTests do). Force every
// protobufs* variant to the snapshot so compile and runtime agree. Safe while atak.proto is unchanged,
// so takpacket's own message ABI stays compatible with the newer protobufs.
// REMOVE once protobufs is tagged (Mode A) / takpacket + mqtt are republished against it.
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.meshtastic" && requested.name.startsWith("protobufs")) {
                useVersion("develop-SNAPSHOT")
                because("preview #NNNN: override takpacket transitive protobufs pin")
            }
        }
    }
}
```

Update `#NNNN` to the current PR. Remove this entire block when returning to a tagged release (Mode A step 3).

## Verification (don't skip test/allTests)

Dispatch the **gradle-runner** subagent (keep the heavy log out of context). The downgrade failure mode above is a *runtime* classpath problem invisible to compilation, so verification MUST exercise tests:

- `kmpSmokeCompile` + a broad compile of proto-consuming modules (`core:*`, `feature:*`) — catches *compile-time* breaking changes (renamed/removed fields, changed oneof shapes).
- **`test` and `allTests`** — the only gate that catches the transitive runtime downgrade. Treat a green compile as necessary-but-not-sufficient.

Triage each compile/test failure: adapt this repo's call sites, or, if a break is unexpected, stop and report rather than papering over it.

## Audit the new additive surface (recommended)

New proto versions usually add fields/messages the app doesn't consume yet. Diff the new surface against current usage and list what became implementable. **Caveat from prior audits: verify each reference directly** — automated gap-lists for this repo have been wrong as often as right. Treat the list as candidates, not facts; don't implement them in this PR unless asked.

## PR + guardrails

- Write the PR per `.github/copilot-pull-request-instructions.md`: WHY-first; 🛠️ (or 🌟 if it unlocks a user-facing feature); link the upstream release notes (real URL only); "Testing Performed" = the gradle-runner run including `allTests`.
- Never hand-edit or vendor generated proto — this repo consumes the Maven artifact only.
- Keep the change minimal: a bump PR is a catalog edit + the force block (Mode B) + necessary call-site adaptations, not a feature.
- Branch off `main` (the 2.8.0 line) unless told otherwise.
- After a successful change, update the relevant memory pointer (protobufs-sdk-alignment / lora-region-preset-map) so the next session knows the new baseline.
