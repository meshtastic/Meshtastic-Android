# `:feature:node`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:feature:node` module handles node-centric features, including the node list, detailed node information, telemetry charts, and the compass.

## Key Components

### 1. `NodeListScreen`
Displays all nodes currently known to the application.

### 2. `NodeDetailScreen`
Shows exhaustive details for a specific node, including hardware info, position history, and last heard status.

### 3. `MetricsViewModel`
Manages the retrieval and display of telemetry data (e.g., battery, SNR, environment metrics) using charts.

### 4. `CompassViewModel`
Provides a compass interface to show the relative direction and distance to other nodes.


## Dependency Graph

<!--region graph-->
<!--endregion-->
