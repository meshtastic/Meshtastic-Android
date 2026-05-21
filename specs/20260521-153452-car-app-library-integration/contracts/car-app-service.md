# Car App Service Contract

**Feature**: Car App Library Integration
**Date**: 2026-05-21

## Service Declaration

The `MeshtasticCarAppService` is the entry point for Android Auto and AAOS hosts.

### AndroidManifest.xml Contract

```xml
<service
    android:name="org.meshtastic.feature.car.service.MeshtasticCarAppService"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService" />
        <category android:name="androidx.car.app.category.MESSAGING" />
        <category android:name="androidx.car.app.category.POI" />
    </intent-filter>
</service>
```

### Categories

| Category | Purpose | Justification |
|----------|---------|---------------|
| `MESSAGING` | Primary — enables ConversationItem, voice reply | Core use case: read/reply to mesh messages |
| `POI` | Secondary — enables PlaceListMapTemplate | Node map with static pins (not navigation) |

### Car API Level

```xml
<meta-data
    android:name="androidx.car.app.minCarApiLevel"
    android:value="8" />
```

Car API Level 8 is required for:
- Spotlight Sections
- Condensed Items
- Minimized Control Panel
- Banners
- Chips
- Section Headers
- Expanded Header Layout

Hosts below API Level 8 will not display the app (graceful absence).

## Session Contract

### MeshtasticCarSession

```kotlin
class MeshtasticCarSession(private val sessionInfo: SessionInfo) : Session() {

    override fun onCreateScreen(intent: Intent): Screen
    // Returns: HomeScreen (tab-based root)
    // Side effects:
    //   - Sets Crashlytics "car_session" custom key
    //   - Starts collecting emergency message flow
    //   - Registers MeshStatusPanel

    override fun onNewIntent(intent: Intent)
    // Handles deep links (e.g., open specific conversation from notification)

    override fun onCarConfigurationChanged(newConfiguration: Configuration)
    // Handles theme/density changes (dark mode, etc.)
}
```

### Screen Stack Contract

```
HomeScreen (root, never popped)
  ├── MessagingScreen (tab 1)
  │     └── ConversationScreen (push on conversation tap)
  ├── NodeDashboardScreen (tab 2)
  │     └── NodeDetailScreen (push on node tap)
  └── MapScreen (tab 3)
        └── NodeDetailScreen (push on map item tap)
```

Maximum screen depth: 3 (compliant with CAL template depth limits).

## Template Contracts

### HomeScreen → TabTemplate (proposed, falls back to ListTemplate if tabs unavailable)

```
TabTemplate {
    tabs: [
        Tab("Messages", messagingIcon),
        Tab("Nodes", nodeIcon),
        Tab("Map", mapIcon),
    ]
    headerAction: Action.APP_ICON
}
```

### MessagingScreen → ListTemplate with Chips + Spotlight Section

```
ListTemplate {
    header: Header {
        title: "Messages"
        chipActions: [ChannelChip(name, unreadBadge) for each channel]
    }
    spotlightSection: SpotlightSection {  // Only if activeEmergencies.isNotEmpty()
        items: [emergencyConversationItems...]
    }
    sections: [
        SectionHeader("Channel: {name}"),
        ConversationItem(name, lastMessage, time, unread) for each conversation
    ]
}
```

### ConversationScreen → MessageTemplate / ListTemplate

```
MessageTemplate {
    // For the selected conversation
    messages: [MessageItem(text, sender, time) ...]
    actions: [
        Action("Reply", voiceIcon) → triggers CAL voice input
        Action("Quick Reply", listIcon) → shows quick-reply list
        Action("Read Aloud", speakerIcon) → triggers TTS
    ]
}
```

### NodeDashboardScreen → ListTemplate with Expanded Header + Condensed Items

```
ListTemplate {
    header: ExpandedHeader {
        title: "Mesh Network"
        subtitle: "{onlineNodes}/{totalNodes} nodes online"
        image: meshTopologyIcon
    }
    items: [
        CondensedItem(
            title: node.longName,
            subtitle: "Signal: {quality} • Battery: {percent}%",
            image: signalIcon(quality),
            onClickListener: → push NodeDetailScreen
        ) for each node, sorted online-first
    ]
}
```

### NodeDetailScreen → PaneTemplate

```
PaneTemplate {
    title: node.longName
    pane: Pane {
        rows: [
            Row("Last Heard", formatTimeAgo(node.lastHeard)),
            Row("Distance", formatDistance(distanceMeters)),
            Row("Hardware", node.hwModel.name),
            Row("Battery", "${node.batteryPercent}%"),
            Row("Signal", formatSnr(node.snr)),
        ]
        actions: [
            Action("Message", messageIcon) → push ConversationScreen for DM
        ]
    }
}
```

### MapScreen → PlaceListMapTemplate

```
PlaceListMapTemplate {
    title: "Node Map"
    itemList: ItemList {
        items: [
            Row(
                title: node.name,
                text: "Updated {timeAgo} • {distanceFormatted}",
                metadata: Place(LatLng(lat, lng)),
                onClickListener: → push NodeDetailScreen
            ) for each node with position
        ]
    }
    anchor: LatLng(ownLat, ownLng)  // if own position available
    isCurrentLocationEnabled: true
}
```

### MeshStatusPanel → Minimized Control Panel

```
// Attached to Session, visible across all screens
MinimizedControlPanel {
    icon: connectionStatusIcon
    title: "{onlineNodeCount} nodes online"
    subtitle: "Last msg: {timeAgo}"
    onClickListener: → expand to full detail panel
}
```

### Emergency Banner

```
// Triggered by EmergencyHandler when emergency packet received
AppManager.showAlert(
    Alert {
        title: "⚠️ EMERGENCY"
        subtitle: "{senderName}: {messagePreview}"
        icon: emergencyIcon
        actions: [Action("View", → push emergency detail)]
        duration: Alert.DURATION_LONG
    }
)
```

## Error Contracts

| Condition | Behavior |
|-----------|----------|
| BLE disconnected | Banner shown; screens degrade to cached data (read-only) |
| No channels configured | Show onboarding PaneTemplate directing to phone app |
| No nodes in range | Empty state in NodeDashboard: "No nodes heard" |
| No positions available | MapScreen shows empty map with "No positions reported" |
| Template item limit exceeded | Paginate with "Load more" action row |
| Voice input fails | Fall back to quick-reply template list |
| Session crash | Crashlytics captures with `car_session` tag; session restarts cleanly |
