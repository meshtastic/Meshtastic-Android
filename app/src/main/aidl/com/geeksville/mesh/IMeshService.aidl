// com.geeksville.mesh.IMeshService.aidl
package com.geeksville.mesh;

// Declare any non-default types here with import statements

interface IMeshService {
    /**
    * Set the ID info for this node
    */
    void setOwner(String myId, String longName, String shortName);

    /*
    Send an opaque packet to a specified node name

    typ is defined in mesh.proto Data.Type.  For now juse use 0 to mean opaque bytes.
    */
    void sendOpaque(String destId, in byte[] payload, int typ);

    /**
    Get the IDs of everyone on the mesh.  You should also subscribe for NODE_CHANGE broadcasts.
    */
    String[] getOnline();

    /**
    Is the packet radio currently connected to the phone?
    */
    boolean isConnected();

    // see com.geeksville.com.geeksville.mesh broadcast intents
    // RECEIVED_OPAQUE  for data received from other nodes
    // NODE_CHANGE  for new IDs appearing or disappearing
    // CONNECTION_CHANGED for losing/gaining connection to the packet radio
}
