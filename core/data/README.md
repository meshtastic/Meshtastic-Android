# `:core:data`

## Overview
The `:core:data` module implements the Repository pattern, serving as the primary data source for ViewModels in feature modules. It orchestrates data flow between the local database (`core:database`), remote services, and network repositories.

## Key Components

### 1. Repositories
- **`NodeRepository`**: High-level access to node information and mesh state.
- **`MeshLogRepository`**: Access to historical logs and diagnostics.
- **`FirmwareReleaseRepository`**: Manages the discovery and retrieval of firmware updates.

### 2. Data Sources
Internal components that handle raw data fetching from APIs or disk.


## Dependency Graph

<!--region graph-->
<!--endregion-->
