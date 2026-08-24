---
description: Comprehensive code review using specialized agents — orchestrates code,
  comments, tests, errors, types, and simplify agents sequentially.
scripts:
  sh: .specify/scripts/bash/detect-changed-files.sh
  ps: .specify/scripts/powershell/detect-changed-files.ps1
---


<!-- Extension: review -->
<!-- Config: .specify/extensions/review/ -->
# Comprehensive PR Review

Run a comprehensive pull request review using multiple specialized agents, each focusing on a different aspect of code quality.

**Review Aspects (optional):** "$ARGUMENTS"

## Review Workflow:

1. **Load Configuration**
   - Read the project config file at `.specify/extensions/review/review-config.yml` (if it exists).
   - If the file does not exist, fall back to the `defaults.agents` section in the extension's `extension.yml`.
   - Extract the `agents` map — each key (`code`, `comments`, `tests`, `errors`, `types`, `simplify`) is a boolean toggle.
   - Agents set to `false` **MUST** be excluded from this run. Do not launch them.

2. **Determine Review Scope**
   - Parse arguments to see if user requested specific review aspects.
   - If specific aspects were requested, run exactly those — config toggles do **not** apply (explicit user request overrides config).
   - Default (no arguments): Run all applicable reviews that are enabled in config.

3. **Available Review Aspects:**

   - **comments** - Analyze code comment accuracy and maintainability
   - **tests** - Review test coverage quality and completeness
   - **errors** - Check error handling for silent failures
   - **types** - Analyze type design and invariants (if new types added)
   - **code** - General code review for project guidelines
   - **simplify** - Simplify code for clarity and maintainability
   - **all** - Run all applicable reviews (default)

4. **Identify Changed Files**

   - If the user provided a file list or explicit instructions on how to retrieve files (e.g., only staged, only unstaged, a specific folder, etc.), follow those instructions directly.
   - Otherwise, you **MUST** execute the `.specify/scripts/bash/detect-changed-files.sh` with `--json` to detect changed files. **Do not** attempt to detect changes by running `git` commands directly, reading git state manually, or using any other method — always delegate to the script.
     - The script automatically picks the best detection mode:
       - **Mode A (feature branch):** diffs the current branch against the default branch (`main`/`master`) from the merge-base, plus any staged and unstaged changes.
       - **Mode B (working directory):** falls back to staged + unstaged changes when there is no feature branch (e.g., working directly on the default branch).
     - JSON output: `{"branch", "default_branch", "mode", "changed_files": [...]}`
   - **Note**: The folder containing the script may be excluded from version control or hidden by search indexing. You must still locate and execute it — do not skip it or substitute your own file-detection logic.

5. **Determine Applicable Reviews**

   Based on changes **and** config toggles (skip any agent where `agents.<name>` is `false`):
   - **Always applicable** (if enabled): `/speckit.review.code` (general quality)
   - **If test files changed** (if enabled): `/speckit.review.tests`
   - **If comments/docs added** (if enabled): `/speckit.review.comments`
   - **If error handling changed** (if enabled): `/speckit.review.errors`
   - **If types added/modified** (if enabled): `/speckit.review.types`
   - **After passing review** (if enabled): `/speckit.review.simplify` (polish and refine)
   - If an agent is disabled by config, note it in the final summary (e.g., "simplify: skipped (disabled in config)").

6. **Launch Review Agents**

   **Sequential approach** (one at a time):
   - Easier to understand and act on
   - Each report is complete before next
   - Good for interactive review

   **Parallel approach** (user can request):
   - Launch all agents simultaneously
   - Faster for comprehensive review
   - Results come back together

7. **Aggregate Results**

   After agents complete, summarize:
   - **Critical Issues** (must fix before merge)
   - **Important Issues** (should fix)
   - **Suggestions** (nice to have)
   - **Positive Observations** (what's good)

8. **Provide Action Plan**

   Organize findings:
   ```markdown
   # PR Review Summary

   ## Critical Issues (X found)
   - [agent-name]: Issue description [file:line]

   ## Important Issues (X found)
   - [agent-name]: Issue description [file:line]

   ## Suggestions (X found)
   - [agent-name]: Suggestion [file:line]

   ## Strengths
   - What's well-done in this PR

   ## Recommended Action
   1. Fix critical issues first
   2. Address important issues
   3. Consider suggestions
   4. Re-run review after fixes
   ```

## Usage Examples:

**Full review (default):**
```
/speckit.review.run
```

**Specific aspects:**
```
/speckit.review.run tests errors
# Reviews only test coverage and error handling

/speckit.review.run comments
# Reviews only code comments

/speckit.review.run simplify
# Simplifies code after passing review
```

**Parallel review:**
```
/speckit.review.run all parallel
# Launches all agents in parallel
```

## Agent Descriptions:

**comment**:
- Verifies comment accuracy vs code
- Identifies comment rot
- Checks documentation completeness

**tests**:
- Reviews behavioral test coverage
- Identifies critical gaps
- Evaluates test quality

**errors**:
- Finds silent failures
- Reviews catch blocks
- Checks error logging

**types**:
- Analyzes type encapsulation
- Reviews invariant expression
- Rates type design quality

**code**:
- Checks project-specific guidelines (`.specify/memory/constitution.md`, `CLAUDE.md`, `.github/copilot-instructions.md`, or equivalent) compliance
- Detects bugs and issues
- Reviews general code quality

**simplify**:
- Simplifies complex code
- Improves clarity and readability
- Applies project standards
- Preserves functionality

## Tips:

- **Run early**: Before creating PR, not after
- **Focus on changes**: Agents analyze diff by default
- **Address critical first**: Fix high-priority issues before lower priority
- **Re-run after fixes**: Verify issues are resolved
- **Use specific reviews**: Target specific aspects when you know the concern

## Notes:

- Agents run autonomously and return detailed reports
- Each agent focuses on its specialty for deep analysis
- Results are actionable with specific file:line references
- Agents use appropriate models for their complexity