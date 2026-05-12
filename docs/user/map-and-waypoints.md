---
title: Map & Waypoints
nav_order: 6
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
- **Your position** — your phone's current GPS location

### Node Markers

Node markers on the map indicate:
| Color | Meaning |
|-------|---------|
| Green | Online (heard recently) |
| Yellow | Away (heard within 2 hours) |
| Gray | Offline (stale position) |
| Blue | Your own node |

### Map Controls

- **Zoom** — pinch or use +/- buttons
- **Pan** — drag to explore
- **Center** — tap the location button to center on your position
- **Node tap** — tap a node marker to view details

## Waypoints

Waypoints are shared geographic points of interest that all mesh members can see.

### Creating a Waypoint

1. Long-press on the map at the desired location.
2. Enter a name and optional description.
3. Choose an icon/emoji for the waypoint.
4. Tap **Send** to share with the mesh.

### Waypoint Properties

| Property | Description |
|----------|-------------|
| Name | Short identifier (max 30 characters) |
| Description | Optional longer description |
| Icon | Visual marker on the map |
| Locked | If locked, only the creator can edit/delete |
| Expiration | Optional auto-remove time |

### Managing Waypoints

- Tap a waypoint on the map to view details
- Edit or delete waypoints you created
- Locked waypoints cannot be modified by other nodes

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

---

