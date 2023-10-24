package com.geeksville.mesh.repository.radio

import dagger.assisted.AssistedFactory

/**
 * Factory for creating `TCPInterface` instances.
 */
@AssistedFactory
interface TCPInterfaceFactory : InterfaceFactorySpi<TCPInterface>