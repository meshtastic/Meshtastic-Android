# Message Queue Feature - MVP Implementation Plan

## Problem Statement

The Meshtastic Android app currently tries to send messages a limited number of times before giving up when a recipient node is unreachable. In sparse mesh networks with mobile devices and intermittent connectivity, this often results in failed message delivery that requires manual intervention.

**Key Pain Points:**
- Messages fail after reaching max retry attempts even if conditions improve later
- No automatic retry when network conditions or node availability changes
- Manual message re-sending required for failed deliveries
- Missed opportunities when devices move through "sweet spots" with better connectivity
- Inefficient for power-saving nodes that only come online periodically

## Feature Summary

An intelligent message queue system that automatically retries message delivery when network conditions improve or target nodes become reachable. This MVP focuses on core functionality with simple, reliable operation.

## MVP Requirements

### Core Functionality
1. **Queue Management**: Store failed messages locally in device cache
2. **Smart Retry Logic**: Automatically retry queued messages when conditions improve
3. **User Setting**: Toggle to enable/disable message queuing in app settings
4. **Visual Feedback**: UI indicator showing queued message status
5. **Node Detection**: Retry when target nodes are detected on the network

### MVP Scope Limitations
- Basic retry logic without complex thresholds
- Local storage only (no cloud sync)
- Text messages only (no other data types initially)
- Simple FIFO queue processing
- Manual queue management (view/clear)

## Technical Implementation Plan

### 1. Data Model Updates

#### New MessageQueue Entity
```kotlin
@Entity(tableName = "message_queue")
data class QueuedMessage(
    @PrimaryKey val uuid: Long,
    val originalPacketId: Int,
    val destinationId: String,
    val messageText: String,
    val channelIndex: Int,
    val queuedTime: Long,
    val attemptCount: Int = 0,
    val lastAttemptTime: Long = 0,
    val maxRetries: Int = 10 // MVP default
)
```

#### Settings Addition
```kotlin
// In AppPrefs or SharedPreferences
const val PREF_MESSAGE_QUEUE_ENABLED = "message_queue_enabled"
val messageQueueEnabled: Boolean by BooleanPref(false)
```

### 2. Service Layer Changes

#### MessageQueueManager
```kotlin
/**
 * Manages the message queue for failed deliveries
 */
class MessageQueueManager @Inject constructor(
    private val queueDao: MessageQueueDao,
    private val preferences: SharedPreferences
) {
    /**
     * Add a failed message to the queue
     */
    suspend fun enqueueMessage(dataPacket: DataPacket, routingError: Int)
    
    /**
     * Attempt to send all queued messages for a specific destination
     */
    suspend fun processQueueForDestination(destinationId: String)
    
    /**
     * Process all queued messages (called periodically)
     */
    suspend fun processAllQueued()
    
    /**
     * Remove message from queue after successful delivery
     */
    suspend fun removeFromQueue(uuid: Long)
    
    /**
     * Get queued messages for UI display
     */
    fun getQueuedMessages(): Flow<List<QueuedMessage>>
}
```

#### MeshService Integration
```kotlin
// In MeshService.kt - modify existing message handling

/**
 * Enhanced message sending with queue support
 */
private fun handleMessageFailure(dataPacket: DataPacket, routingError: Int) {
    serviceScope.launch {
        if (preferences.getBoolean(PREF_MESSAGE_QUEUE_ENABLED, false)) {
            messageQueueManager.enqueueMessage(dataPacket, routingError)
            // Update UI to show queued status
            serviceBroadcasts.broadcastMessageStatus(
                dataPacket.id, 
                MessageStatus.QUEUED_FOR_RETRY
            )
        }
    }
}

/**
 * Trigger queue processing when network conditions change
 */
private fun onNetworkConditionChange() {
    if (connectionState == ConnectionState.CONNECTED) {
        serviceScope.launch {
            messageQueueManager.processAllQueued()
        }
    }
}

/**
 * Process queue when new nodes are detected
 */
private fun onNodeDetected(nodeId: String) {
    serviceScope.launch {
        messageQueueManager.processQueueForDestination(nodeId)
    }
}
```

### 3. Database Schema

#### New DAO Interface
```kotlin
@Dao
interface MessageQueueDao {
    @Query("SELECT * FROM message_queue ORDER BY queued_time ASC")
    fun getAllQueued(): Flow<List<QueuedMessage>>
    
    @Query("SELECT * FROM message_queue WHERE destinationId = :destId")
    suspend fun getQueuedForDestination(destId: String): List<QueuedMessage>
    
    @Insert
    suspend fun insert(queuedMessage: QueuedMessage)
    
    @Delete
    suspend fun delete(queuedMessage: QueuedMessage)
    
    @Query("DELETE FROM message_queue WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: Long)
    
    @Query("DELETE FROM message_queue WHERE attemptCount >= maxRetries")
    suspend fun cleanupExpiredMessages()
}
```

### 4. UI Components

#### New MessageStatus Enum Value
```kotlin
enum class MessageStatus {
    // ... existing values
    QUEUED_FOR_RETRY // New status for queued messages
}
```

#### Settings UI Addition
```kotlin
// In app settings screen
SwitchPreference(
    title = stringResource(R.string.message_queue_enabled),
    summary = stringResource(R.string.message_queue_summary),
    checked = messageQueueEnabled,
    enabled = true,
    onCheckedChange = { enabled ->
        // Update preference
        preferences.edit {
            putBoolean(PREF_MESSAGE_QUEUE_ENABLED, enabled)
        }
    }
)
```

#### Message List UI Updates
```kotlin
// In MessageItem.kt - add new icon for queued messages
Icon(
    imageVector = when (messageStatus) {
        MessageStatus.RECEIVED -> Icons.TwoTone.HowToReg
        MessageStatus.QUEUED -> Icons.TwoTone.CloudUpload
        MessageStatus.QUEUED_FOR_RETRY -> Icons.TwoTone.Schedule // New icon
        MessageStatus.DELIVERED -> Icons.TwoTone.CloudDone
        MessageStatus.ENROUTE -> Icons.TwoTone.Cloud
        MessageStatus.ERROR -> Icons.TwoTone.CloudOff
        else -> Icons.TwoTone.Warning
    },
    contentDescription = stringResource(R.string.message_delivery_status),
)
```

#### Queue Management Screen (Optional for MVP+)
```kotlin
/**
 * Simple screen to view and manage queued messages
 */
@Composable
fun QueuedMessagesScreen(
    queuedMessages: List<QueuedMessage>,
    onRetryMessage: (QueuedMessage) -> Unit,
    onCancelMessage: (QueuedMessage) -> Unit
)
```

### 5. Retry Logic

#### Smart Retry Strategy
```kotlin
/**
 * Determines when to retry queued messages
 */
class RetryStrategy {
    /**
     * Check if message should be retried based on:
     * - Time since last attempt (exponential backoff)
     * - Network connectivity status
     * - Target node availability
     */
    fun shouldRetry(queuedMessage: QueuedMessage, currentTime: Long): Boolean {
        val timeSinceLastAttempt = currentTime - queuedMessage.lastAttemptTime
        val backoffTime = calculateBackoff(queuedMessage.attemptCount)
        
        return timeSinceLastAttempt >= backoffTime &&
               queuedMessage.attemptCount < queuedMessage.maxRetries
    }
    
    private fun calculateBackoff(attemptCount: Int): Long {
        // Exponential backoff: 30s, 1m, 2m, 4m, 8m, max 30m
        return minOf(30_000L * (1 shl attemptCount), 30 * 60_000L)
    }
}
```

### 6. String Resources

```xml
<!-- In strings.xml -->
<string name="message_queue_enabled">Enable Message Queue</string>
<string name="message_queue_summary">Automatically retry failed messages when conditions improve</string>
<string name="message_status_queued_retry">Queued for retry</string>
<string name="queued_messages">Queued Messages</string>
<string name="retry_message">Retry Now</string>
<string name="cancel_queued_message">Cancel</string>
```

## Implementation Phases

### Phase 1: Core Infrastructure (Week 1-2)
- [ ] Database schema and DAO implementation
- [ ] MessageQueueManager basic structure
- [ ] Settings integration
- [ ] Basic message queuing on failure

### Phase 2: Retry Logic (Week 2-3)
- [ ] Implement retry strategy
- [ ] Network condition monitoring
- [ ] Node detection integration
- [ ] Background queue processing

### Phase 3: UI Integration (Week 3-4)
- [ ] Message status UI updates
- [ ] Settings screen integration
- [ ] Queue status indicators
- [ ] Basic queue management

### Phase 4: Testing & Polish (Week 4)
- [ ] Unit tests for queue logic
- [ ] Integration testing
- [ ] Performance optimization
- [ ] Documentation updates

## Success Metrics

### MVP Goals
- Messages automatically retry when target nodes come online
- Reduced manual message re-sending by 80%
- Queue processing with minimal battery impact
- Reliable local storage of queued messages
- Clear user feedback on message queue status

### Technical Requirements
- Queue processing should not impact normal message sending
- Automatic cleanup of expired/old queued messages
- Graceful handling of queue overflow scenarios
- Minimal memory footprint for queue management

## Future Enhancements (Post-MVP)

### Advanced Features
1. **Connection Strength Thresholds**: Only retry when signal strength meets minimum requirements
2. **Priority Queuing**: Important messages get higher retry priority
3. **Selective Queuing**: User can choose which message types to queue
4. **Queue Persistence**: Survive app restarts and device reboots
5. **Network Intelligence**: Learn optimal retry timing for specific nodes
6. **Batch Processing**: Send multiple queued messages efficiently
7. **Queue Analytics**: Statistics on queue performance and success rates

### UI Enhancements
1. **Queue Management Screen**: Full queue view with manual controls
2. **Queue Statistics**: Show success rates and performance metrics
3. **Per-Contact Settings**: Different queue settings per contact
4. **Queue Notifications**: Alert when queued messages are delivered
5. **Export/Import**: Backup and restore queued messages

## Risk Mitigation

### Technical Risks
- **Queue Overflow**: Implement size limits and automatic cleanup
- **Battery Drain**: Limit retry frequency and use efficient background processing
- **Storage Growth**: Regular cleanup of old/expired messages
- **Network Spam**: Implement reasonable retry limits and backoff

### User Experience Risks
- **Confusion**: Clear UI indicators for different message states
- **Performance**: Queue processing should be invisible to user
- **Reliability**: Robust error handling and recovery mechanisms

## Dependencies

### External Dependencies
- Room database for queue storage
- WorkManager for background processing (optional)
- Existing MeshService message handling pipeline

### Internal Dependencies
- Current message sending/receiving infrastructure
- Node detection and network monitoring systems
- Settings and preferences framework
- UI components and status indicators

## Testing Strategy

### Unit Tests
- MessageQueueManager functionality
- Retry strategy logic
- Database operations
- Settings integration

### Integration Tests
- End-to-end message queuing flow
- Network condition change handling
- Queue processing performance
- UI state management

### Manual Testing
- Real-world mesh network scenarios
- Power saving mode compatibility
- Device mobility testing
- Queue persistence across app lifecycle

---

This MVP implementation provides a solid foundation for intelligent message queuing while keeping the scope manageable and focused on core user needs. The modular design allows for incremental enhancement and feature expansion based on user feedback and real-world usage patterns. 