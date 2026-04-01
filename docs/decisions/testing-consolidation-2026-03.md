<!--
 - Copyright (c) 2026 Meshtastic LLC
 -
 - This program is free software: you can redistribute it and/or modify
 - it under the terms of the GNU General Public License as published by
 - the Free Software Foundation, either version 3 of the License, or
 - (at your option) any later version.
 -
 - This program is distributed in the hope that it will be useful,
 - but WITHOUT ANY WARRANTY; without even the implied warranty of
 - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 - GNU General Public License for more details.
 -
 - You should have received a copy of the GNU General Public License
 - along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

# Decision: Testing Consolidation — `core:testing` Module

**Date:** 2026-03-11
**Status:** Implemented

## Context

Each KMP module independently declared scattered test dependencies (`junit`, `mockk`, `coroutines-test`, `turbine`), leading to version drift and duplicated test doubles across modules.

## Decision

Created `core:testing` as a lightweight shared module for test doubles, fakes, and utilities. It depends only on `core:model` and `core:repository` (no heavy deps like `core:database`). All modules declare `implementation(projects.core.testing)` in `commonTest` to get a unified test dependency set.

## Consequences

- **Single source** for test fakes (`FakeRadioController`, `FakeNodeRepository`, `TestDataFactory`)
- **Clean dependency graph** — `core:testing` is lightweight; heavy modules depend on it in test scope, not vice versa
- **No production leakage** — only declared in `commonTest`, never in release artifacts
- **Reduced maintenance** — updating test libraries touches one `build.gradle.kts`

See [`core/testing/README.md`](../../core/testing/README.md) for usage guide and API reference.
