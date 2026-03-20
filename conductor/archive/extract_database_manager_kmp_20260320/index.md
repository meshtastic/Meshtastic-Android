# Track: Extract DatabaseManager to KMP

## Documents
- [Specification](./spec.md)
- [Implementation Plan](./plan.md)
- [Metadata](./metadata.json)

## Context
Meshtastic-Android is designed to support per-node databases. Currently, the logic for managing these databases is in `androidMain`, and the desktop module stubs this out, which leads to a lack of feature parity. This track aims to extract that logic into `commonMain`.
