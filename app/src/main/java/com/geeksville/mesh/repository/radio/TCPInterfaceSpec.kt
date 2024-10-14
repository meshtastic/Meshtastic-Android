package com.geeksville.mesh.repository.radio

import javax.inject.Inject

/**
 * TCP interface backend implementation.
 */
class TCPInterfaceSpec @Inject constructor(
    private val factory: TCPInterfaceFactory
) : InterfaceSpec<TCPInterface> {
    override fun createInterface(rest: String): TCPInterface {
        return factory.create(rest)
    }
}
