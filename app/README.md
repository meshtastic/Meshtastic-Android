# `:app`

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
  subgraph :feature
    direction TB
    :feature:firmware[firmware]:::android-library
    :feature:intro[intro]:::android-library
    :feature:map[map]:::android-library
    :feature:messaging[messaging]:::android-library
    :feature:node[node]:::android-library
    :feature:settings[settings]:::android-library
  end
  subgraph :core
    direction TB
    :core:analytics[analytics]:::android-library
    :core:common[common]:::kmp-library
    :core:data[data]:::android-library
    :core:database[database]:::android-library
    :core:datastore[datastore]:::android-library
    :core:di[di]:::android-library
    :core:model[model]:::android-library
    :core:navigation[navigation]:::android-library
    :core:network[network]:::android-library
    :core:prefs[prefs]:::android-library
    :core:proto[proto]:::android-library
    :core:service[service]:::android-library
    :core:strings[strings]:::kmp-library
    :core:ui[ui]:::android-library
  end
  :app[app]:::android-application

  :app -.-> :core:analytics
  :app -.-> :core:common
  :app -.-> :core:data
  :app -.-> :core:database
  :app -.-> :core:datastore
  :app -.-> :core:di
  :app -.-> :core:model
  :app -.-> :core:navigation
  :app -.-> :core:network
  :app -.-> :core:prefs
  :app -.-> :core:proto
  :app -.-> :core:service
  :app -.-> :core:strings
  :app -.-> :core:ui
  :app -.-> :feature:firmware
  :app -.-> :feature:intro
  :app -.-> :feature:map
  :app -.-> :feature:messaging
  :app -.-> :feature:node
  :app -.-> :feature:settings
  :core:analytics -.-> :core:prefs
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
  :core:model -.-> :core:common
  :core:model -.-> :core:proto
  :core:model -.-> :core:strings
  :core:network -.-> :core:di
  :core:network -.-> :core:model
  :core:service -.-> :core:database
  :core:service -.-> :core:model
  :core:service -.-> :core:proto
  :core:ui -.-> :core:data
  :core:ui -.-> :core:database
  :core:ui -.-> :core:model
  :core:ui -.-> :core:prefs
  :core:ui -.-> :core:proto
  :core:ui -.-> :core:service
  :core:ui -.-> :core:strings
  :feature:firmware -.-> :core:common
  :feature:firmware -.-> :core:data
  :feature:firmware -.-> :core:database
  :feature:firmware -.-> :core:datastore
  :feature:firmware -.-> :core:model
  :feature:firmware -.-> :core:navigation
  :feature:firmware -.-> :core:prefs
  :feature:firmware -.-> :core:proto
  :feature:firmware -.-> :core:service
  :feature:firmware -.-> :core:strings
  :feature:firmware -.-> :core:ui
  :feature:intro -.-> :core:strings
  :feature:map -.-> :core:common
  :feature:map -.-> :core:data
  :feature:map -.-> :core:database
  :feature:map -.-> :core:datastore
  :feature:map -.-> :core:model
  :feature:map -.-> :core:navigation
  :feature:map -.-> :core:prefs
  :feature:map -.-> :core:proto
  :feature:map -.-> :core:service
  :feature:map -.-> :core:strings
  :feature:map -.-> :core:ui
  :feature:messaging -.-> :core:data
  :feature:messaging -.-> :core:database
  :feature:messaging -.-> :core:model
  :feature:messaging -.-> :core:prefs
  :feature:messaging -.-> :core:proto
  :feature:messaging -.-> :core:service
  :feature:messaging -.-> :core:strings
  :feature:messaging -.-> :core:ui
  :feature:node -.-> :core:data
  :feature:node -.-> :core:database
  :feature:node -.-> :core:datastore
  :feature:node -.-> :core:di
  :feature:node -.-> :core:model
  :feature:node -.-> :core:navigation
  :feature:node -.-> :core:proto
  :feature:node -.-> :core:service
  :feature:node -.-> :core:strings
  :feature:node -.-> :core:ui
  :feature:node -.-> :feature:map
  :feature:settings -.-> :core:common
  :feature:settings -.-> :core:data
  :feature:settings -.-> :core:database
  :feature:settings -.-> :core:datastore
  :feature:settings -.-> :core:model
  :feature:settings -.-> :core:navigation
  :feature:settings -.-> :core:prefs
  :feature:settings -.-> :core:proto
  :feature:settings -.-> :core:service
  :feature:settings -.-> :core:strings
  :feature:settings -.-> :core:ui

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
