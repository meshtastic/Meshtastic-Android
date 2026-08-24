# Tasks: M3 Expressive Design System Adoption

**Input**: Design documents from `/specs/20260513-160000-m3-expressive-adoption/`
**Status**: Implementation Complete
**Constraint**: Only native M3 library APIs — zero self-rolled components

---

## Completed Work

### Phase 1: Theme & Typography Foundation

- [X] T001 `MaterialExpressiveTheme` with `MotionScheme.expressive()` at root level
- [X] T002 Expressive typescale with 16sp body minimum; emphasized variants auto-generated
- [X] T003 `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` across all consuming files
- [X] T004 Replace hardcoded `TextStyle` with `MaterialTheme.typography.*` references (audit pass)

### Phase 2: Native Component Migration

- [X] T010 Migrate `ListItem` wrapper to M3 interactive `ListItem(onClick=...)` overload
- [X] T011 Migrate `RegularPreference` to M3 interactive `ListItem` with slots
- [X] T012 Migrate `SwitchPreference` to M3 toggleable `ListItem(checked=..., onCheckedChange=...)`
- [X] T013 Replace `BottomSheetDialog` with native `ModalBottomSheet` (EmojiPicker + Reaction)
- [X] T014 Replace `IconToggleButton` with `OutlinedIconToggleButton` (favorite toggle)
- [X] T015 Adopt `DropdownMenuGroup` for grouped menu items
- [X] T016 Replace `CircularProgressIndicator` with `CircularWavyProgressIndicator` (SwitchPreference)
- [X] T017 Apply `headlineSmallEmphasized` to dialog titles (AlertDialogs)
- [X] T018 Apply `titleLargeEmphasized` to app bar title (MainAppBar)
- [X] T019 Apply `titleMediumEmphasized` to channel names (ChannelItem)
- [X] T020 Apply expressive shape (28dp rounded) to dialogs

### Phase 3: M3 Disabled/Color Delegation

- [X] T021 Use `Color.Unspecified` defaults in ListItem so M3 disabled tokens flow through
- [X] T022 Remove manual disabled color in RegularPreference — delegate to M3
- [X] T023 Resolve deprecated `ListItemColors.copy()` — use `ListItemDefaults.colors()` directly
- [X] T024 `icon()` helper resolves Unspecified → LocalContentColor.current at render time

### Phase 4: Shape & Divider Compatibility

- [X] T025 Override resting shape to `RectangleShape` via `ListItemDefaults.shapes()` in BasicListItem
- [X] T026 Apply RectangleShape to SwitchPreference and RegularPreference
- [X] T027 Preserve press/hover/focus shape morphing animations (only idle state rectangular)

### Phase 5: Dead Code Elimination

- [X] T028 Delete `ClickableTextField` (zero callers)
- [X] T029 Delete `TimeTickWithLifecycle` expect + 3 actuals (zero callers)
- [X] T030 Delete `BottomSheetDialog` (zero callers after ModalBottomSheet migration)

### Phase 6: Navigation State Preservation

- [X] T031 Restore `CurrentTabSaver` with `rememberSaveable` for process-death tab persistence
- [X] T032 Route graph refactoring (Graph + concrete route pairs)

### Phase 7: Dependency Fixes

- [X] T033 Restore markdownRenderer to 0.41.0 (rebase conflict resolution error)

### Phase 8: Validation

- [X] T034 Screenshot test reference images updated
- [X] T035 Full verification: `spotlessApply detekt assembleDebug test allTests` — BUILD SUCCESSFUL
- [X] T036 Self-review in fix-first mode — 3 issues auto-fixed, 0 unresolved

---

## Reverted Work (violated zero-custom-component constraint)

| Task | What | Why Reverted |
|------|------|-------------|
| SwipeToRevealBox | Custom swipe-to-action component | No native M3 API exists |
| expressiveFocusIndication | Custom animated focus rings | No native M3 focus indication API |
| LocalReduceMotion | Custom reduce-motion CompositionLocal | M3 MotionScheme already handles natively |

---

## Future Follow-ups (not in this PR)

| Item | Scope | Notes |
|------|-------|-------|
| Remove HorizontalDividers | ~176 usages across 40 files | M3 list items don't need dividers with shape; large scope |
| Migrate remaining CircularProgressIndicator | 3 sites (IndoorAirQuality, LoadingOverlay, TAKConfigItemList) | Low priority |
| CircularWavyProgressIndicator in FirmwareUpdateScreen | Already done in earlier commit | ✅ |
| SwipeToRevealBox | Waiting for native M3 API | Adopt when CMP ships it |
| Animated focus rings | Waiting for native M3 API | Adopt when CMP ships it |
