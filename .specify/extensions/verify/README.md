# Spec-Kit Verify Extension

Post-implementation quality gate that validates implemented code against specification artifacts.

## Features

- **Implementation verification**: Checks implemented code against spec, plan, tasks, and constitution to catch gaps before review
- **Actionable report**: Produces a verification report with findings, metrics, and next actions
- **Configurable**: Adjust report size limits
- **Automatic hook**: Optional post-implementation prompt after `/speckit.implement`
- **Read-only & idempotent**: Never modifies source files or artifacts; repeated runs produce the same report

## Installation

```bash
specify extension add verify
```

Or install from repository directly:

```bash
specify extension add verify --from https://github.com/ismaelJimenez/spec-kit-verify/archive/refs/tags/v1.0.3.zip
```

For local development:

```bash
specify extension add --dev /path/to/spec-kit-verify
```

## Configuration

1. Create configuration file:

   ```bash
   cp .specify/extensions/verify/config-template.yml \
     .specify/extensions/verify/verify-config.yml
   ```

2. Edit configuration:

   ```bash
   vim .specify/extensions/verify/verify-config.yml
   ```

3. Customize as needed:

   ```yaml
   # Limit report size
   report:
     max_findings: 30
   ```

## Usage

### Command: verify

Validate implemented code against specification artifacts.

```text
# In Claude Code
> /speckit.verify.run
```

**Prerequisites:**

- Spec Kit >= 0.1.0
- Completed `/speckit.implement` run
- `spec.md` and `tasks.md` present in the feature directory
- At least one completed task in `tasks.md`

**Output:**

- Verification report with findings, metrics, and next actions
- Optional remediation suggestions on request

### Automatic Hook

If the `after_implement` hook is enabled, you'll be prompted automatically after `/speckit.implement` completes:

> Run verify to validate implementation against specification?

## Configuration Reference

### Report Settings

| Setting | Type | Required | Description |
|---------|------|----------|-------------|
| `report.max_findings` | integer | No | Maximum findings in the report (default: `50`) |

## Environment Variables

This extension does not currently support environment variable overrides. All configuration is managed through `verify-config.yml`.

## Examples

### Example 1: Basic Verification

```text
# Step 1: Create specification
> /speckit.specify

# Step 2: Plan and generate tasks
> /speckit.plan
> /speckit.tasks

# Step 3: Implement
> /speckit.implement

# Step 4: Verify implementation
> /speckit.verify.run
```

The verify command produces a report like:

```markdown
## Verification Report

| ID | Category | Severity | Location(s) | Summary | Recommendation |
|----|----------|----------|-------------|---------|----------------|
| A1 | Task Completion | LOW | tasks.md | 1 of 12 tasks incomplete | Complete task T08 |
| C1 | Requirement Coverage | CRITICAL | spec.md:FR-003 | No implementation evidence | Implement FR-003 |
| D1 | Scenario & Test Coverage | HIGH | spec.md:SC-02 | No test for login failure | Add test for scenario SC-02 |

Metrics: Tasks 11/12 · Requirement Coverage 92% · Files Verified 8 · Critical Issues 1
```

## What It Does

The verify command analyzes implemented code against specification artifacts:

1. Loads feature artifacts (spec.md, plan.md, tasks.md, constitution.md)
2. Identifies implementation scope from completed tasks
3. Runs verification checks across seven categories
4. Produces a report with findings, metrics, and next actions

### Verification Checks

| Check | What it verifies |
|-------|------------------|
| Task Completion | All tasks marked complete |
| File Existence | Task-referenced files exist on disk |
| Requirement Coverage | Every requirement has implementation evidence |
| Scenario & Test Coverage | Spec scenarios covered by tests or code paths |
| Spec Intent Alignment | Implementation matches spec intent and acceptance criteria |
| Constitution Alignment | Constitution principles are respected |
| Design & Structure Consistency | Architecture and conventions match plan.md |

## Workflow Integration

```
/speckit.specify → /speckit.plan → /speckit.tasks → /speckit.implement → /speckit.verify.run
```

## Operating Principles

- **Read-only**: Never modifies source files, tasks, or spec artifacts
- **Spec-driven**: All findings trace back to specification artifacts
- **Constitution authority**: Constitution violations are always CRITICAL
- **Idempotent**: Multiple runs on the same state produce the same report

## Troubleshooting

### Issue: Configuration not found

**Solution:** Create config from template (see [Configuration](#configuration) section):

```bash
cp .specify/extensions/verify/config-template.yml \
  .specify/extensions/verify/verify-config.yml
```

### Issue: Command not available

**Solutions:**

1. Check extension is installed: `specify extension list`
2. Restart AI agent
3. Reinstall extension: `specify extension add verify`

### Issue: "No completed tasks" error

**Solution:** Run `/speckit.implement` first. The verify command requires at least one completed task (`[x]`) in `tasks.md`.

### Issue: "Missing spec.md" error

**Solution:** Run `/speckit.specify` to create the specification before verifying. Both `spec.md` and `tasks.md` must exist in the feature directory.

## License

MIT License - see [LICENSE](LICENSE) file

## Support

- Issues: [https://github.com/ismaelJimenez/spec-kit-verify/issues](https://github.com/ismaelJimenez/spec-kit-verify/issues)
- Spec Kit Docs: [https://github.com/github/spec-kit](https://github.com/github/spec-kit)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history.

Extension Version: 1.0.3 · Spec Kit: >=0.1.0
