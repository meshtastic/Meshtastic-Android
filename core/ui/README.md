# `:core:ui`

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :core
    direction TB
    :core:analytics[analytics]:::android-library
    :core:api[api]:::android-library
    :core:data[data]:::android-library
    :core:database[database]:::android-library
    :core:datastore[datastore]:::android-library
    :core:di[di]:::android-library
    :core:model[model]:::android-library
    :core:network[network]:::android-library
    :core:prefs[prefs]:::android-library
    :core:proto[proto]:::android-library
    :core:service[service]:::android-library
    :core:strings[strings]:::kmp-library
    :core:ui[ui]:::android-library
  end

  :core:analytics -.-> :core:prefs
  :core:api --> :core:model
  :core:data -.-> :core:analytics
  :core:data -.-> :core:database
  :core:data -.-> :core:datastore
  :core:data -.-> :core:di
  :core:data -.-> :core:model
  :core:data -.-> :core:network
  :core:data -.-> :core:prefs
  :core:data -.-> :core:proto
  :core:database -.-> :core:di
  :core:database -.-> :core:model
  :core:database -.-> :core:proto
  :core:database -.-> :core:strings
  :core:datastore -.-> :core:proto
  :core:model --> :core:proto
  :core:network -.-> :core:di
  :core:network -.-> :core:model
  :core:service --> :core:api
  :core:service -.-> :core:database
  :core:service -.-> :core:model
  :core:service -.-> :core:prefs
  :core:service -.-> :core:proto
  :core:ui -.-> :core:data
  :core:ui -.-> :core:database
  :core:ui -.-> :core:model
  :core:ui -.-> :core:prefs
  :core:ui -.-> :core:proto
  :core:ui -.-> :core:service
  :core:ui -.-> :core:strings

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

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  application[application]:::android-application
  feature[feature]:::android-feature
  library[library]:::android-library
  jvm[jvm]:::jvm-library
  kmp-library[kmp-library]:::kmp-library

  application -.-> feature
  library --> jvm

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

</details>
<!--endregion-->
