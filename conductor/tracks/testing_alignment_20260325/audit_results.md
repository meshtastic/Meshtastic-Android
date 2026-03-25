# Audit Results: core:testing Gaps

## Existing Fakes
- `FakeMeshLogPrefs`
- `FakeMeshLogRepository`
- `FakeMessagingRepositories`
- `FakeNodeRepository` (Partial, needs more methods)
- `FakeRadioController`
- `FakeServiceRepository`
- `TestDataFactory`

## Identified Gaps (High Priority)
1. **`FakeAppPreferences`**: Critical for any module using preferences. Needs to consolidate:
    - `FakeAnalyticsPrefs`
    - `FakeHomoglyphPrefs`
    - `FakeFilterPrefs`
    - `FakeCustomEmojiPrefs`
    - `FakeUiPrefs`
    - `FakeMapPrefs`
    - `FakeMapConsentPrefs`
    - `FakeMapTileProviderPrefs`
    - `FakeRadioPrefs`
    - `FakeMeshPrefs`
2. **`FakeLocationRepository`**: Needed for `core:domain` and `feature:map`.
3. **`FakeMeshService`**: Needed for testing service interactions.
4. **`FakeRadioInterface`**: Needed for `core:network` and `core:data`.
5. **`FakePacketRepository`**: Needed for messaging and history.
6. **`FakeNodeManager`**: Needed for node database operations.
7. **`FakeRadioConfigRepository`**: Needed for radio configuration.

## Missing Utilities
- `BaseFake`: A utility for consistent state management (e.g., reset, common Flow patterns).
