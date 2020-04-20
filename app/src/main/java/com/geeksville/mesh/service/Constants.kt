package com.geeksville.mesh.service

const val prefix = "com.geeksville.mesh"


//
// standard EXTRA bundle definitions
//

// a bool true means now connected, false means not
const val EXTRA_CONNECTED = "$prefix.Connected"

/// a bool true means we expect this condition to continue until, false means device might come back
const val EXTRA_PERMANENT = "$prefix.Permanent"

const val EXTRA_PAYLOAD = "$prefix.Payload"
const val EXTRA_NODEINFO = "$prefix.NodeInfo"
