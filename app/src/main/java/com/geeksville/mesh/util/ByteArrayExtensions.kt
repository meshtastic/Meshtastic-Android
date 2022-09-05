package com.geeksville.mesh.util

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }