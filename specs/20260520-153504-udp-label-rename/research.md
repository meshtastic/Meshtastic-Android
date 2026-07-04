# Research: UDP Label Rename

**Feature**: UDP Label Rename  
**Date**: 2025-05-20  
**Status**: Complete — no unknowns to resolve

## Summary

This feature has no technical unknowns. The change is a single string resource value modification in an existing XML file. No new dependencies, patterns, or architectural decisions are required.

## Research Items

### R-001: String Resource Location (Resolved)

- **Decision**: Modify `core/resources/src/commonMain/composeResources/values/strings.xml`, line 1304
- **Rationale**: This is the canonical location for all shared string resources in the KMP project. The `udp_enabled` key already exists with value "Enabled"; only the value changes to "UDP broadcasting"
- **Alternatives considered**: None — the string key is already established and referenced by `NetworkConfigItemList.kt`

### R-002: Post-Edit Script Requirement (Resolved)

- **Decision**: Run `python3 scripts/sort-strings.py` after the edit
- **Rationale**: Constitution (Development Workflow section) mandates running the sort script after any string resource modification to maintain alphabetical ordering and regenerate `strings-index.txt`
- **Alternatives considered**: None — this is a mandatory workflow step

### R-003: Build Verification (Resolved)

- **Decision**: Full build verification with `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
- **Rationale**: Constitution principle VII (Verify Before Push) requires local verification of all touched modules. Since strings.xml is consumed by all UI modules, a full build is appropriate
- **Alternatives considered**: Module-scoped `:core:resources:allTests` — rejected because the string is consumed transitively by multiple feature modules

## Conclusion

All technical context is fully resolved. No NEEDS CLARIFICATION items remain. Proceed directly to Phase 1 design.
