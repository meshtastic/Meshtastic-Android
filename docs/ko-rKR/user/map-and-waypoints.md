---
title: Map & Waypoints
parent: User Guide
nav_order: 6
last_updated: 2026-05-13
description: View node positions on the map, create and share waypoints, and manage position sharing and privacy.
aliases:
  - map
  - waypoints
  - gps
  - location
---

# Map & Waypoints

The Map screen shows the geographic positions of nodes on your mesh, along with shared waypoints.

## Map View

The map displays:

- **Node positions** — colored markers for each node reporting location
- **Waypoints** — shared points of interest
- **Your position** — your current GPS location

### Node Markers

Node markers on the map indicate:

| Color  | Meaning                                        |
| ------ | ---------------------------------------------- |
| 초록     | Online (heard recently)     |
| Yellow | Away (heard within 2 hours) |
| Gray   | Offline (stale position)    |
| 파랑     | Your own node                                  |

### Map Controls

- **Zoom** — pinch or use +/- buttons
- **Pan** — drag to explore
- **Center** — select the location button to center on your position
- **Node tap** — tap a node marker to view details

The floating toolbar provides quick access to compass, layer switching, node filters, refresh, and location tracking. Tap the compass to reorient north-up, or tap the location button to center on your current position.

![Map controls overlay](../../assets/screenshots/map_controls_overlay.png)

## Waypoints

Waypoints are shared geographic points of interest that all mesh members can see.

### Creating a Waypoint

1. Long-press on the map at the desired location.
2. Enter a name and optional description.
3. Choose an icon/emoji for the waypoint.
4. Tap **Send** to share with the mesh.

### Waypoint Properties

| Property   | 설명                                                      |
| ---------- | ------------------------------------------------------- |
| 이름         | Short identifier (max 30 characters) |
| 설명         | Optional longer description                             |
| Icon       | Visual marker emoji on the map                          |
| 잠김         | If locked, only the creator can edit or delete          |
| Expiration | Optional auto-remove time                               |

### Waypoint Expiration

Waypoints can be set to expire automatically:

- **Never** (default) — waypoint remains until manually deleted
- **Timed** — waypoint is automatically removed after the specified duration (e.g., "remove after 2 hours"). Useful for temporary markers like rally points, hazards, or meeting locations.

Expired waypoints are automatically hidden from the map so they don't clutter the display. The expiration countdown begins when the waypoint is created, not when other nodes receive it.

### Managing Waypoints

- Tap a waypoint on the map to view its details and coordinates
- Edit or delete waypoints you created
- **Locked waypoints** cannot be modified or deleted by other nodes — only the original creator can change them
- Unlocked waypoints can be edited by any mesh member

## Position Sharing

### Enabling Position Sharing

Your node shares its GPS position based on:

- **Fixed interval** — broadcast position at regular intervals
- **Smart position** — broadcast when movement exceeds a threshold
- **Manual** — only share when explicitly requested

Configure position behavior in **Settings → Position**.

### Privacy Considerations

> 🔒 **Privacy:** Position data is broadcast to all nodes on your channel. If you don't want your location shared, disable GPS position in settings or use a fixed/fake position.

## Map Sources

The app supports multiple map tile sources:

- OpenStreetMap (default)
- Satellite imagery (where available)
- Offline tiles (download map areas for offline use)

## Related Topics

- [Nodes](nodes) — view and filter your node list
- [Node Metrics](node-metrics) — signal quality and position history for individual nodes
- [Discovery](discovery) — traceroute and neighbor info for understanding mesh topology
- [Units & Locale](units-and-locale) — distance and coordinate display formats

---

