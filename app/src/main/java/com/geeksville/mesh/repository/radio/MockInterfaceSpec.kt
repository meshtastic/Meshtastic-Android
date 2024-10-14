package com.geeksville.mesh.repository.radio

import javax.inject.Inject

/**
 * Mock interface backend implementation.
 */
class MockInterfaceSpec @Inject constructor(
    private val factory: MockInterfaceFactory
) : InterfaceSpec<MockInterface> {
    override fun createInterface(rest: String): MockInterface {
        return factory.create(rest)
    }

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    override fun addressValid(rest: String): Boolean = true
}
