# Modifications from Upstream Meshtastic-Android

Per GPL-3.0 §5(a), the following modifications have been made to the
upstream Meshtastic Android codebase:

- Renamed application to "KV Field Console"
- Changed application ID and package name
- Replaced launcher icon, splash screen, and color palette with Krath Veil brand identity
- Replaced typography with Georgia (headlines) and Carlito (body)
- Forced dark mode as the only theme
- Simplified bottom navigation to three destinations: Nodes, Connections, Settings
- Trimmed Settings screen items to a demo-appropriate subset
- Updated About screen to reflect fork attribution and source URL

No modifications have been made to:
- Mesh protocol code
- Packet handling
- BLE/USB transport
- Radio configuration logic
- Any cryptographic or networking primitives
