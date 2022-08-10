package com.geeksville.mesh.model

data class QuickChatAction(
    val name: String,
    val message: String,
    val mode: Mode) {
    enum class Mode {
        Append,
        Instant,
    }
}
