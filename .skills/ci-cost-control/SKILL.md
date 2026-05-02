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
- You must run `./gradlew spotlessApply spotlessCheck detekt test allTests` locally before pushing.
- If local tests fail, CI **will** fail. Do not waste the tokens or the compute.

### 3. Modular CI Invocations
When using the `/delegate` or autonomous PR tools, explicitly limit the scope of the CI check if the tool supports it. Avoid running the full multi-OS desktop matrix for a simple documentation fix.

## Monitoring
Use `gh run view <run_id>` to inspect failures. Do not re-run a whole suite if only one shard failed due to a known flake; use `gh run rerun --failed`.
