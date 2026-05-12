---
title: Messages & Channels
nav_order: 3
aliases:
  - channels
  - direct-messages
  - messaging
  - conversations
---

# Messages & Channels

Meshtastic supports two communication modes: **channel broadcasts** and **direct messages**.

## Channels

Channels are shared communication groups. All nodes configured with the same channel key can read and send messages on that channel.

### Default Channel

Every Meshtastic device comes with a default **LongFast** channel. This is an unencrypted channel used for general mesh communication.

### Channel Security

| Security Level | Description |
|----------------|-------------|
| None (default) | Messages are readable by any node on the same channel preset |
| PSK (Pre-Shared Key) | Only nodes with the matching key can decrypt messages |
| Admin Key | Required for remote administration commands |

> 🔒 **Security Tip:** Always configure a unique PSK for private communications. The default channel is intentionally open.

### Adding a Channel

1. Navigate to **Settings → Channels**.
2. Tap **Add Channel** or scan a QR code.
3. Configure the channel name and encryption key.
4. Share the channel URL/QR code with others who need access.

## Direct Messages

Direct messages (DMs) are point-to-point encrypted communications between two specific nodes.

### Sending a Direct Message

1. Open the **Messages** tab.
2. Select a node from your contacts list or tap a node in the node list.
3. Type your message and tap **Send**.

### Message States

| State | Icon | Meaning |
|-------|------|---------|
| Queued | ⏳ | Message waiting to be sent |
| Sent | ✓ | Message transmitted to mesh |
| Delivered | ✓✓ | Acknowledgment received from recipient |
| Error | ✗ | Delivery failed after retries |

## Message Features

### Quick Chat

Pre-configured messages for rapid communication:
- Access via the Quick Chat button in the message input area
- Customize quick chat messages in **Settings → Quick Chat**

![Quick chat option](/assets/screenshots/messages_quick_chat.png)

![Message input area](/assets/screenshots/messages-and-channels_channel_list.png)

### Message Priority

Messages are queued and transmitted based on priority:
1. Emergency/alert messages (highest)
2. Direct messages
3. Channel broadcasts (lowest)

### Message Limits

- **Maximum length:** 237 bytes (approximately 230 characters for ASCII text)
- **Rate limiting:** The mesh enforces airtime fairness; heavy message volume may be throttled

## Best Practices

- Use channels for group coordination
- Use direct messages for private person-to-person communication
- Keep messages short — mesh bandwidth is limited
- Configure encryption for sensitive communications

---

