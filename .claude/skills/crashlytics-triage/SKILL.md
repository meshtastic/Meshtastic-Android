---
name: crashlytics-triage
description: Triage current Meshtastic-Android crashes in Firebase Crashlytics — establish the right version filter (topVersions first, then topIssues filtered to "X.Y.Z (versionCode)"), fan out one crash-investigator subagent per top issue in parallel, and return a distilled verdict table. Use for "what's crashing", "triage Crashlytics", or "is build NNNN healthy" sweeps; for a single known issue id, dispatch crash-investigator directly instead.
disable-model-invocation: true
---

# crashlytics-triage

Crashlytics sweep for the **meshutil** Firebase project (`484268767777`), prod app id `1:484268767777:android:70d9bffeca6efe05334160`. Datadog RUM is the *other* backend (high-volume logged errors — use `datadog-rum-investigator` there); Crashlytics is the low-volume real-crash signal. If Firebase MCP auth fails, the account is james.a.rich@gmail.com.

## 1. Version context first — never guess the filter string
Call `crashlytics_get_report` for **topVersions with NO filter**. This yields the exact display names — the version filter format is `"X.Y.Z (versionCode)"` and hand-built strings silently match nothing. Pick the target version(s): the argument if given, else the newest prod version with meaningful session volume.

## 2. Top issues for that version
`crashlytics_get_report` topIssues filtered to the exact display name from step 1. Take the top ~5 (or the requested count) by event count. Note event counts and affected-user counts.

## 3. Fan out — one crash-investigator per issue, in parallel
Dispatch the `crash-investigator` agent for each issue **in a single message** so they run concurrently. Give each: the issue id, the version display name, and the ask (root-cause hypothesis + fix area + whether it's already fixed/known). Known residuals to cross-check before calling anything "new": cluster-renderer lifecycle (fixed ≥29321034), MQTT/TLS ktor write (fixed, old 2.7.14 users), LazyColumn dup-key (two prior instances).

## 4. Verdict
One table: issue → crash count/users → root-cause hypothesis → status (NEW / known-fixed-residual / regression) → fix area. Flag anything that warrants a hotfix vs. next-release. No raw stack traces in the summary.
