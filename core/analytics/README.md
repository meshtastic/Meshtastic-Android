# `:core:analytics`

## Overview
The `:core:analytics` module provides a unified interface for event tracking and crash reporting. It is designed to strictly separate analytics providers based on the build flavor.

## Key Components

### 1. `PlatformAnalytics`
An interface defining the standard operations for tracking events and reporting errors.

## Flavor Specifics

-   **`google` flavor**: Implements `PlatformAnalytics` using **Firebase Analytics** and **Firebase Crashlytics**.
-   **`fdroid` flavor**: Provides a "no-op" implementation that does not collect any user data or report crashes, ensuring FOSS compliance.

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:analytics[analytics]:::android-library
  :core:analytics -.-> :core:prefs
  :core:analytics -.-> :core:repository

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
