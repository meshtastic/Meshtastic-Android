# Implementation Plan: Fix Android Animation Stalls

## Phase 1: Research and Reproduction
- [x] Task: Historical Regression Analysis
    - [x] Compare current code with pre-2.7.14-internal versions to identify changes in threading or UI state management.
    - [x] Check `gh` history for commits related to `ConnectionsScreen` and `MeshActivity` transitions.
- [x] Task: Reproduction and Diagnosis
    - [x] Create a reproduction case (manual or automated) that consistently shows stalled progress bars on Android.
    - [x] Inspect Recomposition counts using Layout Inspector or logging.
    - [x] Verify Coroutine Dispatchers used for UI state updates.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Research and Reproduction' (Protocol in workflow.md)

## Phase 2: Fix Implementation
- [x] Task: Core Animation Fix
    - [x] Apply fix to resolve threading/recomposition stalls (e.g., correct `Dispatcher.Main` usage or state hoisting).
    - [x] Verify progress bars on Connections screen are animating.
- [x] Task: MeshActivity Transition Fix
    - [x] Fix animation firing for `MeshActivity` entries and exits.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Fix Implementation' (Protocol in workflow.md)

## Phase 3: Project-wide Audit and Final Verification
- [x] Task: Audit App Animations
    - [x] Scan other screens for similar animation stalls and apply fixes where necessary.
- [x] Task: Automated Testing
    - [x] Write/Update Compose UI tests to ensure animations are running on Android.
    - [x] Verify no regressions on Desktop.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Project-wide Audit and Final Verification' (Protocol in workflow.md)
