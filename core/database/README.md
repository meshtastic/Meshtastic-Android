# `:core:database`

This module provides the local Room database persistence layer for the application using Room Kotlin Multiplatform (KMP).

## Key Components

-   **`MeshtasticDatabase`**: The main Room database class, defined in `commonMain`.
-   **DAOs (Data Access Objects)**:
    -   `NodeInfoDao`: Manages storage and retrieval of node information (`NodeEntity`). Contains critical logic for handling Public Key Conflict (PKC) resolution and preventing identity wiping attacks.
    -   `PacketDao`: Handles storage of mesh packets, including text messages, waypoints, and reactions.
-   **Entities**:
    -   `NodeEntity`: Represents a node on the mesh.
    -   `Packet`: Represents a stored packet.
    -   `ReactionEntity`: Represents emoji reactions to packets.

## Security Considerations

### Public Key Conflict (PKC) Handling
The `NodeInfoDao` implements specific logic to protect against impersonation and "wipe" attacks:
-   **Wipe Protection**: Receiving an `is_licensed=true` packet (which normally clears the public key for compliance) will **not** clear an existing valid public key if one is already known. This prevents attackers from sending fake licensed packets to wipe keys from the DB.
-   **Conflict Detection**: If a new key arrives for an existing node ID that conflicts with a known valid key, the key is set to `ERROR_BYTE_STRING` to flag the potential impersonation.


## Dependency Graph

<!--region graph-->
<!--endregion-->
