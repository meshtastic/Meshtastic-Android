package com.geeksville.mesh.service

import java.io.Closeable

interface IRadioInterface : Closeable {
    fun handleSendToRadio(p: ByteArray)
}

