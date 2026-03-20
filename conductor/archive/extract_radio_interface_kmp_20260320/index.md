# Track: Extract RadioInterfaceService to KMP

## Documents
- [Specification](./spec.md)
- [Implementation Plan](./plan.md)
- [Metadata](./metadata.json)

## Context
Meshtastic-Android and Desktop orchestrate their hardware connections (TCP, Serial, BLE) independently using `AndroidRadioInterfaceService` and `DesktopRadioInterfaceService`. This duplicates complex logic like reconnect loops and state emission. This track aims to unify that logic into `commonMain`.
