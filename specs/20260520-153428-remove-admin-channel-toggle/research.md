# Research: Remove Admin Channel Enabled Toggle

## Summary

This feature has no unresolved unknowns. The technical context is fully specified by the feature spec and existing codebase analysis.

## Findings

### 1. Admin Channel Enabled Toggle Location

- **Decision**: Remove lines 207–214 in `SecurityConfigScreen.kt` (HorizontalDivider + SwitchPreference block)
- **Rationale**: Confirmed via code inspection — these are the only lines rendering the `admin_channel_enabled` toggle
- **Alternatives considered**: None — this is the only location

### 2. Unused Import Cleanup

- **Decision**: Remove line 49 (`import org.meshtastic.core.resources.legacy_admin_channel`)
- **Rationale**: After removing the SwitchPreference that uses `Res.string.legacy_admin_channel`, this import becomes unused. Leaving it would fail lint checks.
- **Alternatives considered**: Leave import (rejected — violates Zero Lint Tolerance principle)

### 3. Proto Field Preservation

- **Decision**: Do NOT modify `admin_channel_enabled` proto field
- **Rationale**: Per spec non-goals and Constitution §IV (Privacy First / read-only proto submodule). The field remains for firmware communication and backward compatibility.
- **Alternatives considered**: Full removal including proto (rejected — requires upstream proto change, out of scope)

### 4. String Resource Preservation

- **Decision**: Leave `legacy_admin_channel` string resources intact
- **Rationale**: String resources are Crowdin-managed. Removal is a separate cleanup task. No lint violation from unused string resources.
- **Alternatives considered**: Remove strings (rejected — Crowdin-managed, separate concern)

### 5. Verification Strategy

- **Decision**: Run `./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm`
- **Rationale**: Covers all KMP targets for the settings module. `compileKotlinJvm` ensures desktop target compiles correctly after the removal.
- **Alternatives considered**: Full `assembleDebug` (not needed — change is isolated to one module)

## No NEEDS CLARIFICATION Items

All technical context was resolved from spec + codebase inspection. No external research required.
