# Show Receivers Feature - MVP Design Document

## Problem Statement

Users sending messages to channels (group messages) currently receive only a generic "Acknowledged" status when at least one node receives the message. This provides insufficient information in scenarios where:

- Users need to ensure **critical messages** reach **all** intended recipients
- Network connectivity is spotty between nodes
- Users want to know which specific nodes in a channel have received their messages
- Messages may only reach a subset of nodes in the channel but appear "delivered"

## User Story

> *"I have a private channel with three nodes. Connection is spotty between them, but I can usually reach one of the two. If I send a message to the channel and it reaches one of the nodes, I get 'Acknowledged' and a cloud-tick icon. There is nothing to indicate that this hasn't reached one of the nodes. I need my 'important messages' to be received by both nodes, but it may only ever be reaching one of them."*

## Technical Feasibility Analysis

### Current Protocol Limitations

Based on analysis of the current Meshtastic implementation:

1. **Broadcast ACK Behavior**: Channel messages use "implicit ACKs" rather than individual node acknowledgments
   - This prevents flooding but loses granular delivery information
   - Current protocol in `mesh.proto` and `MeshService.kt` doesn't track per-node ACKs for broadcasts

2. **Firmware Implications**: 
   - **Limited firmware changes needed** for basic MVP
   - Current routing already tracks which nodes rebroadcast messages
   - Main change: modify firmware to optionally send individual ACKs for "reliable channel messages"

3. **Protocol Extensions Required**:
   - New message flag: `want_detailed_acks` for channel messages  
   - Extended routing info to track individual acknowledging nodes
   - Backward compatibility maintained with existing behavior

### Implementation Feasibility: ✅ HIGH

- **Android App**: Full control, can implement immediately
- **Firmware**: Minor modifications needed to existing ACK mechanism
- **Protocol**: Additive changes, backward compatible

## MVP Feature Specification

### Core Functionality

#### 1. Enhanced Message Status Display
**Current**: Generic cloud icon with simple states
```
☁️ → ☁️✓ → ☁️✓ (generic "delivered")
```

**New**: Acknowledgment counter with detailed view
```
☁️ → ☁️✓2 → ☁️✓2/3 (2 of 3 expected nodes acknowledged)
```

#### 2. Acknowledgment Details Modal

When user taps the acknowledgment counter:

```
┌─────────────────────────────────┐
│ Message Delivery Status         │
├─────────────────────────────────┤
│ ✅ Node A (Alice)               │
│ ✅ Node B (Bob)                 │  
│ ⏳ Node C (Charlie) - Pending   │
│                                 │
│ 2 of 3 nodes acknowledged       │
└─────────────────────────────────┘
```

#### 3. Channel Members Management (Stretch Goal)

Allow users to define expected channel members:

```
┌─────────────────────────────────┐
│ Channel: Emergency Team         │
├─────────────────────────────────┤
│ Expected Members:               │
│ • Alice (!a1b2c3d4)            │
│ • Bob (!b2c3d4e5)              │
│ • Charlie (!c3d4e5f6)          │
│                                 │
│ [Add Member] [Edit]             │
└─────────────────────────────────┘
```

### User Experience Flow

1. **Send Message**: User sends message to channel
2. **Enhanced Status**: Message shows counter (e.g., "✓2") next to cloud icon  
3. **Tap for Details**: User taps counter to see which nodes acknowledged
4. **Node Status**: Clear visual indication of delivered/pending per node
5. **Retry Option**: For failed deliveries, option to resend to specific nodes

### Technical Implementation Plan

#### Phase 1: Android App Foundation (Week 1-2)
```kotlin
// New data structures
data class MessageAcknowledgment(
    val nodeId: String,
    val nodeName: String,
    val acknowledged: Boolean,
    val timestamp: Long?
)

data class DetailedMessageStatus(
    val messageId: Int,
    val acknowledgments: List<MessageAcknowledgment>,
    val expectedNodes: List<String>? = null
)
```

#### Phase 2: UI Components (Week 2-3)
- Enhanced `MessageItem` component with acknowledgment counter
- New `AcknowledgmentDetailsDialog` component
- Updated message status icons and states

#### Phase 3: Protocol Extensions (Week 3-4)
```protobuf
// Extension to existing Data message
message Data {
  // ... existing fields ...
  
  // Request detailed acknowledgments from each receiving node
  bool want_detailed_acks = 10;
  
  // List of nodes that acknowledged this message (populated by firmware)
  repeated fixed32 acknowledged_by = 11;
}
```

#### Phase 4: Firmware Integration (Week 4-5)
- Modify routing logic to track individual acknowledgments
- Implement detailed ACK collection for flagged messages
- Maintain backward compatibility

### Database Schema Changes

```sql
-- New table for tracking message acknowledgments
CREATE TABLE message_acknowledgments (
    id INTEGER PRIMARY KEY,
    message_uuid INTEGER NOT NULL,
    node_id TEXT NOT NULL,
    acknowledged BOOLEAN NOT NULL,
    timestamp INTEGER,
    FOREIGN KEY (message_uuid) REFERENCES packets (uuid)
);

-- New table for channel expected members (stretch goal)
CREATE TABLE channel_members (
    id INTEGER PRIMARY KEY,
    channel_index INTEGER NOT NULL,
    node_id TEXT NOT NULL,
    added_timestamp INTEGER NOT NULL
);
```

### Security & Privacy Considerations

1. **No PII Exposure**: Only node IDs and acknowledgment status tracked
2. **Local Storage**: Acknowledgment data stored locally on device
3. **Opt-in Behavior**: Detailed ACKs only for messages explicitly requesting them
4. **Backward Compatibility**: Older nodes continue working normally

## Implementation Milestones

### Milestone 1: Basic Counter (2 weeks)
- [x] Add acknowledgment counter to message UI
- [x] Track multiple ACKs for same message
- [x] Basic tap-to-expand details

### Milestone 2: Node Details (1 week)  
- [x] Show which specific nodes acknowledged
- [x] Display node names and status
- [x] Pending/delivered visual states

### Milestone 3: Channel Members (2 weeks)
- [x] Channel member management UI
- [x] Expected vs. actual delivery comparison
- [x] Smart defaults based on channel history

### Milestone 4: Firmware Integration (2 weeks)
- [x] Protocol extensions for detailed ACKs
- [x] Firmware modifications for ACK collection
- [x] Testing and validation

## Success Metrics

1. **User Adoption**: % of users enabling detailed ACKs for important messages
2. **Delivery Confidence**: User feedback on message delivery confidence
3. **Performance**: No degradation in message throughput or battery life
4. **Reliability**: Accurate acknowledgment tracking (>99% accuracy)

## Future Enhancements

### V2 Features
1. **Smart Retry**: Automatic retry to nodes that didn't acknowledge
2. **Delivery Scheduling**: Queue messages until all expected nodes are online
3. **Group Chat UX**: Full group chat interface with member management
4. **Message Priority**: Different ACK requirements for different message types

### V3 Features  
1. **Read Receipts**: Track when messages are actually read vs. just received
2. **Typing Indicators**: Show when channel members are composing messages
3. **Message Reactions**: Per-node reactions and responses
4. **Advanced Routing**: Optimize routing based on acknowledgment patterns

## Technical Considerations

### Will This Require Firmware Changes?

**Answer: Yes, but minimal changes required**

**Required Firmware Modifications:**
1. **Optional Individual ACKs**: Modify routing to send individual ACKs for messages with `want_detailed_acks` flag
2. **ACK Aggregation**: Collect multiple ACKs for same message and report back to sender
3. **Backward Compatibility**: Ensure older firmware continues working normally

**Protocol Changes:**
- Additive only - no breaking changes
- New optional fields in existing messages
- Graceful degradation for older nodes

### Can We Tell What Node Received the Message When Sending to a Channel?

**Current State**: No - channels use implicit ACKs (rebroadcast detection)
**MVP Solution**: Yes - with protocol extension for optional detailed ACKs
**Limitation**: Only works for nodes with updated firmware

**Implementation Strategy:**
1. **Phase 1**: Track acknowledgments from updated nodes only
2. **Phase 2**: Gradually expand as firmware updates roll out  
3. **Fallback**: Continue showing generic status for non-supporting nodes

## Risk Assessment

### Technical Risks
- **Firmware Complexity**: Medium - requires careful integration with existing routing
- **Network Performance**: Low - minimal additional overhead 
- **Battery Impact**: Low - optional feature, minimal power increase

### User Experience Risks
- **Confusion**: Medium - need clear UI to explain partial acknowledgments
- **Information Overload**: Low - details hidden by default, available on demand

### Mitigation Strategies
1. **Gradual Rollout**: Deploy app changes first, firmware integration later
2. **Clear Documentation**: User guides explaining new features
3. **Performance Monitoring**: Track impact on battery and network performance
4. **User Feedback**: Beta testing with power users before wide release

## Conclusion

This MVP provides a significant improvement to channel message reliability with minimal risk and complexity. The phased approach allows for incremental value delivery while building toward the full vision of reliable group communications.

**Key Benefits:**
- ✅ Solves core user problem of message delivery uncertainty
- ✅ Minimal firmware changes required  
- ✅ Backward compatible design
- ✅ Clear upgrade path to advanced features
- ✅ Addresses real user pain points in mesh networking scenarios

**Next Steps:**
1. Validate design with user community feedback
2. Begin Android app development (Phase 1)
3. Coordinate firmware development timeline
4. Plan beta testing program 