# Track Specification: Fix Android Animation Stalls (Regression)

## Overview
This track aims to diagnose and resolve a regression introduced in recent `2.7.14-internal` releases where animations (standard Compose progress indicators and custom transitions) fail to fire on Android. While these animations work correctly on Desktop, they are "stuck" or "stalled" on Android, likely due to threading issues or recomposition failures.

## Historical Context
- **Introduction**: This issue appeared during the `2.7.14-internal` release cycle.
- **Comparison**: Older versions or the current Desktop build can be used as references to identify code changes that might have triggered the regression.

## Functional Requirements
- **Animation Restoration**: Restore movement to indeterminate circular and linear progress bars, particularly on the Connections screen.
- **Transition Fixes**: Ensure `MeshActivity` animations (entry/exit/transitions) fire as expected.
- **Project-wide Audit**: Audit other screens for similar "stuck" animations.
- **KMP Parity**: Ensure shared `commonMain` code functions correctly on both Android and Desktop.

## Non-Functional Requirements
- **Performance**: Ensure no UI jank or excessive recompositions.
- **Verification**: Use historical code comparison (via `gh` or temporary copies) to isolate the breaking change.

## Acceptance Criteria
- [ ] Indeterminate progress bars on the Connections screen animate continuously.
- [ ] `MeshActivity` animations fire correctly.
- [ ] Root cause identified (Regression since 2.7.14-internal).
- [ ] Automated UI tests verify animation behavior on Android.
- [ ] Unit tests verify state flow if threading/ViewModels are involved.
