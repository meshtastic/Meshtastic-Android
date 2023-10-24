package com.geeksville.mesh.repository.radio

import javax.inject.Inject

/**
 * No-op interface backend implementation.
 */
class NopInterfaceSpec @Inject constructor(
    private val factory: NopInterfaceFactory
): InterfaceSpec<NopInterface> {
    override fun createInterface(rest: String): NopInterface {
        return factory.create(rest)
    }
}