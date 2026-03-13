# Objective
Consolidate, prune, verify, and validate project plans and documentation against 2026 Kotlin Multiplatform (KMP) best practices and the latest dependency standards.

# Background & Motivation
The `docs/agent-playbooks/` directory has accumulated numerous point-in-time session summaries, checklists, and status reports (e.g., `SESSION-FINAL-SUMMARY.md`, `TEST-VERIFICATION-REPORT.md`) during the Phase 3 testing consolidation sprint. These files clutter the directory and dilute the actual "playbooks" (reusable guides). Additionally, the project documentation (`kmp-status.md`, `roadmap.md`, `AGENTS.md`) needs to be synthesized to reflect the recently completed work and validated against 2026 KMP industry standards (e.g., Koin K2 compiler plugin best practices, shared ViewModels, Navigation 3).

# Proposed Solution

## 1. Prune and Consolidate Session Artifacts
- **Consolidate:** Merge the key findings, test coverage metrics (80 tests across 6 features), and testing patterns from the 12+ session update files into a single historical record: `docs/archive/kmp-phase3-testing-consolidation.md`.
- **Prune:** Delete the following redundant point-in-time files from `docs/agent-playbooks/`:
  - `CHECKLIST-testing-consolidation.md`
  - `FINAL-STATUS-tests-fixed.md`
  - `MIGRATION-COMPLETE-SUMMARY.md`
  - `SESSION-FINAL-SUMMARY.md`
  - `SESSION-STATUS-2026-03-11.md`
  - `TEST-VERIFICATION-REPORT.md`
  - `fix-core-domain-tests.md`
  - `kmp-testing-consolidation-slice.md`
  - `phase-1-feature-commontest-bootstrap.md`
  - `phase-3-completion.md`
  - `phase-3-implementation-plan.md`
  - `phase-3-integration-tests-started.md`
- **Relocate:** 
  - Extract the contents of `phase-4-desktop-completion-plan.md` and merge them into `docs/roadmap.md` under the Phase 4 Desktop section. Delete the original file.
  - Move `kmp-feature-migration-plan.md` to `docs/archive/` since Phase 3 is mostly complete.

## 2. Synthesize Status & Roadmap
- **Update `docs/kmp-status.md`:** Update the testing score (currently 5/10) to reflect the completion of Phase 3 integration testing (80 tests across 6 features, test doubles in `core:testing`).
- **Update `docs/roadmap.md`:** Mark Phase 3 as substantially complete. Expand the Phase 4 (Desktop Feature Completion) section using the consolidated plan details.

## 3. Verify and Validate against 2026 KMP Best Practices
Based on a review of 2026 KMP standards and the project's current dependencies (`Koin 4.2.0-RC1`, `Compose Multiplatform 1.11.0-alpha03`, `Navigation 3 1.1.0-alpha03`):
- **Koin Annotations (K2):** The project's decision to move Koin `@Module` and `@KoinViewModel` annotations into `commonMain` aligns perfectly with Koin 4.2 native compiler plugin best practices. The documentation (`AGENTS.md`, `docs/decisions/architecture-review-2026-03.md`) will be validated and explicitly updated to affirm that this is the correct architectural pattern, not a "portability tradeoff".
- **Shared ViewModels (MVI):** Ensure playbook documentation explicitly recommends utilizing the multiplatform `androidx.lifecycle.ViewModel` in `commonMain` to maintain a single source of truth, heavily relying on `StateFlow`.
- **Navigation 3:** The hybrid parity strategy (shared route contracts, platform adapters) is validated as the 2026 standard for Compose Multiplatform.

## 4. Documentation Quality Checks
- Verify `docs/agent-playbooks/README.md` correctly points only to the retained playbooks.
- Rename `testing-quick-ref.sh` to `testing-quick-ref.md` for proper markdown rendering and update internal references.

# Implementation Steps
1. Create `docs/archive/kmp-phase3-testing-consolidation.md` and synthesize the 12+ session artifacts into it.
2. Delete the 12+ redundant session files from `docs/agent-playbooks/`.
3. Update `docs/kmp-status.md` and `docs/roadmap.md` with the new testing metrics and Phase 4 desktop tasks.
4. Rename `testing-quick-ref.sh` to `testing-quick-ref.md` and update internal references.
5. Update `docs/agent-playbooks/README.md` to reflect the pruned directory.
6. Refine `AGENTS.md` and `docs/agent-playbooks/di-navigation3-anti-patterns-playbook.md` to validate Koin K2 multiplatform annotations as the officially recommended pattern.

# Verification & Testing
- Run `ls docs/agent-playbooks/` to ensure only high-signal playbooks remain.
- Ensure `docs/kmp-status.md` reflects an updated test maturity score (e.g., 8/10).
- Run `git status` and `git diff` to ensure changes are accurate.