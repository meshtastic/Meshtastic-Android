package com.geeksville.mesh.service

open class RadioNotConnectedException(message: String = "Not connected to radio") :
    BLEException(message)