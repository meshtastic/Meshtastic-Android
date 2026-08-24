# Spec-Kit Optimize Extension

Audits and optimizes AI governance documents for context efficiency. Designed for long-term AI-driven development where constitutions grow organically and accumulate token debt.

## Why This Extension Exists

In AI-driven development, the constitution is loaded into every AI session's context window. Over time:

- **Token bloat**: Rules, examples, and version history accumulate — costing tokens on every invocation
- **Rule decay**: Incident-specific rules persist forever because AI has no institutional memory
- **Non-deterministic governance**: Ambiguous rules cause different AI sessions to behave differently
- **Governance echoes**: The same rule gets restated across multiple files, wasting tokens and risking contradictions
- **No learning loop**: AI sessions make the same mistakes repeatedly with no mechanism to capture learnings

This extension provides tooling to detect and fix these problems.

## Installation

```bash
# From the spec-kit catalog (when published)
specify extension add optimize

# From a direct URL
specify extension add optimize --from https://github.com/sakitA/spec-kit-optimize/archive/refs/tags/v1.0.0.zip

# For local development
specify extension add --dev /path/to/spec-kit-optimize
```

## Commands

### `/speckit.optimize.run` — Constitution Audit

Analyzes the constitution across 6 categories uniquely relevant to AI-driven development:

1. **Token Budget Analysis** — measures per-section token cost and governance density
2. **Rule Health Analysis** — detects stale, incident-specific, superseded, and graduated rules
3. **AI Interpretability Analysis** — finds ambiguity, contradictions, unenforceable rules
4. **Semantic Compression** — identifies collapsible rule clusters, redundant examples, inline-to-reference conversions
5. **Constitution Coherence** — evaluates principle balance, rule scatter, cross-references, CLAUDE.md drift
6. **Governance Echo Detection** — finds cross-file duplication and total governance token budget

```bash
# Full audit
/speckit.optimize.run

# Single category
/speckit.optimize.run --category token_budget

# Report only (no apply step)
/speckit.optimize.run --report-only
```

### `/speckit.optimize.tokens` — Token Usage Tracker

Measures the token footprint of all governance files and extension commands. Tracks trends over time.

```bash
# Full token report
/speckit.optimize.tokens

# Compare against previous report
/speckit.optimize.tokens --diff

# Extensions only (skip governance files)
/speckit.optimize.tokens --extensions-only
```

### `/speckit.optimize.learn` — Session Learning

End-of-session analysis: detects AI mistake patterns, repetitive corrections, and governance gaps. Suggests constitution rules or memory entries to prevent recurrence.

```bash
# Full session analysis
/speckit.optimize.learn

# Rules only (no memory suggestions)
/speckit.optimize.learn --rules-only

# Analyze from a specific commit
/speckit.optimize.learn --since abc1234
```

## Design Philosophy

**Suggest-only by default.** Every command produces a report first. Nothing is modified until the user explicitly approves. The flow is always: Analyze → Report → Propose → User Consent → Apply.

**Spec-kit standard paths.** The extension works with any spec-kit project. It uses `.specify/memory/constitution.md` as the primary constitution path and follows redirects for project-specific layouts. It never hardcodes project-specific paths.

**Semantic preservation.** Optimization removes redundancy in expression, not in intent. Every governance rule survives compression — only its token cost changes.

## Configuration

Copy `config-template.yml` to `optimize-config.yml` in the extension directory:

```bash
cp .specify/extensions/optimize/config-template.yml \
   .specify/extensions/optimize/optimize-config.yml
```

Key settings:
- `categories.*` — toggle individual analysis categories on/off
- `thresholds.max_constitution_tokens` — flag constitutions exceeding this token estimate
- `thresholds.governance_budget_percent` — max % of context window for governance overhead
- `target_context_window` — context window size for budget calculations (default: 200K)
- `learn.min_corrections_to_flag` — minimum repeated corrections before flagging a pattern

## Integration

- **`/speckit.constitution`** — authoring tool. Optimize hands off to it for applying approved changes with proper version bumping.
- **`/speckit.analyze`** — consistency checker. Run after optimization to verify cross-artifact alignment.
- **`/speckit.optimize.tokens`** → **`/speckit.optimize.run`** — if token tracker reveals high governance overhead, run the full audit.
- **`/speckit.optimize.learn`** → **`/speckit.constitution`** — approved rules from session learning are applied via the constitution skill.

## Reports

Reports are saved to `.specify/optimize/` (with user consent):
- `token-report.md` — latest token usage snapshot (enables historical trends)
- `learning-report-<date>.md` — per-session learning analysis

## Requirements

- Spec-Kit >= 0.1.0
- An existing populated constitution (not a raw template)
- Git repository (for learn command's session analysis)

## License

MIT
