package com.geeksville.mesh.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeListConfig(
    var interval: Int = 7200,
)