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

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:data[data]:::kmp-library

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
