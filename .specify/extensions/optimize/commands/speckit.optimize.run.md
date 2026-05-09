---
description: Audit and optimize governance documents for AI context efficiency.
handoffs:
  - label: Amend constitution
    agent: speckit.constitution
    prompt: Apply the approved optimization changes to the constitution
  - label: Verify consistency
    agent: speckit.analyze
    prompt: Verify cross-artifact consistency after governance changes
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).
Arguments: `--category <name>` to run a single category, `--report-only` to skip the apply step.

## Goal

Audit an existing, populated constitution for problems that are **uniquely harmful in AI-driven development**: token bloat, stale rules, ambiguity causing non-deterministic behavior, redundant governance echoes, and incoherent structure. Produce a findings report with a concrete optimization plan. Apply **only** what the user explicitly approves.

This command does NOT author or amend the constitution (that is `/speckit.constitution`). It audits and optimizes existing content.

## Operating Constraints

- **Suggest-only**: NEVER modify any file without explicit user consent. Always present findings and a plan first, then ask before applying.
- **Semantic preservation**: Optimization removes redundancy, not intent. Every governance rule must survive compression — only its expression changes.
- **Spec-kit standard paths**: Use `.specify/memory/constitution.md` as the primary constitution path. If it contains a redirect (e.g., "Read and follow the constitution in `<path>`"), follow the redirect to the actual file. Fallback discovery order: `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`.
- **Constitution authority**: Respect the constitution's own governance section. Version bumps follow its defined semver policy.
- **Idempotency**: Running this command twice in succession on an optimized constitution MUST produce no new findings.

## Execution Steps

### 1. Locate and Load Constitution

Resolution order:
1. Read `.specify/memory/constitution.md`
2. If it contains a redirect pattern (e.g., `Read and follow the constitution in <path>`), follow the redirect to the actual file
3. If `.specify/memory/constitution.md` does not exist, check fallbacks: `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md`
4. Abort with clear error if no constitution found

Validate the file is a populated constitution (not a raw template with `[PLACEHOLDER]` tokens). If it is still a template, advise the user to run `/speckit.constitution` first and abort.

Record the resolved file path as `CONSTITUTION_PATH` for all subsequent steps.

### 2. Load Configuration

Check for project config at `.specify/extensions/optimize/optimize-config.yml`. If not found, use `defaults` from `extension.yml`. Parse:
- Which categories are enabled
- Threshold values
- Target context window size

### 3. Parse Constitution Structure

Extract and catalog:
- **Sync Impact Report** (HTML comment at top) — version, dates, template status
- **Version History** (HTML comment) — all version entries
- **Title** (H1 heading)
- **Core Principles** — for each: number, name, NON-NEGOTIABLE flag, individual rules as a flat list (each bullet, MUST/SHOULD statement, or table row with normative content)
- **Quality Gates** table
- **Governance** section — authority, amendment process, version semantics
- **Version footer** — current version, ratified date, last amended date

Store each principle's rules as a flat list for cross-comparison.

### 4. Discover Governance Ecosystem

Scan for all governance files that AI agents may load:
- `.specify/memory/constitution.md` (and its redirect target)
- `CLAUDE.md` (root)
- `AGENTS.md` (root)
- `.github/copilot-instructions.md`
- All files in `.ai/rules/` (if directory exists)
- All files in `.specify/memory/` (if any beyond constitution)

For each file found, record: path, size in characters, estimated tokens (chars ÷ `chars_per_token`).

### 5. Run Analysis Categories

Run each enabled category. If `--category <name>` was provided, run only that one.

---

#### Category 1: Token Budget Analysis

*Why AI-specific*: AI agents pay the full token cost of the constitution on every single invocation. A 3000-token constitution across 50 daily sessions = 150K tokens/day of governance overhead. Humans skim; AI tokenizes everything.

**Checks:**

1. **Total token estimate**: Calculate chars ÷ `chars_per_token` for the constitution and each governance file discovered in Step 4.

2. **Per-section token breakdown**: For each H2/H3 section in the constitution, calculate its token cost and compute a "governance density" score = (number of distinct rules in section) ÷ (estimated tokens in section). Low density = high waste.

3. **Version history bloat**: Detect HTML comment blocks containing version history (pattern: `<!-- ... v\d+\.\d+\.\d+ ... -->`). These are valuable for humans reviewing the file but add zero governance value for AI agents. Measure their token cost.

4. **Anti-pattern tax**: Detect sections containing both "WRONG" / "Anti-Pattern" / "NEVER" code blocks AND "CORRECT" / "RIGHT" / "Correct Pattern" code blocks. The anti-pattern is often inferable from the correct pattern alone. Measure the token cost of each anti-pattern block.

5. **Inline code duplication**: For each fenced code block in the constitution, search the repository for matching files or near-matching code. If the code exists in the repo, it can be replaced with a file reference (e.g., "See `src/.../BeanConfiguration.java`"). Use glob/grep to find matching class names, method signatures, or patterns from the code block.

6. **Double-governance**: For each rule, check if an equivalent enforcement exists in:
   - Checkstyle config (glob for `**/checkstyle*.xml`)
   - Build tool config (glob for `build.gradle*`, `buildSrc/**`)
   - Dependency management (glob for `**/libs.versions.toml`, `**/pom.xml`)
   - CI pipeline config (glob for `.github/workflows/*`, `.pipelines/*`, `azure-pipelines*`)
   If a tool already enforces the rule, the constitution copy is redundant — it can be compressed to a reference.

7. **Prose-table overlap**: Detect when the same information appears in both prose (paragraph/bullets) and a table within the same H3 section. Measure the overlap token cost.

**Output per finding**: Section path, token cost, issue type, suggested fix, projected savings.

---

#### Category 2: Rule Health Analysis

*Why AI-specific*: AI agents have no institutional memory. A rule added 6 months ago for a one-time incident is enforced with the same authority as a core architectural principle. There is no natural "forgetting" mechanism — stale rules persist forever.

**Checks:**

1. **Incident-specific rules**: Detect rules that reference specific class names, method names, or file paths (backtick-wrapped identifiers like `` `ClassName` ``, `` `methodName` ``). Cross-reference: search the codebase for the named artifact. If it exists in only one component or has been removed, the rule may be too narrow for a project-wide constitution or entirely stale.

2. **Superseded rules**: Within the same principle and across principles, detect rules that govern the same domain at different specificity levels. Example: "no magic numbers" (general) + "use named constants for all numeric values" (specific) — the specific one supersedes the general.

3. **Graduated rules**: For each rule, check if it is fully enforced by automation:
   - Parse checkstyle config for matching check names (e.g., `MagicNumberCheck` → "no magic numbers" rule)
   - Check `buildSrc/` for custom Gradle tasks (e.g., `CheckFileHeaderTask` → "file headers required")
   - Check CI pipeline for quality gates
   If a rule is 100% enforced by tooling, the constitution statement is redundant and can be compressed to: "Enforced by [tool] — see `[config path]`."

4. **Stale rules via git history**: Run `git log --follow -p` on the constitution file. For rules introduced in older versions (check the version history comment block), evaluate whether the context that motivated the rule still applies. Flag rules that haven't been touched in >3 versions AND reference specific artifacts.

**Output per finding**: Rule text, principle location, health classification (CORE / OPERATIONAL / INCIDENT-RESPONSE / GRADUATED), recommendation, evidence.

---

#### Category 3: AI Interpretability Analysis

*Why AI-specific*: Ambiguity in the constitution causes non-deterministic behavior — different AI sessions resolve the same ambiguity differently, leading to inconsistent codebases. Rules that require human judgment are dead code to AI agents.

**Checks:**

1. **Unenforceable rules (require human action)**: Scan for rules containing: "check with", "discuss with", "team lead approval", "manual review", "consult", "ask before", "get sign-off". These are meaningful to humans but unactionable by AI agents.

2. **Ambiguous quantifiers**: Scan for rules containing: "appropriate", "reasonable", "sufficient", "proper", "clean", "good", "well-structured", "meaningful", "as needed", "where possible", "when necessary". These are interpreted differently by different AI models and sessions. For each, propose a concrete, deterministic replacement.

3. **Missing enforcement mechanism**: For each MUST rule, check if there is a corresponding automated enforcement (checkstyle, CI, Gradle task, spec-kit command). If a rule says MUST but nothing checks compliance, it is "aspirational governance" — effective only when the AI agent happens to remember it.

4. **Contradiction detection**: Parse all rules into normalized assertion form. Check for:
   - **Direct contradictions**: Rule A says "MUST X" and Rule B says "MUST NOT X" or implies not-X
   - **Indirect contradictions**: Rule A requires pattern P, Rule B requires pattern Q, where P and Q are mutually exclusive in practice
   - **Scope conflicts**: Two principles claim authority over the same domain with different guidance
   For each pair, assess severity: CRITICAL (direct), HIGH (indirect), MEDIUM (scope overlap).

5. **Implicit context dependencies**: Scan for rules referencing: "the team's convention", "our usual approach", "as discussed", "you know", "the standard pattern" (without specifying which). These rely on context that AI agents don't carry between sessions.

6. **Non-deterministic choice points**: Scan for rules with: "or" / "either...or" / "when appropriate" / "use your judgment" / "consider" that leave the resolution to the AI agent without a default. Each is a source of cross-session inconsistency.

**Output per finding**: Rule text, location, interpretability issue type, proposed deterministic rewrite, severity.

**Per-rule score** (0–100): Based on specificity (25), enforceability (25), determinism (25), self-containedness (25). Report average per principle and overall.

---

#### Category 4: Semantic Compression

*Why AI-specific*: 10 verbose rules that could be expressed as 2 concise rules cost 5× more context tokens for identical governance. This is not about human readability — it is about information density for context-limited AI consumers.

**Checks:**

1. **Collapsible rule clusters**: Group rules by semantic domain (testing, naming, architecture, dependencies, documentation). Within each group, identify rules that share a common parent assertion. Example: "No wildcard imports", "No magic numbers", "Explicit this. prefix", "JavaDoc required" are all checkstyle-enforced quality rules that could be collapsed to a single reference: "All code MUST pass checkstyle (`config/checkstyle/checkstyle.xml`) with zero violations." Measure per-cluster token savings.

2. **Inline-to-reference conversion**: For each fenced code block (identified in Cat 1), if the code exists as an actual file in the repo, propose replacing the inline block with a file reference. Example: 12 lines of `BeanConfiguration` Java code → "See `src/.../BeanConfiguration.java` for the canonical pattern." Measure per-block token savings.

3. **Redundant examples**: For sections containing both WRONG and CORRECT code blocks, evaluate whether the anti-pattern is inferable from the correct pattern and the rule text. If yes, the anti-pattern block can be removed. Measure savings.

4. **Table compression**: Detect tables where most cells follow a derivable pattern. Example: A 7-line Model Types table could be 3 lines of prose. Measure savings.

5. **Compressed constitution draft**: If total projected savings exceed 10%, produce a full compressed draft that preserves every governance rule while minimizing tokens. Include a "governance preservation check" listing every original rule and its location in the compressed version.

**Output per finding**: Original section, proposed replacement, token savings, governance preservation confirmation.

---

#### Category 5: Constitution Coherence

*Why AI-specific*: AI agents read the constitution linearly and assign roughly equal weight to all sections. A constitution that has grown organically through many amendments tends to be structurally unbalanced — one principle with 30 rules, another with 3. Related rules scattered across principles. Missing cross-references. No clear narrative arc. A human can mentally reorganize; an AI agent cannot.

**Checks:**

1. **Principle balance**: Count rules per principle (bullets, MUST/SHOULD statements, normative table rows). Flag if the largest principle has more than `principle_balance_ratio` (default: 3×) the rules of the smallest. Report the count per principle.

2. **Rule scatter**: For each rule, extract its semantic domain (testing, naming, architecture, dependencies, documentation, API, security). If rules from the same domain appear in more than one principle, flag as scattered. Example: naming conventions in Principle I + entity naming in Principle III = naming rules scattered.

3. **Missing cross-references**: Detect rules that reference concepts defined in other sections without an explicit cross-reference (e.g., a testing rule mentions "coverage" but coverage thresholds are in Quality Gates — no link between them).

4. **Orphaned sections**: Detect sections that are neither referenced by nor reference any other section. These may be bolt-on additions from specific AI sessions that were never integrated into the overall narrative.

5. **CLAUDE.md summary drift**: If `CLAUDE.md` exists and contains a "Critical Rules" or similar summary section, compare each rule against the constitution. Detect:
   - Rules in the summary missing from the constitution (orphaned summaries)
   - Rules in the constitution missing from the summary (under-documented)
   - Rules with wording differences between the two (drift)

**Output per finding**: Location, issue type, proposed resolution. Overall coherence score (0–100) based on balance (25), scatter (25), cross-referencing (25), drift (25).

---

#### Category 6: Governance Echo Detection

*Why AI-specific*: AI-driven projects accumulate multiple governance files — each loaded into the AI context. The same rule restated across files wastes tokens on every invocation and introduces contradiction risk when one copy is updated but others are not.

**Checks:**

1. **Cross-file rule duplication**: For each governance file discovered in Step 4, extract rules (bullets, MUST/SHOULD statements, normative table rows). Compare rules across all file pairs. Flag near-duplicates (same semantic intent, different wording).

2. **Summary drift**: Compare the main constitution against each governance file that summarizes it (typically `CLAUDE.md`). Detect rules updated in one but not the other.

3. **Redundant governance files**: If a governance file's rules are entirely a subset of the constitution's rules, the file is redundant. The entire file could be replaced with a pointer: "See `.specify/memory/constitution.md`."

4. **Governance chain depth**: Trace how the constitution is loaded by each AI tool. Count the number of governance documents in the loading chain and their cumulative token cost.

5. **Total governance budget**: Sum estimated tokens across all governance files. Express as a percentage of the target context window (from config). Flag if exceeding `governance_budget_percent` (default: 15%).

**Output per finding**: Source file, target file, duplicated rule text, recommendation. Overall governance echo map showing which files duplicate which rules.

---

### 6. Generate Unified Findings Report

Combine all category results into a single report. Present to the user:

```markdown
## Governance Optimization: Findings Report

**Constitution**: <CONSTITUTION_PATH>
**Current Version**: <version>
**Estimated Tokens**: <total> (~<lines> lines)
**Governance Ecosystem**: <file_count> files, <total_tokens> tokens (<percent>% of <context_window> context)

### Executive Summary

| Category | Findings | Severity | Projected Savings |
|----------|----------|----------|-------------------|
| Token Budget | X | <highest> | ~Y tokens |
| Rule Health | X | <highest> | — |
| AI Interpretability | X | <highest> | — |
| Semantic Compression | X | <highest> | ~Y tokens |
| Coherence | X | <highest> | — |
| Governance Echo | X | <highest> | ~Y tokens |

**Overall Health Score**: X/100
**Total Projected Token Reduction**: ~Y tokens (Z%)

### Top 5 Findings (by impact)

1. [Finding with highest token savings or highest severity]
2. ...

### Detailed Findings

[Per-category details as described in each category's output section]
```

### 7. Propose Optimization Plan

Based on findings, produce a concrete plan:

```markdown
### Proposed Changes

| # | Change | Category | Files Affected | Token Impact | Risk |
|---|--------|----------|----------------|--------------|------|
| 1 | Remove version history HTML comments | Token Budget | constitution | -X tokens | Low |
| 2 | Compress checkstyle rules to reference | Compression | constitution | -X tokens | Low |
| ... | ... | ... | ... | ... | ... |

### Version Bump

- **Type**: PATCH / MINOR / MAJOR
- **Rationale**: [why this bump level]
- **New Version**: X.Y.Z

**Apply these changes?** Select which changes to apply, or approve all.
```

Wait for user consent. Do NOT proceed without explicit approval.

### 8. Apply Approved Changes

For each user-approved change:

1. Apply the modification to `CONSTITUTION_PATH`
2. Preserve the overall document structure (Sync Impact Report comment, version history, principles, quality gates, governance, footer)
3. Update the version footer: bump per the semver rules in the constitution's governance section
4. Update `Last Amended` date to today (ISO format YYYY-MM-DD)
5. Add a new version history entry in the HTML comment block
6. Update the Sync Impact Report HTML comment at the top

### 9. Post-Application Validation

After writing changes:
1. Re-parse the updated constitution — verify no remaining `[PLACEHOLDER]` bracket tokens
2. Verify version footer matches Sync Impact Report
3. Verify all dates are ISO format (YYYY-MM-DD)
4. Re-run a quick check on the output — verify no new contradictions or ambiguities were introduced by the edits
5. Verify the total governance rule count has not decreased (compression changes expression, not intent)

### 10. Output Summary

```markdown
## Governance Optimization Complete

**Version**: <old> → <new> (<bump-type>)
**Constitution**: <CONSTITUTION_PATH>
**Token Reduction**: <old_tokens> → <new_tokens> (<percent>% savings)

### Changes Applied
- [List of applied changes with token impact]

### Changes Declined
- [List of user-declined changes, preserved for next run]

### Sync Impact Report Updated
- Version change: <old> → <new>
- Modified sections: [list]
- Templates status: [all aligned / needs review]

### Suggested Commit Message
docs: optimize constitution to v<new> — reduce governance token overhead by <percent>%

### Recommended Follow-Up
- Review updated constitution for accuracy
- Run `/speckit.constitution` if substantive amendments are needed beyond optimization
- Run `/speckit.analyze` to verify cross-artifact consistency
- Run `/speckit.optimize.tokens` to verify ecosystem-wide token budget
```

## Operating Principles

### Suggest-Only
Every change is proposed, never applied silently. The user has full veto power over every individual finding. "Apply all" is offered as a convenience but never the default.

### Semantic Preservation
Optimization MUST NOT change the meaning of any rule. Compression removes redundancy in expression, not in intent. After optimization, every governance rule that existed before MUST still be expressible from the optimized document.

### Constitution Authority
The review respects the constitution's own governance section. Version bumps follow the defined semver policy. If the governance section specifies an amendment process, the optimization follows it.

### Idempotency
Running this command twice in succession on the same constitution MUST produce zero new findings on the second run. If it does not, there is a bug in the optimization logic.

### Context Efficiency
The primary goal is making the constitution cheaper to include in AI context windows while maintaining full governance clarity. Every recommendation must be justified by a concrete token savings figure or a measurable improvement in AI interpretability.
