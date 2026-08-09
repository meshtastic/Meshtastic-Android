# Skill: Spec Kit (Specification-Driven Development)

## Description

Spec Kit is the project's specification-driven development (SDD) workflow. It takes a natural-language
feature description through a structured pipeline that produces specs, plans, tasks, and implementation
— with review gates, constitution alignment, and automated git hooks at each stage.

## When to Use

- Starting a **new feature** that needs specification before implementation.
- **Refining** an existing feature spec after user feedback or clarification.
- **Analyzing** cross-artifact consistency before implementation begins.
- **Generating tasks** from an approved plan.
- **Implementing** a fully-specified feature with task tracking.
- Converting tasks to **GitHub Issues** for project management.

## Available Commands

### Core Workflow (in order)

| Command | Slash Command | Purpose |
|---------|--------------|---------|
| **Specify** | `/speckit.specify` | Create or update `spec.md` from a feature description |
| **Clarify** | `/speckit.clarify` | Ask up to 5 targeted clarification questions, encode answers into spec |
| **Plan** | `/speckit.plan` | Generate `plan.md` with architecture, phases, data models |
| **Tasks** | `/speckit.tasks` | Generate `tasks.md` with dependency-ordered, parallelizable tasks |
| **Analyze** | `/speckit.analyze` | Read-only cross-artifact consistency and quality analysis |
| **Implement** | `/speckit.implement` | Execute tasks from `tasks.md` with status tracking |

### Supporting Commands

| Command | Slash Command | Purpose |
|---------|--------------|---------|
| **Checklist** | `/speckit.checklist` | Generate a custom quality checklist for the feature |
| **Constitution** | `/speckit.constitution` | Create or update project constitution (`.specify/memory/constitution.md`) |
| **Tasks to Issues** | `/speckit.taskstoissues` | Convert `tasks.md` into GitHub Issues |

### Git Extension Commands

| Command | Slash Command | Purpose |
|---------|--------------|---------|
| **Git Initialize** | `/speckit.git.initialize` | Initialize git repo (skips if already initialized) |
| **Git Feature** | `/speckit.git.feature` | Create a feature branch with sequential numbering |
| **Git Commit** | `/speckit.git.commit` | Auto-commit changes after a Spec Kit command |
| **Git Remote** | `/speckit.git.remote` | Detect git remote URL for GitHub integration |
| **Git Validate** | `/speckit.git.validate` | Validate branch follows feature naming conventions |

## Full Workflow (End-to-End)

The standard SDD cycle for a new feature:

```text
1. /speckit.specify "Feature description here"
   → Creates specs/<YYYYMMDD-HHMMSS>-feature-name/spec.md
   → Auto-creates feature branch via git hook

2. /speckit.clarify
   → Asks clarification questions, encodes answers into spec.md

3. /speckit.plan
   → Generates plan.md with architecture, phases, data model

4. /speckit.tasks
   → Generates tasks.md with phased, dependency-ordered tasks

5. /speckit.analyze
   → Read-only quality analysis (constitution alignment, coverage gaps)
   → Fix any CRITICAL/HIGH findings before proceeding

6. /speckit.implement
   → Executes tasks with status tracking
   → Auto-commits after each phase
```

## Automated Workflow

The full cycle can also run as a single workflow with review gates:

```text
/speckit.workflow speckit "Describe the feature"
```

This runs: specify → (review gate) → plan → (review gate) → tasks → implement

## File Structure

Spec Kit produces files under `specs/<YYYYMMDD-HHMMSS>-feature-name/`:

```
specs/
└── 20260513-160000-feature-name/
    ├── spec.md              # Feature specification (FRs, NFRs, SCs, user stories)
    ├── plan.md              # Implementation plan (architecture, phases)
    ├── tasks.md             # Dependency-ordered task list
    ├── data-model.md        # Entity definitions and schemas
    ├── research.md          # Technical decisions and alternatives
    ├── quickstart.md        # Getting started guide
    ├── checklists/
    │   └── requirements.md  # Quality checklist
    └── contracts/
        ├── deep-links.md    # Deep link contract
        └── *.json           # Schema contracts
```

## Constitution

The project constitution at `.specify/memory/constitution.md` defines non-negotiable principles.
All specs, plans, and tasks are validated against it during `/speckit.analyze`.

Current constitution (v1.3.3) enforces 7 principles:

1. **KMP Core** — Business logic in `commonMain` only
2. **Zero Lint Tolerance** — `spotlessCheck` + `detekt` must pass
3. **Compose Multiplatform UI** — CMP, not Android-only Compose
4. **Privacy First** — No PII/location/key exposure
5. **Design Standards Compliance** — Review against Meshtastic design standards; cross-platform features must reference an upstream spec from `meshtastic/design/features/`
6. **Documentation Freshness** — User-facing changes update `docs/` (in-app browser, Jekyll, Docusaurus) with `last_updated` frontmatter; blocking CI gate
7. **Verify Before Push** — Local verification before any `git push`

## Extension Hooks

Git hooks are configured in `.specify/extensions.yml` and run automatically:

- **Before** each command: Optional commit hook (commit outstanding changes)
- **After** each command: Optional commit hook (commit generated artifacts)
- **Before specify**: Mandatory feature branch creation

## Meshtastic-Specific Conventions

### Branch Naming

Feature branches created by `/speckit.git.feature` use timestamp-based numbering:
`YYYYMMDD-HHMMSS-feature-name` (e.g., `20260511-211823-compose-screenshot-testing`)

This avoids merge conflicts when multiple specs are developed on parallel branches.

Non-spec branches follow conventional commit-style prefixes:
`feat/`, `fix/`, `chore/`, `docs/`, `build/`, `ci/`, `refactor/`, `test/`, `deps/`

### Task ID Namespacing

To avoid collision when multiple specs exist, prefix task IDs with a short feature mnemonic
(e.g., `SST-T001` for screenshot testing, `DISC-T001` for discovery). The prefix is defined
per-spec in the tasks.md header.

### Design Standards Gate

All specs with UI work must include a Phase 0 `[UI-GATE]` blocking task that reviews
the Meshtastic design standards before implementation begins. The `/speckit.analyze`
command flags missing gates as CRITICAL.

### Deep Link Convention

All deep links must use the canonical URI scheme: `meshtastic://meshtastic/settings/<path>`.
Compatibility aliases with camelCase or hyphenated variants are acceptable but must not be
the primary contract.

## Tips

- Run `/speckit.analyze` before `/speckit.implement` — it catches constitution violations,
  coverage gaps, and cross-artifact inconsistencies.
- Use `/speckit.clarify` when specs feel underspecified — it asks targeted questions and
  encodes answers directly into the spec.
- The `/speckit.checklist` command generates feature-specific quality gates beyond the
  standard code review checklist.
- All commands support passing arguments: `/speckit.specify "description"`.
- Git hooks are optional by default (you'll be prompted). Set `auto_execute_hooks: true`
  in `.specify/extensions.yml` to skip prompts.

## Existing Specs

Specs live in `specs/`, one directory per feature (`<YYYYMMDD-HHMMSS>-<slug>`; two legacy dirs use `NNN-<slug>` numbering). List that directory for the live inventory — each spec's `spec.md` / `tasks.md` records its own scope and status.

## Related Skills

- `implement-feature` — Feature implementation workflow (post-spec)
- `code-review` — PR review checklist
- `testing-ci` — CI validation commands
- `new-branch` — Branch bootstrap recipes
