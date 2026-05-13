---
title: Discovery
nav_order: 12
last_updated: 2026-05-12
aliases:
  - mesh-discovery
  - local-discovery
  - network-scan
---

# Discovery

Local Mesh Discovery helps you explore and understand your mesh network topology.

## Overview

> 💡 **Note:** The Local Mesh Discovery feature (Feature 001) is currently in development. This page provides a concept overview and will be updated with detailed UI guidance once the feature reaches its UI implementation milestones.

Discovery provides tools to:
- Visualize the mesh network topology
- Identify which nodes can hear each other
- Understand signal paths and relay chains
- Discover new nodes joining the mesh

## Planned Capabilities

### Network Topology View

A visual representation of how nodes are connected:
- Direct connections (single hop)
- Relay paths (multi-hop routes)
- Signal quality between pairs

### Node Discovery

Automatic detection of:
- New nodes appearing on the mesh
- Nodes coming online/offline
- Changes in network topology

### Signal Mapping

Understanding RF coverage:
- Which nodes can hear which other nodes
- Signal strength between node pairs
- Optimal positioning recommendations

## Current Discovery Tools

While the dedicated Discovery feature is under development, you can use these existing tools:

### Traceroute

Available from any node's detail screen:
- Shows the exact path packets take
- Reports signal quality at each hop
- Helps identify relay bottlenecks

### Neighbor Info

When the Neighbor Info module is enabled:
- Each node broadcasts its directly-heard neighbors
- Useful for understanding local mesh topology
- Available in the node detail metrics

### Node List Filtering

The current node list provides discovery-like information:
- Sort by last heard time to see recently active nodes
- Sort by signal quality to assess connectivity
- Filter by distance to find nearby nodes

## Future Updates

This page will be expanded with:
- Detailed UI screenshots and workflows
- Network map visualization guide
- Recommendations engine usage
- Discovery automation settings

---

