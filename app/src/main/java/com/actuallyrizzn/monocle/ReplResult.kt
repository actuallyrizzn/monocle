package com.actuallyrizzn.monocle

data class ReplResult(
    val repl: String,
    val raw: ByteArray,
    val packets: List<ByteArray> = emptyList(),
)
