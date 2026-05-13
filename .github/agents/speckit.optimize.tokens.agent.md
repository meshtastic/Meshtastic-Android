---
description: Track and report token usage across extensions and governance files.
handoffs:
- label: Optimize governance
  agent: speckit.optimize.run
  prompt: Run a full governance audit to reduce token overhead
- label: Amend constitution
  agent: speckit.constitution
  prompt: Apply approved token-reduction changes to the constitution
---


<!-- Extension: optimize -->
<!-- Config: .specify/extensions/optimize/ -->
## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).
Arguments: `--diff` to compare against the previous report, `--extensions-only` to skip governance files.

## Goal

Measure the token footprint of every governance document and extension command that AI agents load during sessions. Produce a token usage report with per-file costs, per-extension rankings, session load estimates, and historical trends. Suggest optimizations but apply **nothing** without user consent.

This command answers: "How much of my AI context window is consumed by governance and tooling overhead before any actual work begins?"

## Operating Constraints

- **Suggest-only**: NEVER modify any file without explicit user consent. This command is read-only by default.
- **Spec-kit standard paths**: Start from `.specify/` as the source of truth. Discover tool-specific files (`CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`) by checking if they exist.
- **Reproducible estimates**: Token estimation uses chars ÷ `chars_per_token` (default: 4.0, configurable). Note this is approximate — actual tokenizer counts vary by model. Lower ratios (3.0–3.5) give more conservative estimates for code-heavy files.

## Execution Steps

### 1. Discover Governance Files

Scan for all files that AI agents may load on session start or command invocation:

**Always-loaded files** (loaded on every AI session):
- `CLAUDE.md` (if present — Claude Code sessions)
- `AGENTS.md` (if present — generic agent sessions)
- `.github/copilot-instructions.md` (if present — Copilot sessions)

**Constitution chain**:
- `.specify/memory/constitution.md` — read to check if it is a redirect or contains content
- If redirect, follow to the actual file (e.g., `.ai/rules/constitution.md`)
- Record both the pointer and the target

**Supplementary governance files**:
- Glob `.ai/rules/*.md` (if directory exists)
- Glob `.specify/memory/*.md` (beyond constitution)
- Any other files referenced from the always-loaded files (parse for markdown links and "Read and follow" patterns)

For each file: record path, exists (bool), size in bytes, size in characters, estimated tokens (chars ÷ `chars_per_token`).

### 2. Inventory Extension Commands

For each extension listed in `.specify/extensions.yml` → `installed:`:

1. Read `.specify/extensions/<ext-id>/extension.yml`
2. For each command in `provides.commands[]`:
   - Locate the command file (the `file:` field points to the source)
   - Measure its character count and estimated tokens
3. Sum total tokens per extension

Produce a ranked list of extensions by total token footprint.

### 3. Calculate Per-Session Load Estimates

Estimate what gets loaded for different session types:

**Baseline session** (always loaded):
- Sum tokens of always-loaded governance files
- This is the minimum overhead before any work begins

**Constitution-aware session** (baseline + constitution):
- Add constitution chain tokens
- Add supplementary governance file tokens

**Command invocation** (per command):
- For each extension command, the cost is: baseline + command file tokens + any files the command references (parse "Read" / "Load" instructions in the command file)

Present estimates for each context window size in `context_window_sizes` config (default: 8K, 32K, 128K, 200K, 1M).

```markdown
### Per-Session Token Budget

| Session Type | Tokens | % of 8K | % of 32K | % of 128K | % of 200K | % of 1M |
|---|---|---|---|---|---|---|
| Baseline (governance only) | X | X% | X% | X% | X% | X% |
| + Constitution | X | X% | X% | X% | X% | X% |
| + Largest command | X | X% | X% | X% | X% | X% |
```

### 4. Historical Trend Analysis

Check for a previous report at `.specify/optimize/token-report.md`.

If found:
- Parse the previous report's per-file token counts
- Compare each file: current vs previous
- Calculate per-file growth/reduction
- Flag files growing faster than `file_growth_percent` threshold (default: 20%)
- Show overall governance token trend (growing / stable / shrinking)

If not found:
- Note this is the first run — no trend data available
- Recommend running periodically to track trends

### 5. Generate Token Usage Report

Present the full report to the user:

```markdown
## Token Usage Report

**Date**: <ISO date>
**Target Context Window**: <from config> tokens

### Governance Files

| File | Exists | Chars | Est. Tokens | Load Timing | Notes |
|---|---|---|---|---|---|
| CLAUDE.md | Yes/No | X | X | Always | — |
| .specify/memory/constitution.md | Yes/No | X | X | Always | Redirect to <path> |
| <actual constitution path> | Yes | X | X | Always | Actual content |
| AGENTS.md | Yes/No | X | X | Always | — |
| .github/copilot-instructions.md | Yes/No | X | X | Always | — |
| .ai/rules/<file>.md | Yes | X | X | On reference | — |

**Total governance tokens**: X (~Y% of <context_window>)

### Extension Commands (ranked by token cost)

| Extension | Commands | Total Tokens | Largest Command | Largest Tokens |
|---|---|---|---|---|
| <ext-id> | X | X | <cmd> | X |
| ... | ... | ... | ... | ... |

**Total extension tokens**: X (loaded per invocation, not per session)

### Per-Session Estimates

[Table from Step 3]

### Historical Trend

| File | Previous | Current | Change | Growth % | Flag |
|---|---|---|---|---|---|
| <path> | X | X | +/-X | X% | [!] if > threshold |

**Overall governance trend**: Growing / Stable / Shrinking (X% change)

### Optimization Suggestions

[Ranked by projected token savings — suggest only, do not apply]

1. **<suggestion>**: <description> — saves ~X tokens
2. ...
```

### 6. Save Report

Ask the user: "Save this report to `.specify/optimize/token-report.md` for trend tracking?"

If approved:
- Write the report to `.specify/optimize/token-report.md` (create directory if needed)
- This enables historical trend comparison on future runs

If declined:
- Report is displayed in conversation only, not persisted

### 7. Suggest Next Steps

Based on findings:

```markdown
### Recommended Actions

- If governance budget exceeds threshold → suggest `/speckit.optimize.run` for full audit
- If specific extensions are oversized → suggest reviewing those command files for compression
- If CLAUDE.md duplicates constitution → suggest consolidation
- If growth trend is upward → suggest scheduling periodic token audits
```

## Operating Principles

### Read-Only Default
This command reads and measures — it does not modify. The only write action is saving the report file, and only with explicit consent.

### Consistent Estimation
Token counts use chars ÷ `chars_per_token` (configurable, default: 4.0) throughout. This is an approximation — actual counts vary by tokenizer. Use 3.0–3.5 for code-heavy projects, 4.0 for prose-heavy. The approximation is consistent across runs, making trend analysis valid even if absolute numbers are approximate.

### Actionable Output
Every metric in the report is paired with a concrete action: "X tokens in version history → remove via `/speckit.optimize.run`". Raw numbers without actions are noise.

### Trend Over Snapshots
A single run provides a snapshot. Repeated runs provide a trend. The historical comparison is the most valuable output — it tells you whether your governance is growing, stable, or shrinking over time.