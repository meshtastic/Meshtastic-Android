---
description: Analyze AI session patterns to suggest constitution rules or memory entries.
handoffs:
- label: Amend constitution
  agent: speckit.constitution
  prompt: Add the approved rules to the constitution
- label: Optimize governance
  agent: speckit.optimize.run
  prompt: Run a full governance audit after adding new rules
---


<!-- Extension: optimize -->
<!-- Config: .specify/extensions/optimize/ -->
## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).
Arguments: `--rules-only` to skip memory suggestions, `--memory-only` to skip rule suggestions, `--since <commit>` to limit analysis scope.

## Goal

Analyze the current AI session's work to identify patterns of mistakes, repetitive corrections, and governance gaps. Produce suggestions for new constitution rules or memory entries that would prevent these patterns in future sessions. Apply **nothing** without explicit user consent.

This command answers: "What did this AI session learn the hard way that future sessions should know from the start?"

**When to use**: End of an implementation session, before creating a PR/MR. Run while the session context is still fresh.

## Operating Constraints

- **Suggest-only**: NEVER add rules to the constitution or write memory files without explicit user consent. Always present proposals first.
- **Evidence-based**: Every suggestion MUST cite specific files, diffs, or session events as evidence. No speculative rules.
- **Spec-kit standard paths**: Use `.specify/memory/constitution.md` (follow redirects) for the constitution. Memory files go to the tool's memory system (e.g., `.claude/` for Claude Code).
- **Minimal governance growth**: Prefer memory entries over constitution rules unless the pattern affects all team members and all AI tools. Constitution rules have a token cost paid on every future session.
- **Deterministic proposals**: Every proposed rule MUST be concrete, MUST/SHOULD qualified, and deterministic — no vague language.

## Execution Steps

### 1. Determine Analysis Scope

Identify the work done in the current session:

1. **Git-based scope** (primary):
   - Run `git log --oneline` to find recent commits
   - If `--since <commit>` is provided, use that as the starting point
   - Otherwise, heuristically identify the session boundary: look for commits from today, or the most recent cluster of commits by the current author
   - Record the commit range as `SESSION_RANGE`

2. **Diff analysis**:
   - Run `git diff <SESSION_RANGE>` to get the full session diff
   - Run `git diff --stat <SESSION_RANGE>` for a file-level overview
   - Record all modified files as `SESSION_FILES`

3. **Session metadata**:
   - Count: commits, files modified, lines added, lines removed
   - Identify: primary language(s), directories touched, components affected

If no git changes are found, inform the user: "No session changes detected. This command analyzes git history to find patterns. Run it after making changes."

### 2. Load Current Governance

Load the constitution (following the standard resolution chain from `.specify/memory/constitution.md`). Parse into a flat list of rules for gap analysis.

Load config from `.specify/extensions/optimize/optimize-config.yml` (or defaults) for:
- `min_corrections_to_flag` (default: 2)
- `include_memory_suggestions` (default: true)
- `include_rule_suggestions` (default: true)

### 3. Detect Mistake Patterns

Analyze the session's git history for evidence of repeated corrections:

#### 3a. Repeated Correction Patterns

For each file in `SESSION_FILES`, check commit history within `SESSION_RANGE`:

- **Same-file re-edits**: Files modified in 3+ separate commits within the session. This suggests the AI got it wrong initially and had to correct multiple times. Record the file and the nature of each change.

- **Revert patterns**: Look for pairs of commits where the second commit undoes part of the first (same lines modified in opposite directions). This indicates the AI made a wrong choice that was immediately corrected.

- **Fix-after-fix chains**: Commits with messages containing correction indicators: "fix", "actually", "oops", "correct", "should be", "typo", "missed", "forgot". Each represents a mistake the AI made.

- **Checkstyle/linter fix commits**: Commits that only fix style violations (detected by diffing against checkstyle or linter output). These indicate the AI didn't follow style rules the first time.

#### 3b. Repeated Transformation Patterns

Look for the same type of change applied to multiple files:

- **Boilerplate additions**: Same code pattern added to 3+ files (e.g., adding `this.` prefix, adding file headers, adding import ordering). If the AI had to manually apply the same transformation many times, it suggests a rule that should be automated.

- **Naming corrections**: Same type of rename applied multiple times (e.g., removing `Entity` suffix from non-entity classes, or adding it to entity classes). Suggests unclear naming rules.

- **Pattern enforcement**: Same structural change across files (e.g., converting `@Service` annotations to `@Bean` registration). Suggests the AI kept defaulting to a wrong pattern.

#### 3c. Constitution Violation Patterns

For each detected mistake pattern:

1. Search the constitution for a rule that should have prevented it
2. If a rule exists:
   - The AI violated an existing rule → the rule may be ambiguous, poorly worded, or easy to miss
   - Record as: "Existing rule violated — suggest rewrite for clarity"
3. If no rule exists:
   - The pattern represents a governance gap
   - Record as: "No existing rule — suggest new rule"

### 4. Detect Repetitive Task Patterns

Beyond mistakes, identify tasks the AI performed repeatedly that suggest missing automation or rules:

- **Manual enforcement**: Tasks that could be automated (e.g., repeatedly checking import order → should be enforced by a linter, repeatedly adding JavaDoc → should be caught by checkstyle)

- **Boilerplate generation**: Repeated creation of similar files (e.g., creating test classes with the same structure, creating DTOs with the same patterns). Suggests templates or generators would help.

- **Cross-file consistency**: Changes that required updating multiple files to stay consistent (e.g., adding a field to an entity requires updating the DTO, the mapper, and the test). Suggests a documentation or tooling gap.

### 5. Generate Proposals

For each detected pattern, generate a proposal. Proposals are either **constitution rules** or **memory entries**.

#### Constitution Rule Proposals

Only propose constitution rules when the pattern:
- Affects all developers (not just one person's preference)
- Applies across all AI tools (not tool-specific)
- Is project-wide (not component-specific)
- Would prevent the mistake in future sessions

Format for each proposed rule:

```markdown
### Proposed Rule: <short title>

**Type**: Constitution Rule
**Principle Placement**: <existing principle name, or "New Principle: <name>">
**Severity**: MUST / SHOULD

**Rule Text**:
> <Concrete, deterministic rule text. MUST/SHOULD qualified. No vague language.>

**Rationale**: <What session pattern triggered this>

**Evidence**:
- `<file>:<line>` — <what happened>
- Commit `<hash>` — <what the fix was>
- Pattern repeated <N> times across <files>

**Enforcement Suggestion**: <How to automate: checkstyle rule, Gradle task, CI check, or "manual review only">

**Token Cost**: ~<estimated tokens this rule adds to the constitution>
```

#### Memory Entry Proposals

Propose memory entries when the pattern:
- Is specific to this user or project (not universal)
- Is preference-based rather than governance-based
- Would help the AI agent in future sessions without being a formal rule

Format for each proposed memory:

```markdown
### Proposed Memory: <short title>

**Type**: Memory Entry
**Memory Type**: feedback / user / project / reference
**File Name**: <proposed filename, e.g., feedback_import_order.md>

**Content**:
> <Proposed memory content, structured per the memory type's conventions>

**Rationale**: <What session pattern triggered this>

**Evidence**:
- <specific examples from the session>
```

### 6. Present Learning Report

Present all proposals to the user:

```markdown
## Session Learning Report

**Session**: <commit range>
**Files Modified**: <count>
**Commits Analyzed**: <count>

### Session Patterns Detected

| # | Pattern | Occurrences | Type | Proposal |
|---|---------|-------------|------|----------|
| 1 | <pattern description> | X times | Mistake | Rule / Memory |
| 2 | <pattern description> | X times | Repetitive | Rule / Memory |
| ... | ... | ... | ... | ... |

### Existing Rules Violated

| # | Rule | Principle | Violation Count | Issue |
|---|------|-----------|-----------------|-------|
| 1 | <rule text> | <principle> | X | Ambiguous / Easy to miss |

**Suggestion**: Rewrite for clarity → <proposed rewrite>

### Proposed Constitution Rules (<count>)

[List each proposed rule per format above]

### Proposed Memory Entries (<count>)

[List each proposed memory per format above]

### Summary

- **Total patterns detected**: X
- **Constitution rules proposed**: X (adds ~Y tokens to governance)
- **Memory entries proposed**: X
- **Existing rules to rewrite**: X

**Which proposals would you like to apply?**
Select by number, "all rules", "all memories", or "none".
```

Wait for user selection. Do NOT apply anything without explicit consent.

### 7. Apply Approved Proposals

For each user-approved proposal:

**Constitution rules**:
- Do NOT directly edit the constitution
- Hand off to `/speckit.constitution` with the specific rule text, principle placement, and rationale
- This ensures proper version bumping and governance compliance

**Memory entries**:
- Write the memory file to the appropriate memory directory
- Update the memory index (e.g., `MEMORY.md`)
- Confirm each write to the user

**Rule rewrites** (for existing rules that were violated due to ambiguity):
- Hand off to `/speckit.optimize.run --category ai_interpretability` for a targeted rewrite
- Or hand off to `/speckit.constitution` for manual amendment

### 8. Output Summary

```markdown
## Session Learning Complete

### Applied
- Constitution rules handed to `/speckit.constitution`: X
- Memory entries written: X
- Rule rewrites suggested: X

### Declined
- [List of declined proposals — preserved in report for future reference]

### Learning Report Saved
- Report: `.specify/optimize/learning-report-<date>.md`

### Recommended Follow-Up
- Run `/speckit.constitution` to formally add approved rules
- Run `/speckit.optimize.run` to verify the new rules don't create contradictions
- Run `/speckit.optimize.tokens` to check token budget after additions
```

### 9. Save Learning Report

Ask the user: "Save this learning report to `.specify/optimize/learning-report-<date>.md`?"

If approved, save the full report for historical reference. This enables trend analysis across sessions: "Are the same patterns recurring despite rules being added?"

## Operating Principles

### Evidence-Based Only
Every proposal cites specific files, line numbers, commits, and pattern counts. No speculative rules based on general best practices — only rules motivated by observed session behavior.

### Minimal Governance Growth
Prefer memory entries (zero token cost to future sessions) over constitution rules (permanent token cost). Only propose constitution rules when the pattern is project-wide, tool-agnostic, and would benefit all future AI sessions.

### Deterministic Proposals
Every proposed rule text is concrete, MUST/SHOULD qualified, and deterministic. If the AI agent writing the proposal cannot make the rule deterministic, it should propose a memory entry instead.

### Suggest-Only
The learning report is a proposal, not an action. The user reviews each suggestion individually and decides what to keep. Declined proposals are preserved in the report for future reconsideration.

### Session Boundary Respect
This command only analyzes the current session's work. It does not dig into older history or make suggestions based on past sessions. For historical analysis, use `/speckit.optimize.run` which audits the full constitution.