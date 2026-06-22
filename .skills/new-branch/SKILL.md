# Skill: New Branch Bootstrap

## Description
Canonical recipe for spinning up a fresh working branch off the latest upstream `main`. Use this skill
whenever the user says things like *"make a new branch off fetched origin/main"*, *"peel off a fresh
branch"*, *"dust off #NNNN"*, or otherwise signals the start of a new unit of work.

This replaces the ad-hoc prose that used to be retyped at the start of every session.

## When to Use
- Starting any new feature, fix, chore, or refactor.
- Rebasing a stale PR onto current `main` (see [Rebase variant](#rebase-variant)).
- Reproducing a CI failure from a clean baseline.

## Preconditions (verify before branching)
1. **Clean worktree.** If `git status --porcelain` is non-empty, ask the user before proceeding.
2. **Upstream remote present.** `git remote -v` must list `upstream` pointing at
   `meshtastic/Meshtastic-Android`. If only `origin` exists on a fork, treat `origin` as upstream.
3. **Secrets bootstrapped.** If `local.properties` is missing, copy `secrets.defaults.properties`
   (required for `google` flavor builds).

## Standard Recipe

```bash
# 1. Fetch latest upstream
git fetch upstream --prune --tags

# 2. Create the branch from upstream/main (never from a local stale main)
git switch -c <branch-name> upstream/main

# 3. Sanity check
git --no-pager log -1 --oneline
```

## Branch Naming
Use conventional-commit style prefixes that match the PR title convention in
`.github/copilot-pull-request-instructions.md`:

| Prefix | Use for |
| :--- | :--- |
| `feat/<scope>` | New user-visible behavior |
| `fix/<scope>` | Bug fixes |
| `refactor/<scope>` | Code structure changes, no behavior change |
| `chore/<scope>` | Tooling, deps, CI, cleanup |
| `docs/<scope>` | Documentation only |

Keep the slug short and kebab-case, e.g. `fix/r8-animation-release`, `chore/koin-application-migration`.

## Rebase Variant
When the user says *"rebase #NNNN"* or *"dust off PR NNNN"*:

```bash
git fetch upstream --prune
gh pr checkout <NNNN>          # checks out the PR head locally
git rebase upstream/main
# Resolve conflicts, then:
git push --force-with-lease
```

Never use plain `--force`. Always `--force-with-lease` to avoid clobbering collaborator pushes.

## Post-Branch Checklist
- [ ] Branch name follows conventional prefix.
- [ ] `local.properties` exists.
- [ ] `ANDROID_HOME` exported (see AGENTS.md workspace bootstrap).
- [ ] Optional: run `./gradlew assembleDebug` once to catch environment regressions before editing.

## Tip: Delegate Long Audits to a Subagent
If the user's opening prompt is a sweeping audit or investigation (e.g. *"audit changes since
v2.7.13 for regressions"*, *"investigate why animations are broken on release"*), dispatch the
`Explore` subagent (read-only, broad fan-out) so file dumps stay out of the main context and
you get back just the conclusion; use the `gradle-runner` subagent for heavy `./gradlew` runs.
See CLAUDE.md → "Delegate to keep context lean".
