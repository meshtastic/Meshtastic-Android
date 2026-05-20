# Data Model: UDP Label Rename

**Feature**: UDP Label Rename  
**Date**: 2025-05-20

## Overview

This feature modifies no data models, entities, or state transitions. The only artifact is a string resource value change.

## String Resource

| Key | Current Value | New Value | File |
|-----|---------------|-----------|------|
| `udp_enabled` | `Enabled` | `UDP broadcasting` | `core/resources/src/commonMain/composeResources/values/strings.xml` |

## Entity Impact

None. No database entities, protobuf messages, or domain models are affected.

## State Transitions

None. The toggle's functional behavior (enabling/disabling UDP broadcasting via `Config.NetworkConfig.ProtocolFlags.UDP_BROADCAST`) is unchanged.

## Validation Rules

- The string value MUST NOT be empty
- The string key `udp_enabled` MUST remain unchanged (preserves Crowdin translation mappings)
- The new value "UDP broadcasting" MUST be ≤40 characters to avoid truncation on standard device widths (it is 16 characters — well within limits)
