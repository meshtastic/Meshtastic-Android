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
- Cancel redundant runs if your new push supersedes them: `gh run cancel <run_id>`.

### 2. Local First
NEVER use CI as a "remote compiler." 
- You must run `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` locally before pushing.
- If local tests fail, CI **will** fail. Do not waste the tokens or the compute.

### 3. Let the path filters do their job
`pull-request.yml`'s `check-changes` filter already skips the heavy pipeline for docs-only
changes, and `merge-queue.yml` skips it for docs-only merge-group entries (`docs/**`, `*.md`).
Don't defeat that by bundling an unrelated source edit into a docs PR — a one-line README fix
should not be paying for the multi-OS desktop matrix. If you add a new top-level module, add its
`<root>/**` line to the `android:` filter, or the drift guard fails the PR.

## Monitoring
Use `gh run view <run_id>` to inspect failures. Do not re-run a whole suite if only one shard failed due to a known flake; use `gh run rerun --failed`.
