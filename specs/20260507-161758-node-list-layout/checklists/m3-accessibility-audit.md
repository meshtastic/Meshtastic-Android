# Material 3, Expressive & Accessibility Audit — Node List Layout

**Date**: 2026-05-09
**Scope**: `spec.md`, `plan.md`, `tasks.md` for feature `002-node-list-layout`
**Baseline**: Existing `NodeItem.kt`, `NodeListScreen.kt`, `NodeChip.kt`, `LoraSignalIndicator.kt`, `SwitchPreference.kt`, `SwitchListItem` in codebase

---

## 1. Material 3 Compliance

### 1.1 Component Selection

| Spec Reference | Specified Component | M3 Best Practice | Status | Finding |
|---|---|---|---|---|
| FR-001 | `SegmentedButton` | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` | ✅ CORRECT | Already used in codebase (`TimeFrameSelector`, `ChannelScreen`, `FirmwareUpdateScreen`). Correct M3 pattern for binary choice. |
| FR-003 | `Switch` composables | M3 `Switch` via `SwitchPreference` or `SwitchListItem` | ⚠️ UNDERSPEC | Spec says raw `Switch` composables, but codebase already has `SwitchListItem` and `SwitchPreference` wrappers in `core/ui` that provide proper M3 `ListItem` integration, `toggleable` semantics, and disabled-state alpha. **Tasks should use these wrappers, not raw `Switch`.** |
| FR-008 | Live preview | Card-based preview | ✅ OK | M3 `Card` with `CardDefaults` is the existing pattern per `NodeItem`. |
| Help sheet | `ModalBottomSheet` | M3 `ModalBottomSheet` | ✅ CORRECT | Existing codebase pattern (`NodeDetailScreens`, `MessageItem`, `DeviceList`). Use `rememberModalBottomSheetState(skipPartiallyExpanded = true)` consistently. |
| FR-009 | `NodeChip` (Card) | M3 `Card` with `MaterialTheme.shapes.small` | ✅ CORRECT | Existing `NodeChip` uses M3 `Card` + `CardDefaults`. |

### 1.2 Typography

| Usage | Current | M3 Expressive Best Practice | Status | Finding |
|---|---|---|---|---|
| Node long name | `titleMediumEmphasized` | Correct — M3 Expressive emphasized variant | ✅ CORRECT | `NodeItemHeader` line 423 uses `MaterialTheme.typography.titleMediumEmphasized`. New `NodeItemCompact` should match. |
| Settings section header | Not specified | `titleSmall` or `labelLarge` for section headers | ⚠️ UNDERSPEC | Spec does not specify typography for the "Node Layout" settings section header. Should use project-consistent heading style. |
| Toggle labels | Not specified | `bodyLarge` (M3 `ListItem` headline) | ⚠️ UNDERSPEC | Using `SwitchPreference`/`SwitchListItem` would auto-apply correct M3 `ListItem` typography. |
| Signal labels in Help | Not specified | `bodyMedium` for descriptions, `labelSmall` for signal values | ⚠️ UNDERSPEC | Should specify typography scale for help sheet entries. |

### 1.3 Color System

| Usage | Current | M3 Best Practice | Status | Finding |
|---|---|---|---|---|
| Signal quality colors | Custom `StatusColors` extension (`StatusGreen`, `StatusYellow`, `StatusOrange`, `StatusRed`) | Should use M3 semantic color tokens where possible | ⚠️ ACCEPTABLE | Custom signal colors are domain-specific and not representable by M3 semantic tokens alone. The existing `StatusColors` extension on `ColorScheme` is a reasonable approach — colors adapt to dark/light theme via the color scheme. No change needed. |
| Disabled toggle alpha | `enabled = false` on `Switch` | M3 specifies 38% content alpha for disabled states | ✅ HANDLED | `SwitchPreference` already applies 50% alpha to headline/supporting text when disabled (line 55-56). Close enough to M3 spec (38%). |
| Card container colors | Node-specific colors with alpha | Uses `contentColorFor()` for text contrast | ✅ CORRECT | `NodeItem` lines 126-137 properly derive `contentColor` from `containerColor`. |

### 1.4 Spacing & Layout

| Spec Reference | Specified Value | M3 Guidelines | Status | Finding |
|---|---|---|---|---|
| FR-026 | `spacedBy(2.dp)` compact rows | M3 recommends 4-8dp between related content | ⚠️ TIGHT | 2.dp is tighter than M3's recommended minimum of 4dp. Acceptable for a "compact" density variant where the explicit goal is minimizing space, but **should be noted as an intentional deviation**. |
| FR-027 | 2.dp top/bottom compact, 3.dp complete | M3 list items recommend 8-16dp vertical padding | ⚠️ TIGHT | Same rationale — compact mode intentionally trades padding for density. The existing `NodeItem` uses 12.dp padding (line 157), which is M3-conformant. The compact variant's 2.dp is a deliberate trade-off. **Should document this as an intentional density trade-off.** |
| FR-014 | `spacedBy(6.dp)` with `VerticalDivider(15.dp)` | M3 uses `VerticalDivider` height matching content, typically no explicit height | ⚠️ MINOR | Hardcoded 15.dp height for `VerticalDivider` is fragile if text size changes with accessibility scaling. Consider using `Modifier.height(IntrinsicSize.Min)` on the parent `Row` and `fillMaxHeight()` on dividers. |

### 1.5 M3 Expressive APIs In Use

The codebase already uses several M3 Expressive APIs (all behind `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`):

- `animateFloatingActionButton` — `NodeListScreen.kt` line 141
- `titleMediumEmphasized` typography — `NodeItem.kt` line 423
- M3 Expressive is CMP 1.11.0-alpha07 compatible

**Recommendation for new composables**:
- `NodeItemCompact` SHOULD use `titleMediumEmphasized` for the node name to match the complete variant.
- The density `SegmentedButton` in Settings could use `Expressive` shape tokens if available in the CMP M3 version.

---

## 2. Android Accessibility Audit

### 2.1 CRITICAL: Row-Level Semantics Missing in Existing `NodeItem`

**Finding**: The existing `NodeItem.kt` has **no `Modifier.semantics(mergeDescendants = true)`** on the outer `Card` or `Column`. Each child composable (`HopsInfo`, `DistanceInfo`, `MaterialBatteryInfo`, etc.) provides its own `contentDescription`, but they are **not merged into a single TalkBack announcement**.

**Impact**: TalkBack users hear each child element as a separate focusable item within a node row — potentially 8-12 focus stops per node. With 100+ nodes, this creates an overwhelming navigation experience.

**M3/Accessibility Best Practice**: List items should be a single focusable unit with a merged content description. Android accessibility guidelines recommend `mergeDescendants = true` for compound list items.

**Applies to**:
- Existing `NodeItem` (Complete layout) — **NL-T006 is HIGH priority**
- New `NodeItemCompact` — NL-T012 correctly specifies this

**Recommendation**: Add to both layouts:
```kotlin
Card(
    modifier = modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
            contentDescription = buildNodeDescription(...)
        },
    ...
)
```

### 2.2 Missing `clearAndSetSemantics` for Decorative Icons

**Finding**: Icons like the lock/key icon (`NodeKeyStatusIcon`), transport icon, and favorite star each provide their own `contentDescription`. In the merged row context, these should use `contentDescription = null` for purely decorative icons, or contribute to the merged description.

**Status**: ⚠️ UNDERSPEC — FR-025 says "full TalkBack coverage" but doesn't specify the merge strategy.

### 2.3 Missing `Role.Button` on Clickable Rows

**Finding**: `NodeItem` uses `combinedClickable` (line 157) but does not set `semantics { role = Role.Button }`. TalkBack users won't hear "double tap to activate" — they'll only hear the content description.

**Recommendation**: Add `role = Role.Button` in the semantics block, or wrap in an accessible clickable container.

### 2.4 Missing `stateDescription` for Toggle-Dependent Visibility

**Finding**: In compact mode, toggled-off fields simply disappear from the layout. TalkBack users navigating the Settings toggles won't get feedback about what's currently visible vs hidden in the node list.

**Recommendation**: Each `SwitchPreference`/`SwitchListItem` should include `stateDescription` ("Showing" / "Hidden") or use the M3 `Switch` built-in semantics (which `SwitchPreference` already provides via `toggleable`).

**Status**: ✅ HANDLED — `SwitchPreference` uses `Modifier.toggleable()` which auto-announces switch state. No additional work needed.

### 2.5 Missing Minimum Touch Target Sizes

**Finding**: The help button (?) referenced in NL-T037 should meet the minimum 48x48dp touch target per WCAG/Android guidelines. The spec does not specify touch target size.

**Recommendation**: Use `IconButton` (which has M3's built-in 48dp minimum) rather than a raw `Icon` with `clickable`.

### 2.6 Color-Only Information Conveyance

**Finding**: Signal quality (Good/Fair/Bad/None) uses color as the **primary** differentiator. This fails WCAG 1.4.1 "Use of Color" — color should not be the sole means of conveying information.

**Current mitigation**: The `Quality` enum uses different icons for each level (`ic_signal_cellular_4_bar`, `ic_signal_cellular_alt`, `_2_bar`, `_1_bar`), which provides a **shape-based redundant indicator**. ✅ This satisfies the guideline.

**Compact mode concern**: If the compact layout only shows a colored icon without the quality label text, TalkBack users lose the textual label. FR-017 says "The icon color MUST use the `Quality` enum" but doesn't guarantee the text label is part of the semantic description.

**Recommendation**: Ensure the compact signal icon includes `contentDescription = stringResource(quality.nameRes)` (e.g., "Signal: Good").

### 2.7 Dynamic Content & Live Regions

**Finding**: When density changes (Complete → Compact), the entire `LazyColumn` re-renders. TalkBack users may lose their scroll position or focus.

**Recommendation**: Consider announcing the density change via `liveRegion = LiveRegionMode.Polite` on the density status or list header, so TalkBack users are informed without losing context.

### 2.8 Font Scaling / Dynamic Type

**Finding**: The spec hardcodes `36.dp` minimum chip height and `70.dp` maximum. If the user has large font scaling enabled (200%+ on Android), the text inside the chip may clip.

**Recommendation**: Consider using `Modifier.defaultMinSize(minHeight = 36.dp)` combined with `wrapContentHeight()` to allow the chip to grow with font scaling, rather than a hard `size()` constraint.

### 2.9 Contrast Ratios

**Finding**: The `NodeChip` uses arbitrary node colors (`node.colors`) for container and text. There is no guarantee these meet WCAG AA 4.5:1 contrast ratio, especially for node-assigned colors that may be very light or very dark.

**Status**: Pre-existing issue, not introduced by this feature. But worth noting — `contentColorFor()` in `NodeItem` helps, but `NodeChip` doesn't use it (line 47 uses raw `Color(textColor)` from the node model).

---

## 3. Summary of Findings

### By Severity

| Severity | Count | IDs |
|----------|-------|-----|
| 🔴 CRITICAL | 1 | 2.1 (no row-level semantics merge) |
| 🟡 HIGH | 2 | 2.3 (missing Role.Button), 1.1 (use SwitchPreference not raw Switch) |
| 🟠 MEDIUM | 5 | 1.2 typography underspec (×2), 1.4 tight spacing, 1.4 VerticalDivider height, 2.6 compact signal a11y |
| 🔵 LOW | 4 | 2.5 touch targets, 2.7 live regions, 2.8 font scaling, 2.9 contrast |
| ✅ PASS | 7 | SegmentedButton, Card, ModalBottomSheet, Expressive typography, signal icons, color system, disabled state |

### Required Spec/Task Changes

| ID | Finding | Spec Impact | Task Impact |
|----|---------|-------------|-------------|
| 2.1 | Row-level semantics merge | Add to FR-025: "MUST use `semantics(mergeDescendants = true)` with a composed `contentDescription`" | **NL-T006** is HIGH priority — fixes existing `NodeItem`. NL-T012 covers `NodeItemCompact`. Both layouts need a `buildNodeDescription()` function. |
| 2.3 | Missing `Role.Button` | Add to FR-025: "clickable rows MUST declare `role = Role.Button`" | Add to NL-T006 and NL-T012 |
| 1.1 | Use `SwitchPreference`/`SwitchListItem` | Update FR-003: change "9 toggles (`Switch` composables)" → "9 toggles using `SwitchPreference` from `core:ui`" | Update NL-T028 to reference `SwitchPreference` |
| 1.4 | VerticalDivider fragility | Update FR-014: note that parent Row should use `IntrinsicSize.Min` | Add note to NL-T020 |
| 2.6 | Compact signal contentDescription | Add to FR-017: "Signal icon MUST include `contentDescription` with quality level name" | Add to NL-T023 |
| 2.8 | Font scaling | Update FR-011: use `defaultMinSize` not hard `size` | Add note to NL-T032 |

### Recommended Additions (Non-Blocking)

| Finding | Recommendation |
|---------|---------------|
| 1.4 spacing | Document 2.dp/3.dp padding as intentional density deviation from M3 8-16dp recommendation |
| 2.5 touch targets | NL-T037: specify `IconButton` for help button |
| 2.7 live regions | Consider `LiveRegionMode.Polite` announcement on density change |
| 2.9 contrast | Separate issue — audit `NodeChip` color contrast across all node color assignments |

