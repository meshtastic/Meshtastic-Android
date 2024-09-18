// com.geeksville.mesh.IMeshService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements
parcelable DataPacket;
parcelable NodeInfo;
parcelable MeshUser;
parcelable Position;
parcelable MyNodeInfo;

/**
This is the public android API for talking to meshtastic radios.

To connect to meshtastic you should bind to it per https://developer.android.com/guide/components/bound-services

The intent you use to reach the service should look like this:

        val intent = Intent().apply {
            setClassName(
                "com.geeksville.mesh",
                "com.geeksville.mesh.service.MeshService"
            )
        }

In Android 11+ you *may* need to add the following to the client app's manifest to allow binding of the mesh service:
<queries>
    <package android:name="com.geeksville.mesh" />
</queries>
For additional information, see https://developer.android.com/guide/topics/manifest/queries-element


Once you have bound to the service you should register your broadcast receivers per https://developer.android.com/guide/components/broadcasts#context-registered-receivers

    // com.geeksville.mesh.x broadcast intents, where x is:

    // RECEIVED.<portnumm> -  will **only** deliver packets for the specified port number.  If a wellknown portnums.proto name for portnum is known it will be used
    // (i.e. com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP) else the numeric portnum will be included as a base 10 integer (com.geeksville.mesh.RECEIVED.4403 etc...)

    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
    // MESSAGE_STATUS_CHANGED for any message status changes (for sent messages only, payload will contain a message ID and a MessageStatus)

Note - these calls might throw RemoteException to indicate mesh error states
*/
interface IMeshService {
    /// Tell the service where to send its broadcasts of received packets
    /// This call is only required for manifest declared receivers.  If your receiver is context-registered
    /// you don't need this.
    void subscribeReceiver(String packageName, String receiverName);

    /**
    * Set the user info for this node
    */
    void setOwner(in MeshUser user);

    void setRemoteOwner(in int requestId, in byte []payload);
    void getRemoteOwner(in int requestId, in int destNum);

    /// Return my unique user ID string
    String getMyId();

    /// Return a unique packet ID
    int getPacketId();

    /*
    Send a packet to a specified node name

    typ is defined in mesh.proto Data.Type.  For now juse use 0 to mean opaque bytes.

    destId can be null to indicate "broadcast message"

    messageStatus and id of the provided message will be updated by this routine to indicate
    message send status and the ID that can be used to locate the message in the future
    */
    void send(inout DataPacket packet);

    /**
    Get the IDs of everyone on the mesh.  You should also subscribe for NODE_CHANGE broadcasts.
    */
    List<NodeInfo> getNodes();

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It returns a DeviceConfig protobuf.
    byte []getConfig();
    /// It sets a Config protobuf via admin packet
    void setConfig(in byte []payload);

    /// Set and get a Config protobuf via admin packet
    void setRemoteConfig(in int requestId, in int destNum, in byte []payload);
    void getRemoteConfig(in int requestId, in int destNum, in int configTypeValue);

    /// Set and get a ModuleConfig protobuf via admin packet
    void setModuleConfig(in int requestId, in int destNum, in byte []payload);
    void getModuleConfig(in int requestId, in int destNum, in int moduleConfigTypeValue);

    /// Set and get the Ext Notification Ringtone string via admin packet
    void setRingtone(in int destNum, in String ringtone);
    void getRingtone(in int requestId, in int destNum);

    /// Set and get the Canned Message Messages string via admin packet
    void setCannedMessages(in int destNum, in String messages);
    void getCannedMessages(in int requestId, in int destNum);

    /// This method is only intended for use in our GUI, so the user can set radio options
    /// It sets a Channel protobuf via admin packet
    void setChannel(in byte []payload);

    /// Set and get a Channel protobuf via admin packet
    void setRemoteChannel(in int requestId, in int destNum, in byte []payload);
    void getRemoteChannel(in int requestId, in int destNum, in int channelIndex);

    /// Send beginEditSettings admin packet to nodeNum
    void beginEditSettings();

    /// Send commitEditSettings admin packet to nodeNum
    void commitEditSettings();

    /// delete a specific nodeNum from nodeDB
    void removeByNodenum(in int requestID, in int nodeNum);

    /// Send position packet with wantResponse to nodeNum
    void requestPosition(in int destNum, in Position position);

    /// Send setFixedPosition admin packet (or removeFixedPosition if Position is empty)
    void setFixedPosition(in int destNum, in Position position);

    /// Send traceroute packet with wantResponse to nodeNum
    void requestTraceroute(in int requestId, in int destNum);

    /// Send Shutdown admin packet to nodeNum
    void requestShutdown(in int requestId, in int destNum);

    /// Send Reboot admin packet to nodeNum
    void requestReboot(in int requestId, in int destNum);

    /// Send FactoryReset admin packet to nodeNum
    void requestFactoryReset(in int requestId, in int destNum);

    /// Send NodedbReset admin packet to nodeNum
    void requestNodedbReset(in int requestId, in int destNum);

    /// Returns a ChannelSet protobuf
    byte []getChannelSet();

    /**
    Is the packet radio currently connected to the phone?  Returns a ConnectionState string.
    */
    String connectionState();

    /// If a macaddress we will try to talk to our device, if null we will be idle.
    /// Any current connection will be dropped (even if the device address is the same) before reconnecting.
    /// Users should not call this directly, only used internally by the MeshUtil activity
    /// Returns true if the device address actually changed, or false if no change was needed
    boolean setDeviceAddress(String deviceAddr);

    /// Get basic device hardware info about our connected radio.  Will never return NULL.  Will return NULL
    /// if no my node info is available (i.e. it will not throw an exception)
    MyNodeInfo getMyNodeInfo();

    /// Start updating the radios firmware
    void startFirmwareUpdate();

    /// Return a number 0-100 for firmware update progress. -1 for completed and success, -2 for failure
    int getUpdateStatus();

    /// Start providing location (from phone GPS) to mesh
    void startProvideLocation();

    /// Stop providing location (from phone GPS) to mesh
    void stopProvideLocation();
}
