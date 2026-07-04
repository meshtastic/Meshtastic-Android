# Code review Extension for Spec Kit

Post-implementation code review with specialized agents for code quality, comments, tests, error handling, type design, and simplification. Orchestrates 6 focused review agents into a single consolidated report with severity-based grouping and actionable remediation guidance.

## Features

- **Coordinator** (`/speckit.review.run`): Orchestrates all agents, produces a consolidated report
- **Code Reviewer** (`/speckit.review.code`): Project guideline compliance, bug detection, code quality
- **Comment Analyzer** (`/speckit.review.comments`): Comment accuracy, documentation completeness, comment rot
- **Test Analyzer** (`/speckit.review.tests`): Behavioral coverage, critical gap identification, test resilience
- **Error Handling Reviewer** (`/speckit.review.errors`): Silent failure detection, catch block analysis, error logging
- **Type Design Analyzer** (`/speckit.review.types`): Encapsulation, invariant expression, usefulness, and enforcement
- **Code Simplifier** (`/speckit.review.simplify`): Clarity analysis, unnecessary complexity, redundant abstractions

## Installation

```bash
# From community catalog
specify extension add review

# Or from repository directly
specify extension add review --from https://github.com/ismaelJimenez/spec-kit-review/archive/refs/tags/v1.0.1.zip

# Local development
specify extension add --dev /path/to/spec-kit-review
```

Verify installation:

```bash
specify extension list
# Should show:
#  ✓ Review Extension (v1.0.1)
#     Post-implementation comprehensive code review with specialized agents for code quality, comments, tests, error handling, type design, and simplification.
#     Commands: 7 | Hooks: 1 | Status: Enabled
```

## Usage

### Full Coordinated Review

Run all specialized agents against your changes and get a consolidated report:

```
/speckit.review.run
```

All commands (coordinator and individual agents) use the built-in `detect-changed-files` script to automatically identify what to review when no files are specified:
- **Feature branch**: Committed changes since the merge base with the default branch (main/master), plus any uncommitted work
- **Default branch**: Only uncommitted work (staged and unstaged changes)

You can skip the script entirely by telling the agent what to review:

```
/speckit.review.run only staged changes
/speckit.review.run only files in src/utils/
```

### Targeted Review

Run only specific agents by passing aspect names:

```
/speckit.review.run tests errors      # Only test and error handling analysis
/speckit.review.run code              # Only code quality review
/speckit.review.run comments simplify # Only comment analysis and simplification
```

Valid aspects: `code`, `comments`, `tests`, `errors`, `types`, `simplify`, `all`

### Parallel Review

By default agents run sequentially so you can act on each report as it arrives. Add `parallel` to launch all agents simultaneously for faster results:

```
/speckit.review.run all parallel      # Full review, all agents in parallel
/speckit.review.run tests errors parallel  # Parallel targeted review
```

Parallel mode is useful for comprehensive reviews where you want all findings at once rather than incremental feedback.

### Direct Agent Invocation

Run any agent directly for focused, deep analysis:

```
/speckit.review.code       # Code quality review
/speckit.review.comments   # Comment accuracy analysis
/speckit.review.tests      # Test coverage analysis
/speckit.review.errors     # Error handling review
/speckit.review.types      # Type design analysis
/speckit.review.simplify   # Code simplification suggestions
```

Each agent auto-detects changed files independently when invoked directly.

## Report Output

The consolidated report includes:

- **Critical Issues**: Must-fix issues identified by agents — file, line, description
- **Important Issues**: Should-fix issues — file, line, description
- **Suggestions**: Nice-to-have improvements — file, line, description
- **Strengths**: What's well-done in the PR
- **Recommended Action**: Prioritized remediation steps

## Configuration

### Project Guidelines

If project-specific guidelines exist (`.specify/memory/constitution.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, or equivalent), agents use them as additional review criteria for project-specific conventions and standards.

## Environment Requirements

- **git**: Required for change detection
- **spec-kit**: >= 0.1.0

## Token Usage

> **Heads up:** A full coordinated review (`/speckit.review.run`) dispatches 6 specialized agents, each of which reads the changed files independently. This can be token-intensive on larger PRs. To reduce costs, run targeted reviews (`/speckit.review.run code errors`) instead of the full suite.

## Recommended Workflow

```
1. Implement changes:       /speckit.implement
2. Run full review:         /speckit.review.run
3. Fix critical issues
4. Re-run targeted review:  /speckit.review.run code errors
5. Verify fixes resolved
6. Create PR
```

## Integration with Verify Extension

If you also use the [Verify Extension](https://github.com/ismaelJimenez/spec-kit-verify) (`spec-kit-verify`), the recommended workflow is:

```
1. Implement changes:       /speckit.implement
2. Verify spec alignment:   /speckit.verify.run
3. Run PR review:           /speckit.review.run
4. Fix issues and iterate
```

The verify extension validates that your implementation matches specification artifacts (spec.md, plan.md, tasks.md). The review extension then performs broader code quality analysis. When both are installed, the verify extension offers a handoff to run the review automatically after verification completes.

## Troubleshooting

### Issue: Command not available

**Solutions:**

1. Check extension is installed: `specify extension list`
2. Restart AI agent
3. Reinstall extension: `specify extension add review`

### Issue: `Validation Error: Invalid alias 'speckit.review'`

**Solution:** Upgrade to v1.0.1 or later and invoke the coordinator with `/speckit.review.run`. Spec Kit now enforces three-segment alias names, so `/speckit.review` is no longer accepted by the validator.

### Issue: "Not a git repository" error

**Solution:** The review extension requires git for change detection. Initialize a git repository with `git init` or run commands from within an existing repo.

### Issue: "No changes detected"

**Solution:** Make some code changes first. On a feature branch, commit changes. On the default branch, stage or modify files.

## Acknowledgments

The first version of this extension was modeled after the [PR Review Toolkit](https://github.com/anthropics/claude-code/tree/main/plugins/pr-review-toolkit) plugin for Claude Code by Anthropic.

## License

MIT License — see [LICENSE](LICENSE) file

## Support

- Issues: [https://github.com/ismaelJimenez/spec-kit-review/issues](https://github.com/ismaelJimenez/spec-kit-review/issues)
- Spec Kit Docs: [https://github.com/github/spec-kit](https://github.com/github/spec-kit)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

Extension Version: 1.0.1 · Spec Kit: >=0.1.0
