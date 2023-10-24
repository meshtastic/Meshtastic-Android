package com.geeksville.mesh.repository.radio

import android.app.Application
import com.geeksville.mesh.android.BuildUtils
import com.geeksville.mesh.android.GeeksvilleApplication
import javax.inject.Inject

/**
 * Mock interface backend implementation.
 */
class MockInterfaceSpec @Inject constructor(
    private val application: Application,
    private val factory: MockInterfaceFactory
): InterfaceSpec<MockInterface> {
    override fun createInterface(rest: String): MockInterface {
        return factory.create(rest)
    }

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    override fun addressValid(rest: String): Boolean =
        BuildUtils.isEmulator || ((application as GeeksvilleApplication).isInTestLab)
}