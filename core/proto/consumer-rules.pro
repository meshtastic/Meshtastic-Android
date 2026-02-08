# Core proto classes required for packet handling and serialization
# FromRadio and related message types (primary packet container)
-keep class org.meshtastic.proto.FromRadio
-keep class org.meshtastic.proto.Data
-keep class org.meshtastic.proto.MeshPacket
-keep class org.meshtastic.proto.LogRecord

# Message type payloads (handled in packet routing)
-keep class org.meshtastic.proto.AdminMessage
-keep class org.meshtastic.proto.StoreAndForward
-keep class org.meshtastic.proto.StoreForwardPlusPlus
-keep class org.meshtastic.proto.Routing

# User and Node information
-keep class org.meshtastic.proto.User
-keep class org.meshtastic.proto.NeighborInfo
-keep class org.meshtastic.proto.Neighbor

# Location and environment data
-keep class org.meshtastic.proto.Position
-keep class org.meshtastic.proto.Waypoint
-keep class org.meshtastic.proto.StatusMessage

# Telemetry data types
-keep class org.meshtastic.proto.Telemetry
-keep class org.meshtastic.proto.DeviceMetrics
-keep class org.meshtastic.proto.EnvironmentMetrics
-keep class org.meshtastic.proto.AirQualityMetrics
-keep class org.meshtastic.proto.PowerMetrics
-keep class org.meshtastic.proto.LocalStats
-keep class org.meshtastic.proto.HostMetrics

# Other data
-keep class org.meshtastic.proto.Paxcount
-keep class org.meshtastic.proto.DeviceMetadata

# Configuration classes
-keep class org.meshtastic.proto.ChannelSet
-keep class org.meshtastic.proto.LocalConfig
-keep class org.meshtastic.proto.Config
-keep class org.meshtastic.proto.ModuleConfig
-keep class org.meshtastic.proto.Channel
-keep class org.meshtastic.proto.ClientNotification
