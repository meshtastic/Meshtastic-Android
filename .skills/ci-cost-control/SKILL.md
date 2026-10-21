---
name: ci-cost-control
description: Keep GitHub Actions spend down on Meshtastic-Android - avoid redundant runs, cancel superseded ones, and pick the narrowest CI path for the change. Use this whenever you are about to push repeatedly, re-run a workflow, touch `.github/workflows/`, or someone asks why CI is slow, queued or expensive.
---

# Skill: CI Cost Control & Monitoring

## Description
Guidelines for agents to minimize GitHub Actions compute waste and prevent redundant or failing CI runs.

## Rules

### 1. Check Before You Kick
Before pushing code that triggers a CI workflow, you **MUST** check if a relevant run is already in progress:
```bash
gh run list --branch $(git branch --show-current) --limit 5
```
- If a run is pending/running for your current state, **DO NOT** push again unless you are fixing a specific CI failure.
- Batch fixes into one push. `pull-request.yml` cancels a PR's superseded run on its own (`cancel-in-progress`), and `pr-closed-cleanup.yml` cancels a closed PR's runs, so there is nothing to cancel by hand.

### 2. Local First
NEVER use CI as a "remote compiler." 
- You must run `./gradlew spotlessApply spotlessCheck detekt detektTypeResolved assembleDebug test allTests` locally before pushing.
- If local tests fail, CI **will** fail. Do not waste the tokens or the compute.

### 3. Let the path filters do their job
`pull-request.yml`'s `check-changes` filter already skips the heavy pipeline for docs-only
changes, and `merge-queue.yml` skips it for docs-only merge-group entries (`docs/**`, `fastlane/**`,
`obtainium/**`, `*.md`). Don't defeat that by bundling an unrelated source edit into a docs PR: a
one-line README fix should not pay for the Gradle jobs. The PR's desktop matrix runs only when the
`desktop` filter matches, and screenshot validation only when the `screenshots` filter does. If you
add a new top-level module, add its `<root>/**` line to the `android:` filter, or the drift guard
fails the PR.

## Monitoring
Use `gh run view <run_id>` to inspect failures. Do not re-run a whole suite if only one shard failed due to a known flake; use `gh run rerun <run_id> --failed`.
