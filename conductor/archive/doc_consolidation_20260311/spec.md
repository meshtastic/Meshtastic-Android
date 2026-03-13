# Track Specification: Implement document consolidation plan

## Objective
Consolidate, prune, verify, and validate project plans and documentation against 2026 Kotlin Multiplatform (KMP) best practices and the latest dependency standards.

## Background & Motivation
The `docs/agent-playbooks/` directory has accumulated numerous point-in-time session summaries, checklists, and status reports (e.g., `SESSION-FINAL-SUMMARY.md`, `TEST-VERIFICATION-REPORT.md`) during the Phase 3 testing consolidation sprint. These files clutter the directory and dilute the actual "playbooks" (reusable guides). Additionally, the project documentation (`kmp-status.md`, `roadmap.md`, `AGENTS.md`) needs to be synthesized to reflect the recently completed work and validated against 2026 KMP industry standards (e.g., Koin K2 compiler plugin best practices, shared ViewModels, Navigation 3).

## Scope
1. **Prune and Consolidate Session Artifacts:** Merge the key findings into a single historical record (`docs/archive/kmp-phase3-testing-consolidation.md`) and delete 12+ redundant point-in-time files. Relocate `phase-4-desktop-completion-plan.md` into `docs/roadmap.md` and move `kmp-feature-migration-plan.md` to `docs/archive/`.
2. **Synthesize Status & Roadmap:** Update `docs/kmp-status.md` and `docs/roadmap.md` with new testing metrics (80 tests across 6 features) and expanded Phase 4 Desktop tasks.
3. **Verify and Validate against 2026 KMP Best Practices:** Validate the usage of Koin `@Module` and `@KoinViewModel` annotations in `commonMain` according to Koin 4.2 native compiler plugin best practices. Update `AGENTS.md` and `di-navigation3-anti-patterns-playbook.md` to officially recommend this pattern and multiplatform `androidx.lifecycle.ViewModel` in `commonMain`.
4. **Documentation Quality Checks:** Verify `README.md` in playbooks correctly points to retained playbooks. Rename `testing-quick-ref.sh` to `testing-quick-ref.md` and update internal references.